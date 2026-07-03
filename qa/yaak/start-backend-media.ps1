param(
    [string] $BackendDir = (Join-Path $PSScriptRoot "..\..\backend")
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding

# 输出带时间戳的日志。
function Write-Log {
    param(
        [Parameter(Mandatory = $true)] [string] $Message,
        [string] $Color = "Cyan"
    )
    $timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$timestamp] $Message" -ForegroundColor $Color
}

# 前置条件：HostName 为可解析主机名，Port 为 TCP 端口；后置条件：端口可连接时返回 true，否则返回 false；不变量：函数不会抛出连接异常。
function Test-Port {
    param(
        [Parameter(Mandatory = $true)] [string] $HostName,
        [Parameter(Mandatory = $true)] [int] $Port,
        [int] $TimeoutMs = 2000
    )
    $tcp = [System.Net.Sockets.TcpClient]::new()
    try {
        $target = if ($HostName -eq "localhost") { "127.0.0.1" } else { $HostName }
        $connect = $tcp.BeginConnect($target, $Port, $null, $null)
        $wait = $connect.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if (-not $wait -or -not $tcp.Connected) { return $false }
        $tcp.EndConnect($connect)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $tcp.Close()
    }
}

# 前置条件：环境文件使用 KEY=VALUE 格式；后置条件：有效键值写入当前进程环境变量；不变量：空行和注释行不会改变环境。
function Import-EnvFile {
    param([Parameter(Mandatory = $true)] [string] $Path)
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $parts = $line -split "=", 2
        if ($parts.Count -ne 2) { return }
        [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
    }
}

# 前置条件：环境变量可能包含端点 URL 或独立端口；后置条件：返回可用于 TCP 探测的端口号；不变量：无法解析时返回默认端口。
function Get-PortFromEndpoint {
    param(
        [string] $Endpoint,
        [string] $ExplicitPort,
        [int] $DefaultPort
    )
    if ($ExplicitPort) { return [int] $ExplicitPort }
    if ($Endpoint -and $Endpoint -match ':(\d+)(/.*)?$') { return [int] $Matches[1] }
    return $DefaultPort
}

$scriptStartTime = Get-Date
Write-Log ">>> 媒体鉴权测试 - 环境预检查开始..."

$resolvedBackendDir = (Resolve-Path $BackendDir).Path
$envFile = Join-Path $resolvedBackendDir ".env.mailhog.example"
if (-not (Test-Path $envFile)) {
    throw "无法找到配置文件: $envFile"
}

Write-Log "正在从 $envFile 注入环境变量..."
Import-EnvFile -Path $envFile

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_CONFIG_IMPORT = "optional:file:.env.mailhog.example[.properties]"

Write-Log "正在检查测试素材文件..."
$testImage = Join-Path $PSScriptRoot "test-avatar.png"
$licenseImage = Join-Path $PSScriptRoot "test-license.png"
$chatImageFile = Join-Path (Join-Path $PSScriptRoot "fixtures") "chat-image.txt"

if (-not (Test-Path $testImage)) {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] 缺少 test-avatar.png，头像上传测试将失败。"
}
else {
    Write-Log "test-avatar.png 存在" "Green"
}

if (-not (Test-Path $licenseImage)) {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] 缺少 test-license.png，执照上传测试将失败。"
}
else {
    Write-Log "test-license.png 存在" "Green"
}

if (-not (Test-Path $chatImageFile)) {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] 缺少 fixtures/chat-image.txt，聊天图片测试将失败。"
}
else {
    Write-Log "fixtures/chat-image.txt 存在" "Green"
}

Write-Log "正在检查 S3 服务..."
$s3Host = if ($env:MAYOISTAR_S3_HOST) { $env:MAYOISTAR_S3_HOST } else { "127.0.0.1" }
$s3Port = Get-PortFromEndpoint -Endpoint $env:MAYOISTAR_S3_ENDPOINT -ExplicitPort $env:MAYOISTAR_S3_PORT -DefaultPort 9000
if (Test-Port -HostName $s3Host -Port $s3Port) {
    Write-Log "S3 服务探测成功 ($($s3Host):$($s3Port))" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] S3 端口 $s3Port 未响应，文件上传功能将不可用。"
}

Write-Log "正在检查 Redis 服务..."
$redisHost = if ($env:MAYOISTAR_REDIS_HOST) { $env:MAYOISTAR_REDIS_HOST } else { "127.0.0.1" }
$redisPort = if ($env:MAYOISTAR_REDIS_PORT) { [int] $env:MAYOISTAR_REDIS_PORT } elseif ($env:DEV_REDIS_PORT) { [int] $env:DEV_REDIS_PORT } else { 6379 }
if (Test-Port -HostName $redisHost -Port $redisPort) {
    Write-Log "Redis 服务探测成功 ($($redisHost):$($redisPort))" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] Redis 端口 $redisPort 未响应，后端启动后可能报错。"
}

Write-Log "正在检查 MailHog 服务..."
$smtpPort = if ($env:DEV_MAILHOG_SMTP_PORT) { [int] $env:DEV_MAILHOG_SMTP_PORT } else { 1025 }
if (Test-Port -HostName "127.0.0.1" -Port $smtpPort) {
    Write-Log "MailHog SMTP 探测成功 (127.0.0.1:$($smtpPort))" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] MailHog 未启动，邮件发送功能将失效。"
}

$duration = [Math]::Round(((Get-Date) - $scriptStartTime).TotalSeconds, 2)
Write-Log ">>> 所有预检查在 $duration 秒内完成" "Yellow"
Write-Log "正在执行 mvn spring-boot:run..." "Magenta"

Set-Location $resolvedBackendDir
mvn spring-boot:run
