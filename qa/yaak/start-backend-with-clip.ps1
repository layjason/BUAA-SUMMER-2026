param(
    [string] $BackendDir = (Join-Path $PSScriptRoot "..\..\backend")
)

$ErrorActionPreference = "Stop"

$resolvedBackendDir = (Resolve-Path $BackendDir).Path
$clipDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..\clip-service")).Path
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

# 检查 RustFS/S3
$storageHost = if ($env:MAYOISTAR_S3_HOST) { $env:MAYOISTAR_S3_HOST } else { "localhost" }
$storagePort = if ($env:MAYOISTAR_S3_PORT) { $env:MAYOISTAR_S3_PORT } else { "9000" }
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect($storageHost, [int]$storagePort)
    $tcp.Close()
}
catch {
    Write-Warning "RustFS/S3 endpoint is not reachable at ${storageHost}:${storagePort}. File upload QA cases require docker compose to start rustfs."
}

# 检查 Redis
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

# 检查 CLIP 边车服务
$clipEndpoint = if ($env:MAYOISTAR_CLIP_ENDPOINT) { $env:MAYOISTAR_CLIP_ENDPOINT } else { "http://localhost:8000" }
if ($clipEndpoint -match '://([^:]+):(\d+)$') {
    $clipHost = $Matches[1]
    $clipPort = $Matches[2]
}
else {
    $clipHost = "localhost"
    $clipPort = "8000"
}
Write-Host "Checking CLIP service (${clipHost}:${clipPort})..."
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect($clipHost, [int]$clipPort)
    $tcp.Close()
    Write-Host "CLIP service is ready" -ForegroundColor Green
}
catch {
    Write-Warning "CLIP service is not running at ${clipHost}:${clipPort}."
    Write-Warning "  Start it with: cd $clipDir ; python main.py"
    Write-Warning "  Or with Docker: cd $clipDir ; docker compose up -d"
}

Set-Location $resolvedBackendDir
mvn spring-boot:run
