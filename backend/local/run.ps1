# 공통 Java 진입점을 local 설정으로 실행합니다. 환경변수는 실행 전에 지정합니다.
& (Join-Path $PSScriptRoot '../common/run.ps1') -Profile 'local'
exit $LASTEXITCODE
