param(
    [ValidateRange(1024, 65535)][int]$Port = 28080,
    [switch]$NoBrowser
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$envFile = Join-Path $PSScriptRoot '.env'
$composeFile = Join-Path $PSScriptRoot 'compose.yml'
$jar = Join-Path $projectRoot 'backend/common/target/boundary-records-0.1.0.jar'

function Test-DockerReady {
    # Windows PowerShell 5.1 reports native stderr as errors even when redirected.
    $ErrorActionPreference = 'Continue'
    & docker info --format '{{.ServerVersion}}' *> $null
    return ($LASTEXITCODE -eq 0)
}

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker Desktop is required. Install it and try again.'
    }
    if (-not (Test-DockerReady)) {
        Write-Host 'Starting Docker Desktop...'
        & docker desktop start
        if ($LASTEXITCODE -ne 0) {
            throw 'Open Docker Desktop, wait until the engine is running, and try again.'
        }
    }
    $dockerReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-DockerReady) { $dockerReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $dockerReady) { throw 'Docker did not become ready. Check Docker Desktop.' }

    & (Join-Path $PSScriptRoot 'init-env.ps1') -Port $Port
    $settings = @{}
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ($line -match '^([A-Z_]+)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
    }
    $httpPort = 8080
    if ($settings.ContainsKey('LOCAL_HTTP_PORT')) {
        $httpPort = [int]$settings['LOCAL_HTTP_PORT']
    }
    if ($httpPort -lt 1024 -or $httpPort -gt 65535) { throw 'Invalid LOCAL_HTTP_PORT in .env.' }
    $baseUrl = "http://127.0.0.1:$httpPort"
    $loginUrl = "$baseUrl/master/login.html"

    if (-not (Test-Path -LiteralPath $jar)) {
        $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($maven) { $mavenPath = $maven.Source }
        else { $mavenPath = Join-Path $projectRoot '.tools/apache-maven-3.9.9/bin/mvn.cmd' }
        if (-not (Test-Path -LiteralPath $mavenPath)) {
            throw 'Install JDK 17 and Maven 3.9+, then run mvn package from the project folder.'
        }
        & $mavenPath -B -f (Join-Path $projectRoot 'pom.xml') package
        if ($LASTEXITCODE -ne 0) { throw 'Build failed. See the Maven output above.' }
    }

    & docker compose --env-file $envFile -f $composeFile up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Server start failed. Check the Docker output above for a port conflict.' }
    Write-Host 'Waiting for the database and server...'
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $health = Invoke-RestMethod "$baseUrl/actuator/health" -TimeoutSec 2
            $login = Invoke-WebRequest $loginUrl -UseBasicParsing -TimeoutSec 2
            if ($health.status -eq 'UP' -and $login.StatusCode -eq 200 -and $login.Content.Contains('loginCsrf')) {
                $ready = $true
                break
            }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        & docker compose --env-file $envFile -f $composeFile logs --tail 40 app
        throw 'The login page is not ready. Check the server logs above.'
    }
    Write-Host "READY: $loginUrl"
    Write-Host "Login credentials: BOOTSTRAP_ADMIN_USER / BOOTSTRAP_ADMIN_PASSWORD in $envFile"
    if (-not $NoBrowser) { Start-Process $loginUrl }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
