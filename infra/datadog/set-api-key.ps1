# Datadog の API キーを Parameter Store に登録する。キーは画面にもチャットにも出さない
# 登録したあとの次の up.ps1 から、EC2 で Datadog Agent が動く。消すと Agent は動かなくなる
#
# 使い方: ./infra/datadog/set-api-key.ps1
#         ./infra/datadog/set-api-key.ps1 -Remove
param([switch]$Remove)
$ErrorActionPreference = 'Stop'

$region = 'ap-northeast-1'
$name = '/sns-app/datadog/api-key'

if ($Remove) {
    aws ssm delete-parameter --region $region --name $name
    if ($LASTEXITCODE -ne 0) { throw 'API キーを消せませんでした' }
    Write-Host '消しました。次の up.ps1 から Agent は動きません'
    exit 0
}

$key = Read-Host 'Datadog の API キーを貼り付けて Enter（画面には出ません）' -MaskInput
if ($key -notmatch '^[0-9a-f]{32}$') { throw 'API キーは32文字の英数字です。Application Key と間違えていないか確かめてください' }

aws ssm put-parameter --region $region --name $name --type SecureString --value $key --overwrite --output text | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'API キーを登録できませんでした' }
Write-Host '登録しました。次の up.ps1 から Agent が動きます'
