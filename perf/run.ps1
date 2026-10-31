# 性能テストを1回まわす: 専用 DB を作る → アプリを起動 → データ投入 → k6 で測る → 後片付け
# 普段の DB（snsapp_development）には触らない。テスト用の DB（snsapp_perf）は最後に消す（-KeepData のときだけ残す）。
#
# 使い方（リポジトリ直下で）:
#   cd backend; mvn package -DskipTests; cd ..
#   ./perf/run.ps1                    # 5人で1分
#   ./perf/run.ps1 -Vus 10 -Duration 2m
#   ./perf/run.ps1 -KeepData          # 測ったあと DB を残す（EXPLAIN で調べるとき。終わったら DROP DATABASE snsapp_perf）
param(
    [int]$Vus = 5,
    [string]$Duration = '1m',
    [switch]$KeepData
)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $root 'backend/target/sns-api-0.1.0.jar'
$java = Join-Path $HOME 'tools/jdk-25.0.4.1+1/bin/java.exe'
$k6 = Join-Path $HOME 'tools/k6-v2.3.0-windows-amd64/k6.exe'
$port = 8081   # 普段の 8080 と重ならないようにする
$baseUrl = "http://localhost:$port"
$perfDb = 'snsapp_perf'

if (-not (Test-Path $jar)) { throw "jar がありません。先に backend で mvn package -DskipTests を実行してください: $jar" }

function Invoke-Mysql([string]$sql, [string]$database = '') {
    $sql | docker exec -i sns-app-mysql mysql -uroot -psnsapp --default-character-set=utf8mb4 $database 2>&1 |
        Where-Object { $_ -notmatch 'Using a password' }
    if ($LASTEXITCODE -ne 0) { throw "MySQL の実行に失敗しました" }
}

Write-Host '== MySQL を起動'
docker compose -f (Join-Path $root 'docker-compose.yml') up -d --wait db | Out-Null

$app = $null
try {
    Write-Host "== 専用 DB $perfDb を作り直す"
    Invoke-Mysql "DROP DATABASE IF EXISTS $perfDb; CREATE DATABASE $perfDb CHARACTER SET utf8mb4;"

    Write-Host "== アプリを起動（$baseUrl）"
    $env:DB_URL = "jdbc:mysql://localhost:3307/$perfDb"
    $log = Join-Path $PSScriptRoot 'app.log'
    # メモリの少ない PC 向けの設定
    $app = Start-Process $java -PassThru -NoNewWindow -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -ArgumentList '-Xmx256m', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-jar', $jar, "--server.port=$port"
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
        Start-Sleep 2
        try { Invoke-WebRequest "$baseUrl/api/auth/me" -SkipHttpErrorCheck | Out-Null; $ready = $true } catch { }
    }
    if (-not $ready) { throw "アプリが起動しませんでした。$log を見てください" }

    Write-Host '== テストデータを入れる（ユーザー1,000人・投稿10万件）'
    # 1人目は API で登録して、パスワードのハッシュをほかのユーザーに使い回す
    $body = @{ email = 'perf_0000@example.com'; password = 'perf-test-password'; username = 'perf_0000'; displayName = '性能テスト 0' } | ConvertTo-Json
    Invoke-RestMethod "$baseUrl/api/auth/register" -Method Post -ContentType 'application/json; charset=utf-8' -Body $body | Out-Null
    Get-Content (Join-Path $PSScriptRoot 'seed.sql') -Raw -Encoding utf8 | ForEach-Object { Invoke-Mysql $_ $perfDb }

    Write-Host "== 測る（$Vus 人で $Duration）"
    & $k6 run --quiet -e BASE_URL=$baseUrl -e VUS=$Vus -e DURATION=$Duration (Join-Path $PSScriptRoot 'timeline-perf.js')
    $exitCode = $LASTEXITCODE
} finally {
    Write-Host '== 後片付け'
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id -Force }
    Remove-Item Env:DB_URL -ErrorAction SilentlyContinue
    if (-not $KeepData) { Invoke-Mysql "DROP DATABASE IF EXISTS $perfDb;" }
}
if ($exitCode -ne 0) { Write-Host '目安（p95 300ms）を超えたか、エラーがありました' -ForegroundColor Red }
exit $exitCode
