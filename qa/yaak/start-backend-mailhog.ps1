param(
    [string] $BackendDir = (Join-Path $PSScriptRoot "..\..\backend")
)

$ErrorActionPreference = "Stop"

$resolvedBackendDir = (Resolve-Path $BackendDir).Path
$envFile = Join-Path $resolvedBackendDir ".env.mailhog.example"
if (-not (Test-Path $envFile)) {
    throw "Cannot find MailHog environment template: $envFile"
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) {
        return
    }
    $parts = $line -split "=", 2
    if ($parts.Count -ne 2) {
        return
    }
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
}

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_CONFIG_IMPORT = "optional:file:.env.mailhog.example[.properties]"

$storageEndpoint = if ($env:MAYOISTAR_S3_ENDPOINT) { $env:MAYOISTAR_S3_ENDPOINT } else { "http://localhost:9000" }
try {
    Invoke-WebRequest -Uri $storageEndpoint -Method Head -TimeoutSec 3 | Out-Null
}
catch {
    Write-Warning "RustFS/S3 endpoint is not reachable: $storageEndpoint. File upload QA cases require docker compose to start rustfs."
}

$redisHost = if ($env:MAYOISTAR_REDIS_HOST) { $env:MAYOISTAR_REDIS_HOST } else { "localhost" }
$redisPort = if ($env:MAYOISTAR_REDIS_PORT) { $env:MAYOISTAR_REDIS_PORT } else { "6379" }
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect($redisHost, [int]$redisPort)
    $tcp.Close()
}
catch {
    Write-Warning "Redis is not reachable at ${redisHost}:${redisPort}. Backend will fail to start because Redis is required for media access cache and rate limiting."
}

Set-Location $resolvedBackendDir
mvn spring-boot:run
