# 結合テストを1回まわす: 画面と jar をビルド → 専用 DB を作る → アプリを起動 → Chrome で確かめる → 後片付け
# 普段の DB（snsapp_development）には触らない。テスト用の DB（snsapp_e2e）は最後に消す。
#
# 使い方（リポジトリ直下で）:
#   ./e2e/run.ps1                 # 全部
#   ./e2e/run.ps1 -SkipBuild      # ビルド済みの jar をそのまま使う
#   ./e2e/run.ps1 -Grep 新着      # 名前に「新着」が入るテストだけ
param(
    [switch]$SkipBuild,
    [string]$Grep = ''
)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $root 'backend/target/sns-api-0.1.0.jar'
$jdk = Join-Path $HOME 'tools/jdk-25.0.4.1+1'
$mvn = Join-Path $HOME 'tools/apache-maven-3.9.16/bin/mvn.cmd'
$port = 8082   # 普段の 8080、性能テストの 8081 と重ならないようにする
$baseUrl = "http://localhost:$port"
$db = 'snsapp_e2e'
$log = Join-Path $PSScriptRoot 'app.log'

function Invoke-Mysql([string]$sql) {
    $sql | docker exec -i sns-app-mysql mysql -uroot -psnsapp 2>&1 | Where-Object { $_ -notmatch 'Using a password' }
    if ($LASTEXITCODE -ne 0) { throw 'MySQL の実行に失敗しました' }
}

function Test-Docker {
    # Docker Desktop の起動中は docker コマンドが一時的に見つからないことがある
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    docker info *> $null
    $running = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previous
    return $running
}

if (-not $SkipBuild) {
    Write-Host '== ビルド'
    Push-Location (Join-Path $root 'frontend')
    try {
        npm run build --silent
        if ($LASTEXITCODE -ne 0) { throw '画面のビルドに失敗しました' }
    } finally { Pop-Location }
    $env:JAVA_HOME = $jdk
    $env:MAVEN_OPTS = '-Xmx192m -XX:+UseSerialGC -XX:TieredStopAtLevel=1'
    & $mvn -q -o -f (Join-Path $root 'backend/pom.xml') package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'jar のビルドに失敗しました' }
}
if (-not (Test-Path $jar)) { throw "jar がありません: $jar" }

Push-Location $PSScriptRoot
try {
    if (-not (Test-Path 'node_modules')) {
        npm ci --silent
        if ($LASTEXITCODE -ne 0) { throw 'npm ci に失敗しました' }
    }
} finally { Pop-Location }

# 自分で起動したものだけ、最後に止める
$dockerStarted = -not (Test-Docker)
if ($dockerStarted) {
    Write-Host '== Docker Desktop を起動'
    Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    for ($i = 0; $i -lt 90 -and -not (Test-Docker); $i++) { Start-Sleep 2 }
    if (-not (Test-Docker)) { throw 'Docker Desktop が起動しませんでした' }
}
$dbStarted = -not (docker ps -q --filter 'name=^sns-app-mysql$')

$app = $null
$exitCode = 1
try {
    Write-Host '== MySQL を起動'
    docker compose -f (Join-Path $root 'docker-compose.yml') up -d --wait db 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'MySQL が起動しませんでした' }

    Write-Host "== 専用 DB $db を作り直す"
    Invoke-Mysql "DROP DATABASE IF EXISTS $db; CREATE DATABASE $db CHARACTER SET utf8mb4;"

    Write-Host "== アプリを起動（$baseUrl）"
    $env:DB_URL = "jdbc:mysql://localhost:3307/$db"
    $env:APP_PUBLIC_URL = $baseUrl
    $env:IMAGES_DIR = Join-Path $PSScriptRoot 'test-images'
    # メモリの少ない PC 向けの設定
    $app = Start-Process (Join-Path $jdk 'bin/java.exe') -PassThru -NoNewWindow -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -ArgumentList '-Xmx256m', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-jar', $jar, "--server.port=$port"
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
        Start-Sleep 2
        try { Invoke-WebRequest "$baseUrl/api/auth/me" -SkipHttpErrorCheck | Out-Null; $ready = $true } catch { }
    }
    if (-not $ready) { throw "アプリが起動しませんでした。$log を見てください" }

    Write-Host '== テスト'
    $env:E2E_BASE_URL = $baseUrl
    $env:E2E_APP_LOG = $log
    Push-Location $PSScriptRoot
    try {
        if ($Grep) { npx playwright test --grep $Grep } else { npx playwright test }
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    Write-Host '== 後片付け'
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id -Force }
    foreach ($name in 'DB_URL', 'APP_PUBLIC_URL', 'IMAGES_DIR', 'E2E_BASE_URL', 'E2E_APP_LOG') {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
    Remove-Item (Join-Path $PSScriptRoot 'test-images') -Recurse -Force -ErrorAction SilentlyContinue
    if (Test-Docker) {
        Invoke-Mysql "DROP DATABASE IF EXISTS $db;"
        if ($dbStarted) { docker compose -f (Join-Path $root 'docker-compose.yml') stop db 2>&1 | Out-Null }
    }
    if ($dockerStarted) { docker desktop stop 2>&1 | Out-Null }
}
if ($exitCode -ne 0) { Write-Host '失敗しました。e2e/playwright-report/index.html に画面の記録があります' -ForegroundColor Red }
exit $exitCode
