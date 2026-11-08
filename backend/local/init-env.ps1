param([ValidateRange(1024, 65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'

# 최초 한 번만 생성합니다. 재실행해도 기존 DB/관리자 비밀번호를 바꾸지 않습니다.
$destination = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $destination) {
    Write-Host '기존 backend/local/.env를 유지합니다.'
    return
}

function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

$content = @"
# 이 PC 전용 설정입니다. Git에 올리지 마세요.
DB_PASSWORD=$(New-LocalSecret)
APP_NODE_ID=office-local-01
APP_PUBLIC_BASE_URL=http://127.0.0.1:$Port
BOOTSTRAP_ADMIN_USER=admin
BOOTSTRAP_ADMIN_PASSWORD=$(New-LocalSecret)
LOCAL_HTTP_PORT=$Port
LOCAL_BIND_ADDRESS=127.0.0.1
SESSION_COOKIE_SECURE=false
APP_ALLOW_INSECURE_HTTP=false
"@

# 동시에 실행된 경우에도 다른 프로세스가 만든 설정을 덮어쓰지 않습니다.
$stream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($content + "`n")
    $stream.Write($bytes, 0, $bytes.Length)
} finally {
    $stream.Dispose()
}
Write-Host "로컬 설정 생성 완료: $destination"
Write-Host "관리자 주소: http://127.0.0.1:$Port/master/index.html"
Write-Host '관리자 계정은 .env의 BOOTSTRAP_ADMIN_USER / BOOTSTRAP_ADMIN_PASSWORD에서 확인하세요.'
