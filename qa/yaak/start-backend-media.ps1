param(
    [string] $BackendDir = (Join-Path $PSScriptRoot "..\..\backend")
)

# 瀹氫箟甯︽椂闂寸殑鏃ュ織杈撳嚭鍑芥暟
function Write-Log ($Message, $Color = "Cyan") {
    $Timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$Timestamp] $Message" -ForegroundColor $Color
}

# 瀹氫箟蹇€?TCP 鎺㈡祴鍑芥暟
function Test-Port ($HostName, $Port, $TimeoutMs = 2000) {
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
        # 寮哄埗灏?localhost 鏇挎崲涓?127.0.0.1 閬垮厤 IPv6 瑙ｆ瀽鍧?        $target = if ($HostName -eq "localhost") { "127.0.0.1" } else { $HostName }
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
Write-Log ">>> 濯掍綋閴存潈娴嬭瘯 - 鐜棰勬鏌ュ紑濮?.."

$ErrorActionPreference = "Stop"

# 1. 璺緞瑙ｆ瀽
$resolvedBackendDir = (Resolve-Path $BackendDir).Path
$envFile = Join-Path $resolvedBackendDir ".env.mailhog.example"
if (-not (Test-Path $envFile)) {
    throw "鏃犳硶鎵惧埌閰嶇疆鏂囦欢: $envFile"
}

# 2. 鍔犺浇鐜鍙橀噺骞惰В鏋?Write-Log "姝ｅ湪浠?$envFile 娉ㄥ叆鐜鍙橀噺..."
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $parts = $line -split "=", 2
    if ($parts.Count -ne 2) { return }
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
}

# 娉ㄥ叆 Spring 鐗规湁鍙橀噺
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_CONFIG_IMPORT = "optional:file:.env.mailhog.example[.properties]"

# 3. 妫€鏌ユ祴璇曠礌鏉愭枃浠?Write-Log "姝ｅ湪妫€鏌ユ祴璇曠礌鏉愭枃浠?.."
$testImage = Join-Path $PSScriptRoot "test-avatar.png"
$licenseImage = Join-Path $PSScriptRoot "test-license.png"
$chatImageFile = Join-Path (Join-Path $PSScriptRoot "fixtures") "chat-image.txt"

if (-not (Test-Path $testImage)) {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] 缂哄皯 test-avatar.png锛屽ご鍍忎笂浼犳祴璇曞皢澶辫触銆?
}
else {
    Write-Log "test-avatar.png 瀛樺湪" "Green"
}

if (-not (Test-Path $licenseImage)) {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] 缂哄皯 test-license.png锛屾墽鐓т笂浼犳祴璇曞皢澶辫触銆?
}
else {
    Write-Log "test-license.png 瀛樺湪" "Green"
}

if (-not (Test-Path $chatImageFile)) {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] 缂哄皯 fixtures/chat-image.txt锛岃亰澶╁浘鐗囨祴璇曞皢澶辫触銆?
}
else {
    Write-Log "fixtures/chat-image.txt 瀛樺湪" "Green"
}

# 4. 妫€鏌?S3 (閫傞厤 MAYOISTAR_S3_ENDPOINT)
Write-Log "姝ｅ湪妫€鏌?S3 鏈嶅姟..."
$s3Host = "127.0.0.1"
$s3Port = 9000
if ($env:MAYOISTAR_S3_ENDPOINT -match ':(\d+)') {
    $s3Port = $Matches[1]
}

if (Test-Port $s3Host $s3Port) {
    Write-Log "S3 鏈嶅姟鎺㈡祴鎴愬姛 (${s3Host}:${s3Port})" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] S3 绔彛 ${s3Port} 鏈搷搴斻€傛枃浠朵笂浼犲姛鑳藉皢涓嶅彲鐢ㄣ€?
}

# 5. 妫€鏌?Redis (閫傞厤 DEV_REDIS_PORT)
Write-Log "姝ｅ湪妫€鏌?Redis 鏈嶅姟..."
$redisHost = "127.0.0.1"
$redisPort = if ($env:DEV_REDIS_PORT) { $env:DEV_REDIS_PORT } else { "6379" }

if (Test-Port $redisHost $redisPort) {
    Write-Log "Redis 鏈嶅姟鎺㈡祴鎴愬姛 (${redisHost}:${redisPort})" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] Redis 绔彛 ${redisPort} 鏈搷搴斻€傚悗绔惎鍔ㄥ悗鍙兘鎶ラ敊銆?
}

# 6. 妫€鏌?MailHog (SMTP)
Write-Log "姝ｅ湪妫€鏌?MailHog 鏈嶅姟..."
$smtpPort = if ($env:DEV_MAILHOG_SMTP_PORT) { $env:DEV_MAILHOG_SMTP_PORT } else { "1025" }
if (Test-Port "127.0.0.1" $smtpPort) {
    Write-Log "MailHog SMTP 鎺㈡祴鎴愬姛 (127.0.0.1:${smtpPort})" "Green"
}
else {
    Write-Warning "[$((Get-Date -Format 'HH:mm:ss'))] MailHog 鏈惎鍔紝閭欢鍙戦€佸姛鑳藉皢澶辨晥銆?
}

# 7. 杩愯鍚庣
$Duration = [Math]::Round(((Get-Date) - $ScriptStartTime).TotalSeconds, 2)
Write-Log ">>> 鎵€鏈夐妫€鏌ュ湪 ${Duration} 绉掑唴瀹屾垚" "Yellow"
Write-Log "姝ｅ湪鎵ц mvn spring-boot:run..." "Magenta"

Set-Location $resolvedBackendDir
mvn spring-boot:run
