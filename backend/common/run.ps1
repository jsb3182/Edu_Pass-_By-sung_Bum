param([Parameter(Mandatory = $true)][ValidateSet('local', 'aws')][string]$Profile)
$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'target/boundary-records-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw '먼저 프로젝트 루트에서 mvn package를 실행하세요.'
}
& java "-Dspring.profiles.active=$Profile" -jar $jar
exit $LASTEXITCODE
