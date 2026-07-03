param(
    [string] $BackendDir = (Join-Path $PSScriptRoot "..\..\backend")
)

# 定义带时间的日志输出函数
function Write-Log ($Message, $Color = "Cyan") {
    $Timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$Timestamp] $Message" -ForegroundColor $Color
}

# 定义快速 TCP 探测函数
function Test-Port ($HostName, $Port, $TimeoutMs = 2000) {
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
        # 强制将 localhost 替换为 127.0.0.1 避免 IPv6 解析坑
        $target = if ($HostName -eq "localhost") { "127.0.0.1" } else { $HostName }
        $connect = $tcp.BeginConnect($target, [int]$Port, $null, $null)
        $wait = $connect.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if (-not $wait -or -not $tcp.Connected) { return $false }
        $tcp.EndConnect($connect)
        return $true
    }
    catch { return $false }
    finally { $tcp.Close() }
}

$ScriptStartTime = Get-Date
Write-Log ">>> 脚本启动：环境预检查开始..."

$ErrorActionPreference = "Stop"

# 1. 路径解析
$resolvedBackendDir = (Resolve-Path $BackendDir).Path
$envFile = Join-Path $resolvedBackendDir ".env.mailhog.example"
if (-not (Test-Path $envFile)) {
    throw "无法找到配置文件: $envFile"
}

# 2. 加载环境变量并解析
Write-Log "正在从 $envFile 注入环境变量..."
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $parts = $line -split "=", 2
    if ($parts.Count -ne 2) { return }
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
}

# 注入 Spring 特有变量
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_CONFIG_IMPORT = "optional:file:.env.mailhog.example[.properties]"

# 3. 检查 S3 (适配 MAYOISTAR_S3_ENDPOINT)
Write-Log "正在检查 S3 服务..."
$s3Host = "127.0.0.1"
$s3Port = 9000
# 尝试从 endpoint 字符串解析端口：http://localhost:9000
if ($env:MAYOISTAR_S3_ENDPOINT -match ':(\d+)') {
    $s3Port = $Matches[1]
}

if (Test-Port $s3Host $s3Port) {
    Write-Log "S3 服务探测成功 (${s3Host}:${s3Port})" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] S3 端口 ${s3Port} 未响应。文件上传功能将不可用。"
}

# 4. 检查 Redis (适配 DEV_REDIS_PORT)
Write-Log "正在检查 Redis 服务..."
$redisHost = "127.0.0.1"
$redisPort = if ($env:DEV_REDIS_PORT) { $env:DEV_REDIS_PORT } else { "6379" }

if (Test-Port $redisHost $redisPort) {
    Write-Log "Redis 服务探测成功 (${redisHost}:${redisPort})" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] Redis 端口 ${redisPort} 未响应。后端启动后可能报错。"
}

# 5. 检查 CLIP 边车服务 (适配 MAYOISTAR_CLIP_ENDPOINT)
Write-Log "正在检查 CLIP 边车服务..."
$clipHost = "127.0.0.1"
$clipPort = 8000
# 尝试从 endpoint 字符串解析端口：http://localhost:8000
if ($env:MAYOISTAR_CLIP_ENDPOINT -match '://([^:]+):(\d+)$') {
    $clipHost = if ($Matches[1] -eq "localhost") { "127.0.0.1" } else { $Matches[1] }
    $clipPort = $Matches[2]
}

if (Test-Port $clipHost $clipPort) {
    Write-Log "CLIP 边车服务探测成功 (${clipHost}:${clipPort})" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] CLIP 边车服务端口 ${clipPort} 未响应。AI 图片分类功能将不可用。"
}

# 6. 运行后端
$Duration = [Math]::Round(((Get-Date) - $ScriptStartTime).TotalSeconds, 2)
Write-Log ">>> 所有预检查在 ${Duration} 秒内完成" "Yellow"
Write-Log "正在执行 mvn spring-boot:run..." "Magenta"

Set-Location $resolvedBackendDir
mvn spring-boot:run
