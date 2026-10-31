#Requires -Version 7
# 環境（EC2・RDS・Elastic IP など）を作る。
# down.ps1 で保存したDBのスナップショットがあれば、最新のものから復元する。
# 作り終わったら、そのままアプリもデプロイする（deploy.ps1）。
# 使い方:  ./up.ps1              （作って、デプロイする）
#          ./up.ps1 -SkipDeploy  （作るだけ）
param([switch]$SkipDeploy)

$ErrorActionPreference = 'Stop'
$tf = "$env:USERPROFILE\tools\terraform\terraform.exe"
$region = 'ap-northeast-1'
$db = 'sns-app-db'
Set-Location $PSScriptRoot

aws sts get-caller-identity --output text *> $null
if ($LASTEXITCODE -ne 0) { throw 'AWSにログインしていません。aws login を実行してから、もう一度実行してください。' }

if (-not (Test-Path .terraform)) {
    & $tf init -input=false
    if ($LASTEXITCODE -ne 0) { throw 'terraform init に失敗しました。' }
}

$latest = aws rds describe-db-snapshots --region $region --snapshot-type manual `
    --query "reverse(sort_by(DBSnapshots[?starts_with(DBSnapshotIdentifier, '$db-') && Status=='available'], &SnapshotCreateTime))[0].DBSnapshotIdentifier" `
    --output text
$tfArgs = @('apply', '-auto-approve', '-input=false')
if ($latest -and $latest -ne 'None') {
    Write-Host "DBをスナップショットから復元します: $latest"
    $tfArgs += "-var=db_snapshot_identifier=$latest"
} else {
    Write-Host 'スナップショットがないので、空のDBを作ります（テーブルはアプリの起動時に Flyway が作ります）。'
}

Write-Host '環境を作っています（RDSの作成に10分ほどかかります）'
& $tf @tfArgs
if ($LASTEXITCODE -ne 0) { throw 'terraform apply に失敗しました。もう一度 ./up.ps1 を実行してください。' }

$url = & $tf output -raw app_url
# サーバーの準備（Docker など）が終わるのを待つのは deploy.ps1 がやる
if (-not $SkipDeploy) {
    & "$PSScriptRoot\deploy.ps1"
}
Write-Host "使えるようになりました: $url"
Write-Host '使い終わったら ./down.ps1 で消してください（消さないとクレジットが減り続けます）。'
