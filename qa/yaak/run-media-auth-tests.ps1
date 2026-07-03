param(
    [string] $BaseUrl = "http://localhost:8080",
    [string] $MailHogApiBase = "http://127.0.0.1:8025",
    [int] $MailTimeoutSeconds = 30
)

# ============================================================================
# 绉嶅瓙璐﹀彿淇℃伅锛堟潵鑷?V2__seed_qa_accounts.sql锛?# ============================================================================
$TestUserEmail = "test_user@mayoistar.qa"
$TestUserPassword = "4g9Pf6KNpw4rxe3NL7hij9l2"
$TestPeerEmail = "test_peer@mayoistar.qa"
$TestPeerPassword = "1QL71Nz-b1aYcP5yzcTn4vSu"
$AdminUsername = "admin"
$AdminPassword = "uMudtQCQ4ZJ9NKOYyYBtdxg5"
$TestUserId = "11111111-1111-1111-1111-111111111111"
$TestPeerId = "22222222-2222-2222-2222-222222222222"
$AdminUserId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

$ErrorActionPreference = "Stop"
$YaakDataDir = "$env:TEMP\mayoistar-media-auth-yaak"
$script:testResults = [System.Collections.ArrayList]::new()
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$MerchantEmail = "yaak-media-merchant.$stamp@example.com"
$MerchantPassword = "Password123!"
$MerchantName = "MayoiStar Media Test Merchant"
$MerchantNickname = "media-merchant-$stamp"

# 妫€鏌ュ熀纭€渚濊禆
if (-not (Get-Command yaak -ErrorAction SilentlyContinue)) {
    throw "yaak CLI 鏈壘鍒帮紝璇峰厛瀹夎 yaak銆?
}
if (-not (Get-Command jq -ErrorAction SilentlyContinue)) {
    Write-Warning "jq 鏈壘鍒帮紝灏嗕娇鐢?PowerShell JSON 澶勭悊銆?
}

# 瑙ｆ瀽 yaak 鍙墽琛屾枃浠惰矾寰?$YaakExecutable = (Get-Command yaak -ErrorAction Stop).Source
$YaakNodeExecutable = $null
$YaakCliScript = $null
if ($YaakExecutable -like "*.ps1") {
    $yaakBaseDir = Split-Path $YaakExecutable -Parent
    $candidateNode = Join-Path $yaakBaseDir "node.exe"
    $YaakNodeExecutable = if (Test-Path $candidateNode) { $candidateNode } else { (Get-Command node -ErrorAction Stop).Source }
    $YaakCliScript = Join-Path $yaakBaseDir "node_modules\@yaakapp\cli\bin\cli.js"
}

$ScriptDir = if ($MyInvocation.MyCommand.Path) { Split-Path -Parent $MyInvocation.MyCommand.Path } else { $PSScriptRoot }
$TestAvatarPath = Join-Path $ScriptDir "test-avatar.png"
$TestLicensePath = Join-Path $ScriptDir "test-license.png"

New-Item -ItemType Directory -Force -Path $YaakDataDir | Out-Null

# ============================================================================
# 杈呭姪鍑芥暟
# ============================================================================

function Write-Log ($Message, $Color = "Cyan") {
    $Timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$Timestamp] $Message" -ForegroundColor $Color
}

function Write-Skip {
    param([string] $Reason)
    Write-Host "  [skip] $Reason" -ForegroundColor Yellow
}

# yaak CLI 璋冪敤鍩虹璁炬柦锛堜笌 run-mailhog-smoke.ps1 涓€鑷达級
function ConvertTo-WindowsProcessArgument {
    param([string] $Argument)
    if ($null -eq $Argument) { return '""' }
    if ($Argument -notmatch '[\s"]') { return $Argument }
    $builder = [System.Text.StringBuilder]::new()
    [void] $builder.Append('"')
    $backslashCount = 0
    foreach ($char in $Argument.ToCharArray()) {
        if ($char -eq '\') { $backslashCount++; continue }
        if ($char -eq '"') {
            [void] $builder.Append('\' * (($backslashCount * 2) + 1))
            [void] $builder.Append('"')
            $backslashCount = 0; continue
        }
        if ($backslashCount -gt 0) {
            [void] $builder.Append('\' * $backslashCount)
            $backslashCount = 0
        }
        [void] $builder.Append($char)
    }
    if ($backslashCount -gt 0) { [void] $builder.Append('\' * ($backslashCount * 2)) }
    [void] $builder.Append('"')
    return $builder.ToString()
}

function Invoke-YaakLines {
    param([Parameter(Mandatory = $true)] [string[]] $Arguments)
    $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processArguments = @()
    if ($script:YaakCliScript) {
        $processInfo.FileName = $script:YaakNodeExecutable
        $processArguments += @($script:YaakCliScript, "--data-dir", $YaakDataDir)
    }
    else {
        $processInfo.FileName = $script:YaakExecutable
        $processArguments += @("--data-dir", $YaakDataDir)
    }
    $processArguments += $Arguments
    $processInfo.UseShellExecute = $false
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    if ($null -ne $processInfo.ArgumentList) {
        foreach ($argument in $processArguments) { [void] $processInfo.ArgumentList.Add($argument) }
    }
    else {
        $processInfo.Arguments = ($processArguments | ForEach-Object { ConvertTo-WindowsProcessArgument $_ }) -join " "
    }
    $process = [System.Diagnostics.Process]::Start($processInfo)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "yaak $($Arguments -join ' ') failed with exit code $($process.ExitCode).`n$stdout`n$stderr"
    }
    if ([string]::IsNullOrWhiteSpace($stdout)) { return @() }
    return $stdout -split "\r?\n" | Where-Object { $_ -ne "" }
}

function Invoke-YaakString {
    param([Parameter(Mandatory = $true)] [string[]] $Arguments)
    return (Invoke-YaakLines -Arguments $Arguments) -join "`n"
}

function Get-ResponseJsonFromYaakOutput {
    param([Parameter(Mandatory = $true)] [string] $RawOutput)
    $normalizedLines = @()
    foreach ($line in ($RawOutput -split "\r?\n")) {
        $trimmed = ($line -replace "`e\[[0-9;]*m", "").Trim()
        if ($trimmed.StartsWith("* ")) { continue }
        if ($trimmed.StartsWith("< ")) { $trimmed = $trimmed.Substring(2).Trim() }
        $normalizedLines += $trimmed
    }
    $normalized = $normalizedLines -join "`n"
    $start = $normalized.LastIndexOf('{"code"')
    if ($start -lt 0) { return "" }
    $depth = 0
    $inString = $false
    $escaped = $false
    for ($index = $start; $index -lt $normalized.Length; $index++) {
        $char = $normalized[$index]
        if ($escaped) { $escaped = $false; continue }
        if ($char -eq '\') { $escaped = $true; continue }
        if ($char -eq '"') { $inString = -not $inString; continue }
        if ($inString) { continue }
        if ($char -eq '{') { $depth++ }
        if ($char -eq '}') {
            $depth--
            if ($depth -eq 0) { return $normalized.Substring($start, $index - $start + 1) }
        }
    }
    return ""
}

function Get-HttpStatusCodeFromYaakOutput {
    param([Parameter(Mandatory = $true)] [string] $RawOutput)
    foreach ($line in ($RawOutput -split "\r?\n")) {
        if ($line -match '^< HTTP/[\d.]+\s+(?<status>\d{3})(?<reason>.*)$') {
            return @{ Code = [int] $matches["status"]; Line = "$($matches["status"])$($matches["reason"])".Trim() }
        }
    }
    return @{ Code = $null; Line = "" }
}

function Get-YaakIdByName {
    param([Parameter(Mandatory = $true)] [string[]] $Lines, [Parameter(Mandatory = $true)] [string] $Name)
    $escaped = [regex]::Escape($Name)
    $line = $Lines | Where-Object { $_ -match "^(?<id>\S+)\s+-\s+.*$escaped$" } | Select-Object -First 1
    if (-not $line) { throw "Cannot find Yaak item named '$Name'." }
    return ([regex]::Match($line, "^(?<id>\S+)")).Groups["id"].Value
}

function Set-YaakEnvironmentVariables {
    param([string] $EnvironmentId, [hashtable] $Variables)
    $environmentJson = Invoke-YaakString -Arguments @("environment", "show", $EnvironmentId)
    $environment = $environmentJson | ConvertFrom-Json
    $merged = @{}
    foreach ($variable in $environment.variables) { $merged[$variable.name] = [string] $variable.value }
    foreach ($key in $Variables.Keys) { $merged[$key] = [string] $Variables[$key] }
    $updatedVariables = @()
    foreach ($key in ($merged.Keys | Sort-Object)) {
        $updatedVariables += @{ name = $key; value = $merged[$key]; enabled = $true }
    }
    $payload = @{
        id = $environment.id; workspaceId = $environment.workspaceId; name = $environment.name
        variables = $updatedVariables
    } | ConvertTo-Json -Depth 8 -Compress
    Invoke-YaakLines -Arguments @("environment", "update", "--json=$payload") | Out-Null
}

# 浠?JSON 鍝嶅簲涓彁鍙栧瓧娈靛€硷紙绌哄€艰繑鍥炵┖瀛楃涓诧級
# 鍓嶇疆鏉′欢锛?Response 鏄悎娉?JSON锛涘悗缃潯浠讹細鑻ュ瓧娈靛瓨鍦ㄥ垯杩斿洖鍏跺瓧绗︿覆鍊硷紝鍚﹀垯杩斿洖绌哄瓧绗︿覆
function Get-JsonField {
    param($Response, [string] $Field)
    try {
        $value = $Response.$Field
        if ($null -eq $value) { return "" }
        return [string] $value
    }
    catch { return "" }
}

# 鍙戦€?yaak 璇锋眰骞舵牎楠?HTTP 鐘舵€佺爜
# 鍓嶇疆鏉′欢锛?Name 瀵瑰簲鐨勮姹傚凡瀛樺湪浜庡綋鍓?workspace锛涘悗缃潯浠讹細杩斿洖瑙ｆ瀽鍚庣殑 JSON 鍝嶅簲瀵硅薄锛屽苟璁板綍娴嬭瘯缁撴灉
function Send-YaakRequestJson {
    param(
        [string] $Name,
        [int[]] $ExpectedCodes = @(200)
    )
    $requestId = Get-YaakIdByName -Lines (Invoke-YaakLines -Arguments @("request", "list", $script:WorkspaceId)) -Name $Name
    $showJson = Invoke-YaakString -Arguments @("request", "show", $requestId)
    $req = $showJson | ConvertFrom-Json

    Write-Host "`n$('鈺? * 60)"
    Write-Host "  $Name"
    Write-Host "$('鈹€' * 60)"
    Write-Host "  $($req.method) $($req.url)"

    if ($req.headers) {
        foreach ($h in $req.headers) {
            if ($h.enabled) { Write-Host "  > $($h.name): $($h.value)" }
        }
    }
    $bodyShown = $false
    if ($req.body) {
        if ($req.body.text) {
            Write-Host "  Body:"
            Write-Host "  $($req.body.text)"
            $bodyShown = $true
        }
        elseif ($req.body.form) {
            Write-Host "  Body (form):"
            foreach ($f in $req.body.form) {
                if ($f.enabled) {
                    $val = if ($f.file) { "[file: $($f.file)]" } else { $f.value }
                    Write-Host "    $($f.name) = $val"
                }
            }
            $bodyShown = $true
        }
    }
    if (-not $bodyShown) { Write-Host "  Body: (none)" }

    $rawOutput = Invoke-YaakString -Arguments @("request", "send", $requestId, "-e", $script:EnvironmentId, "-v")
    $httpStatus = Get-HttpStatusCodeFromYaakOutput -RawOutput $rawOutput
    $respBody = Get-ResponseJsonFromYaakOutput -RawOutput $rawOutput

    Write-Host "$('鈹€' * 60)"
    Write-Host "  鈫?$($httpStatus.Line)"
    Write-Host "  $respBody"

    if ([string]::IsNullOrWhiteSpace($respBody)) {
        $resp = [pscustomobject]@{ code = $httpStatus.Code; message = ""; data = @{} }
    }
    else {
        try { $resp = $respBody | ConvertFrom-Json }
        catch { $resp = [pscustomobject]@{ code = $httpStatus.Code; message = ""; data = @{} } }
    }
    $actualCode = $resp.code
    $passed = $ExpectedCodes -contains $actualCode

    if ($passed) {
        Write-Host "  " -NoNewline
        Write-Host "鉁?PASS" -ForegroundColor Green -NoNewline
        Write-Host " (code=$actualCode)"
    }
    else {
        $expectedStr = $ExpectedCodes -join ','
        Write-Host "  " -NoNewline
        Write-Host "鉁?FAIL" -ForegroundColor Red -NoNewline
        Write-Host " (expected=$expectedStr, actual=$actualCode, message=$($resp.message))"
    }

    [void] $script:testResults.Add(@{
            Name     = $Name
            Expected = $ExpectedCodes
            Actual   = $actualCode
            Passed   = $passed
        })
    return $resp
}

function Send-YaakRequest {
    param([string] $Name, [int[]] $ExpectedCodes = @(200))
    Send-YaakRequestJson -Name $Name -ExpectedCodes $ExpectedCodes | Out-Null
}

function Decode-MailHogBody {
    param([string] $Body)
    $normalized = $Body -replace "=\r?\n", ""
    $normalized = [regex]::Replace($normalized, "=([0-9A-Fa-f]{2})",
        { param($match) [char] [Convert]::ToInt32($match.Groups[1].Value, 16) })
    return [System.Net.WebUtility]::HtmlDecode($normalized)
}

# 浠?MailHog 鑾峰彇閭欢涓殑 token
# 鍓嶇疆鏉′欢锛歁ailHog API 鍙闂紱鍚庣疆鏉′欢锛氳繑鍥炲尮閰嶆敹浠朵汉鍜岀敤閫旂殑 token 瀛楃涓?function Get-MailHogToken {
    param([string] $Recipient, [string] $TokenPurpose)
    $deadline = (Get-Date).AddSeconds($MailTimeoutSeconds)
    do {
        $response = Invoke-RestMethod -Uri "$MailHogApiBase/api/v2/messages" -Method Get
        $items = if ($response.items) { @($response.items) } elseif ($response.Items) { @($response.Items) } else { @() }
        $items = @($items | Sort-Object -Property Created -Descending)
        foreach ($message in $items) {
            $body = Decode-MailHogBody -Body ([string] $message.Content.Body)
            $headers = $message.Content.Headers
            $toHeader = if ($headers.To) { $headers.To -join "," } else { "" }
            if ($toHeader -notmatch [regex]::Escape($Recipient) -and $body -notmatch [regex]::Escape($Recipient)) { continue }
            $match = [regex]::Match($body, '[?&]token=([^\"''&<>\s]+)')
            if ($match.Success) {
                $token = [System.Uri]::UnescapeDataString($match.Groups[1].Value)
                Write-Host "  [MailHog] 鑾峰彇鍒?$TokenPurpose token"
                return $token
            }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Cannot find $TokenPurpose token email for $Recipient in MailHog."
}

function Write-TestSummary {
    $passed = $script:testResults | Where-Object { $_.Passed }
    $failed = $script:testResults | Where-Object { -not $_.Passed }
    Write-Host "`n$('#' * 60)"
    Write-Host "#  娴嬭瘯缁撴灉姹囨€?- 濯掍綋涓嬭浇閴存潈"
    Write-Host "$('#' * 60)"
    Write-Host ""
    Write-Host "  閫氳繃: $($passed.Count) / $($script:testResults.Count)" -ForegroundColor Green
    if ($failed.Count -gt 0) {
        Write-Host "  澶辫触: $($failed.Count)" -ForegroundColor Red
        Write-Host ""
        Write-Host "  澶辫触鐨勬祴璇?"
        foreach ($f in $failed) {
            Write-Host "    " -NoNewline
            Write-Host "鉁?$($f.Name)" -ForegroundColor Red -NoNewline
            Write-Host " (expected=$($f.Expected -join '|'), actual=$($f.Actual))"
        }
    }
    if ($passed.Count -gt 0 -and $passed.Count -eq $script:testResults.Count) {
        Write-Host "  鍏ㄩ儴閫氳繃!" -ForegroundColor Green
    }
}

# 鍒涘缓 yaak workspace
function New-YaakWorkspace {
    param([string] $Name)
    $output = Invoke-YaakString -Arguments @("workspace", "create", "--json", "{`"name`":`"$Name`"}")
    if ($output -match 'Created workspace (\S+)') { return $Matches[1] }
    throw "Failed to create workspace: $output"
}

# 鍒涘缓 yaak environment
function New-YaakEnvironment {
    param([string] $WorkspaceId, [string] $Name, [hashtable] $Variables)
    $vars = @()
    foreach ($key in $Variables.Keys) {
        $vars += @{ name = $key; value = [string] $Variables[$key]; enabled = $true }
    }
    $payload = @{ workspaceId = $WorkspaceId; name = $Name; variables = $vars } | ConvertTo-Json -Depth 4 -Compress
    $output = Invoke-YaakString -Arguments @("environment", "create", "--json", $payload)
    if ($output -match '^(\S+)\s+-') { return $Matches[1] }
    throw "Failed to create environment: $output"
}

# 鍒涘缓 yaak folder
function New-YaakFolder {
    param([string] $WorkspaceId, [string] $Name)
    $output = Invoke-YaakString -Arguments @("folder", "create", $WorkspaceId, "--name", $Name)
    if ($output -match '^(\S+)\s+-') { return $Matches[1] }
    throw "Failed to create folder '$Name': $output"
}

# 鍒涘缓 yaak JSON 璇锋眰
# 鍓嶇疆鏉′欢锛歸orkspace/folder 宸插垱寤猴紱鍚庣疆鏉′欢锛氳繑鍥炲垱寤虹殑璇锋眰 ID
function New-YaakJsonRequest {
    param(
        [string] $WorkspaceId,
        [string] $FolderId,
        [string] $Name,
        [string] $Method,
        [string] $Url,
        [string] $TokenVar,
        [string] $Body
    )
    $headers = @(@{ name = "Content-Type"; value = "application/json" })
    $authType = $null
    $auth = @{}
    if ($TokenVar) {
        $authType = "bearer"
        $auth = @{ token = "`${[ $TokenVar ]}"; prefix = "Bearer" }
    }
    $request = [ordered]@{
        workspaceId        = $WorkspaceId
        folderId           = $FolderId
        name               = $Name
        method             = $Method
        url                = $Url
        headers            = $headers
        authenticationType = $authType
        authentication     = $auth
    }
    if ($Body) {
        $request.bodyType = "application/json"
        $request.body = @{ text = $Body }
    }
    $json = $request | ConvertTo-Json -Depth 4 -Compress
    $output = Invoke-YaakString -Arguments @("request", "create", $WorkspaceId, "--json", $json)
    if ($output -match '^(\S+)\s+-') { return $Matches[1] }
    throw "Failed to create request '$Name': $output"
}

# 鍒涘缓 yaak multipart 璇锋眰锛堢敤浜庢枃浠朵笂浼狅級
# 鍓嶇疆鏉′欢锛?FilePath 鎸囧悜瀛樺湪鐨勬湰鍦版枃浠讹紱鍚庣疆鏉′欢锛氳繑鍥炲垱寤虹殑璇锋眰 ID
function New-YaakMultipartRequest {
    param(
        [string] $WorkspaceId,
        [string] $FolderId,
        [string] $Name,
        [string] $Method,
        [string] $Url,
        [string] $TokenVar,
        [string] $FilePath
    )
    $authType = $null
    $auth = @{}
    if ($TokenVar) {
        $authType = "bearer"
        $auth = @{ token = "`${[ $TokenVar ]}"; prefix = "Bearer" }
    }
    $request = [ordered]@{
        workspaceId        = $WorkspaceId
        folderId           = $FolderId
        name               = $Name
        method             = $Method
        url                = $Url
        headers            = @()
        authenticationType = $authType
        authentication     = $auth
        bodyType           = "multipart/form-data"
        body               = @{
            form = @(
                @{ name = "file"; file = $FilePath; enabled = $true }
            )
        }
    }
    $json = $request | ConvertTo-Json -Depth 4 -Compress
    $output = Invoke-YaakString -Arguments @("request", "create", $WorkspaceId, "--json", $json)
    if ($output -match '^(\S+)\s+-') { return $Matches[1] }
    throw "Failed to create multipart request '$Name': $output"
}

# 鍒涘缓涓嶉渶瑕佽璇佺殑 yaak JSON 璇锋眰锛堢敤浜庡尶鍚嶅拰绛惧悕绡℃敼娴嬭瘯锛?function New-YaakNoAuthJsonRequest {
    param(
        [string] $WorkspaceId,
        [string] $FolderId,
        [string] $Name,
        [string] $Method,
        [string] $Url,
        [string] $Body
    )
    $headers = @()
    if ($Body) { $headers = @(@{ name = "Content-Type"; value = "application/json" }) }
    $request = [ordered]@{
        workspaceId        = $WorkspaceId
        folderId           = $FolderId
        name               = $Name
        method             = $Method
        url                = $Url
        headers            = $headers
        authenticationType = $null
        authentication     = @{}
    }
    if ($Body) {
        $request.bodyType = "application/json"
        $request.body = @{ text = $Body }
    }
    $json = $request | ConvertTo-Json -Depth 4 -Compress
    $output = Invoke-YaakString -Arguments @("request", "create", $WorkspaceId, "--json", $json)
    if ($output -match '^(\S+)\s+-') { return $Matches[1] }
    throw "Failed to create request '$Name': $output"
}

# ============================================================================
# 鍒涘缓宸ヤ綔绌洪棿涓庣幆澧?# ============================================================================

Write-Log ">>> 鍒涘缓 Yaak 宸ヤ綔绌洪棿..." "Magenta"
$script:WorkspaceId = New-YaakWorkspace -Name "MayoiStar Media Auth Tests"

Write-Log ">>> 鍒涘缓 Yaak 鐜..." "Magenta"
$script:EnvironmentId = New-YaakEnvironment -WorkspaceId $script:WorkspaceId -Name "Local" -Variables @{
    baseUrl                   = $BaseUrl
    testUserEmail             = $TestUserEmail
    testUserPassword          = $TestUserPassword
    testPeerEmail             = $TestPeerEmail
    testPeerPassword          = $TestPeerPassword
    adminUsername             = $AdminUsername
    adminPassword             = $AdminPassword
    testUserId                = $TestUserId
    testPeerId                = $TestPeerId
    adminUserId               = $AdminUserId
    merchantEmail             = $MerchantEmail
    merchantPassword          = $MerchantPassword
    merchantNickname          = $MerchantNickname
    merchantName              = $MerchantName
    avatarFile                = $TestAvatarPath
    licenseFile               = $TestLicensePath
    userAccessToken           = ""
    peerAccessToken           = ""
    adminAccessToken          = ""
    merchantAccessToken       = ""
    merchantUserId            = ""
    activationToken           = ""
    merchantActivationToken   = ""
    privateConversationId     = ""
    teamConversationId        = ""
    teamId                    = ""
    privateImageMediaId       = ""
    privateImageSignedUrl     = ""
    teamImageMediaId          = ""
    teamImageSignedUrl        = ""
    licenseMediaId            = ""
    licenseSignedUrl          = ""
    avatarMediaId             = ""
    avatarSignedUrl           = ""
}

Write-Log ">>> 鍒涘缓鏂囦欢澶?.." "Magenta"
$loginFolder = New-YaakFolder -WorkspaceId $script:WorkspaceId -Name "00 鐧诲綍涓庢敞鍐?
$privateFolder = New-YaakFolder -WorkspaceId $script:WorkspaceId -Name "01 绉佽亰鍥剧墖閴存潈"
$groupFolder = New-YaakFolder -WorkspaceId $script:WorkspaceId -Name "02 缇よ亰鍥剧墖閴存潈"
$leaveFolder = New-YaakFolder -WorkspaceId $script:WorkspaceId -Name "03 閫€鍑虹兢鑱婂悗涓嶅彲璁块棶"
$merchantFolder = New-YaakFolder -WorkspaceId $script:WorkspaceId -Name "04 鍟嗗璧勮川閴存潈"
$adminFolder = New-YaakFolder -WorkspaceId $script:WorkspaceId -Name "05 绠＄悊鍛樺叏璧勬簮璁块棶"
$sigFolder = New-YaakFolder -WorkspaceId $script:WorkspaceId -Name "06 绛惧悕瀹屾暣鎬?

# ============================================================================
# 鍒涘缓璇锋眰
# ============================================================================
Write-Log ">>> 鍒涘缓璇锋眰瀹氫箟..." "Magenta"

# --- 00 鐧诲綍 ---
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.01 鐧诲綍 test_user" "POST" "${BaseUrl}/identity/auth/login" "" '{"email":"${[ testUserEmail ]}","password":"${[ testUserPassword ]}"}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.02 鐧诲綍 test_peer" "POST" "${BaseUrl}/identity/auth/login" "" '{"email":"${[ testPeerEmail ]}","password":"${[ testPeerPassword ]}"}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.03 鐧诲綍 admin" "POST" "${BaseUrl}/admin/auth/login" "" '{"username":"${[ adminUsername ]}","password":"${[ adminPassword ]}"}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.04 娉ㄥ唽鍟嗗" "POST" "${BaseUrl}/identity/auth/register/merchant" "" '{"email":"${[ merchantEmail ]}","password":"${[ merchantPassword ]}","nickname":"${[ merchantNickname ]}","name":"${[ merchantName ]}"}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.05 婵€娲诲晢瀹? "POST" "${BaseUrl}/identity/auth/activate" "" '{"token":"${[ merchantActivationToken ]}"}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.06 鐧诲綍鍟嗗" "POST" "${BaseUrl}/identity/auth/login" "" '{"email":"${[ merchantEmail ]}","password":"${[ merchantPassword ]}"}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.07 test_user 鍙戦€佸ソ鍙嬬敵璇? "POST" "${BaseUrl}/social/friend-requests" "userAccessToken" '{"targetUserId":"${[ testPeerId ]}","source":"profile","message":"濯掍綋閴存潈娴嬭瘯 - 娣诲姞濂藉弸"}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.08 test_peer 鍚屾剰濂藉弸鐢宠" "POST" "${BaseUrl}"'/social/friend-requests/${[ friendRequestId ]}/decision' "peerAccessToken" '{"accepted":true}'
New-YaakJsonRequest $script:WorkspaceId $loginFolder "00.09 鑾峰彇绉佽亰浼氳瘽鍒楄〃" "GET" "${BaseUrl}/chat/conversations?page=1&pageSize=20" "userAccessToken"

# --- 01 绉佽亰鍥剧墖閴存潈 ---
New-YaakMultipartRequest $script:WorkspaceId $privateFolder "01.01 涓婁紶绉佽亰鍥剧墖" "POST" "${BaseUrl}/chat/media/images" "userAccessToken" $TestAvatarPath
New-YaakJsonRequest $script:WorkspaceId $privateFolder "01.02 鍙戦€佺鑱婂浘鐗囨秷鎭? "POST" "${BaseUrl}"'/chat/conversations/${[ privateConversationId ]}/messages' "userAccessToken" '{"kind":"image","imageMediaId":"${[ privateImageMediaId ]}"}'
New-YaakJsonRequest $script:WorkspaceId $privateFolder "01.03 绉佽亰鎴愬憳 test_peer 涓嬭浇鍥剧墖" "GET" ("${BaseUrl}" + '${[ privateImageSignedUrl ]}') "peerAccessToken"
New-YaakJsonRequest $script:WorkspaceId $privateFolder "01.04 绠＄悊鍛樹笅杞界鑱婂浘鐗? "GET" ("${BaseUrl}" + '${[ privateImageSignedUrl ]}') "adminAccessToken"
New-YaakNoAuthJsonRequest $script:WorkspaceId $privateFolder "01.05 鍖垮悕涓嬭浇绉佽亰鍥剧墖" "GET" ("${BaseUrl}" + '${[ privateImageSignedUrl ]}')
New-YaakJsonRequest $script:WorkspaceId $privateFolder "01.06 闈炴垚鍛樺晢瀹剁敤鎴蜂笅杞界鑱婂浘鐗? "GET" ("${BaseUrl}" + '${[ privateImageSignedUrl ]}') "merchantAccessToken"

# --- 02 缇よ亰鍥剧墖閴存潈 ---
$ts = Get-Date -Format "yyyyMMddHHmmss"
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.01 鍒涘缓缇よ亰灏忛槦" "POST" "${BaseUrl}/social/teams" "userAccessToken" ('{"name":"濯掍綋閴存潈娴嬭瘯灏忛槦-' + $ts + '","tags":["娴嬭瘯","濯掍綋閴存潈"],"joinMode":"publicJoin","capacity":20,"description":"濯掍綋涓嬭浇閴存潈娴嬭瘯鐢ㄥ皬闃?}')
# 缇よ亰鍥剧墖涓婁紶涓庡彂閫侊紙浣跨敤 test-avatar.png锛?New-YaakMultipartRequest $script:WorkspaceId $groupFolder "02.03 涓婁紶缇よ亰鍥剧墖" "POST" "${BaseUrl}/chat/media/images" "userAccessToken" $TestAvatarPath
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.04 鍙戦€佺兢鑱婂浘鐗囨秷鎭? "POST" "${BaseUrl}"'/chat/conversations/${[ teamConversationId ]}/messages' "userAccessToken" '{"kind":"image","imageMediaId":"${[ teamImageMediaId ]}"}'

# test_peer 鍔犲叆灏忛槦
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.05 test_peer 鍔犲叆灏忛槦" "POST" "${BaseUrl}"'/social/teams/${[ teamId ]}/join' "peerAccessToken" '{"message":"鐢宠鍔犲叆"}'
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.06 缇ゆ垚鍛?test_peer 涓嬭浇鍥剧墖" "GET" ("${BaseUrl}" + '${[ teamImageSignedUrl ]}') "peerAccessToken"
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.07 绠＄悊鍛樹笅杞界兢鑱婂浘鐗? "GET" ("${BaseUrl}" + '${[ teamImageSignedUrl ]}') "adminAccessToken"

# 娉ㄥ唽涓€涓澶栫敤鎴风敤浜庨潪鎴愬憳娴嬭瘯
$outsiderEmail = "yaak-media-outsider.$stamp@example.com"
$outsiderNickname = "media-outsider-$stamp"
Set-YaakEnvironmentVariables $script:EnvironmentId @{
    outsiderEmail = $outsiderEmail
    outsiderPassword = $MerchantPassword
    outsiderNickname = $outsiderNickname
    outsiderAccessToken = ""
    outsiderActivationToken = ""
}
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.08 娉ㄥ唽闈炴垚鍛樼敤鎴? "POST" "${BaseUrl}/identity/auth/register/personal" "" '{"email":"${[ outsiderEmail ]}","password":"${[ outsiderPassword ]}","nickname":"${[ outsiderNickname ]}"}'
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.09 婵€娲婚潪鎴愬憳鐢ㄦ埛" "POST" "${BaseUrl}/identity/auth/activate" "" '{"token":"${[ outsiderActivationToken ]}"}'
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.10 鐧诲綍闈炴垚鍛樼敤鎴? "POST" "${BaseUrl}/identity/auth/login" "" '{"email":"${[ outsiderEmail ]}","password":"${[ outsiderPassword ]}"}'
New-YaakJsonRequest $script:WorkspaceId $groupFolder "02.11 闈炴垚鍛樼敤鎴蜂笅杞界兢鑱婂浘鐗? "GET" ("${BaseUrl}" + '${[ teamImageSignedUrl ]}') "outsiderAccessToken"

# --- 03 閫€鍑虹兢鑱婂悗涓嶅彲璁块棶 ---
New-YaakJsonRequest $script:WorkspaceId $leaveFolder "03.01 test_peer 閫€鍑哄皬闃? "POST" "${BaseUrl}"'/social/teams/${[ teamId ]}/leave' "peerAccessToken"
New-YaakJsonRequest $script:WorkspaceId $leaveFolder "03.02 宸查€€鍑虹敤鎴?test_peer 涓嬭浇鍥剧墖" "GET" ("${BaseUrl}" + '${[ teamImageSignedUrl ]}') "peerAccessToken"
New-YaakJsonRequest $script:WorkspaceId $leaveFolder "03.03 浠嶅湪缇や腑 test_user 涓嬭浇鍥剧墖" "GET" ("${BaseUrl}" + '${[ teamImageSignedUrl ]}') "userAccessToken"
New-YaakJsonRequest $script:WorkspaceId $leaveFolder "03.04 绠＄悊鍛樹笅杞藉凡閫€鍑虹兢鑱婂浘鐗? "GET" ("${BaseUrl}" + '${[ teamImageSignedUrl ]}') "adminAccessToken"

# --- 04 鍟嗗璧勮川閴存潈 ---
New-YaakMultipartRequest $script:WorkspaceId $merchantFolder "04.01 鍟嗗涓婁紶鎵х収" "POST" "${BaseUrl}/identity/media/license" "merchantAccessToken" $TestLicensePath
New-YaakJsonRequest $script:WorkspaceId $merchantFolder "04.02 鍟嗗鏈汉涓嬭浇鎵х収" "GET" ("${BaseUrl}" + '${[ licenseSignedUrl ]}') "merchantAccessToken"
New-YaakJsonRequest $script:WorkspaceId $merchantFolder "04.03 绠＄悊鍛樹笅杞藉晢瀹舵墽鐓? "GET" ("${BaseUrl}" + '${[ licenseSignedUrl ]}') "adminAccessToken"
New-YaakJsonRequest $script:WorkspaceId $merchantFolder "04.04 test_user 闈炴墍鏈夎€呬笅杞藉晢瀹舵墽鐓? "GET" ("${BaseUrl}" + '${[ licenseSignedUrl ]}') "userAccessToken"
New-YaakNoAuthJsonRequest $script:WorkspaceId $merchantFolder "04.05 鍖垮悕涓嬭浇鍟嗗鎵х収" "GET" ("${BaseUrl}" + '${[ licenseSignedUrl ]}')

# --- 05 绠＄悊鍛樺叏璧勬簮璁块棶 ---
New-YaakJsonRequest $script:WorkspaceId $adminFolder "05.01 绠＄悊鍛樹笅杞界鑱婂浘鐗? "GET" ("${BaseUrl}" + '${[ privateImageSignedUrl ]}') "adminAccessToken"
New-YaakJsonRequest $script:WorkspaceId $adminFolder "05.02 绠＄悊鍛樹笅杞界兢鑱婂浘鐗? "GET" ("${BaseUrl}" + '${[ teamImageSignedUrl ]}') "adminAccessToken"
New-YaakJsonRequest $script:WorkspaceId $adminFolder "05.03 绠＄悊鍛樹笅杞藉晢瀹舵墽鐓? "GET" ("${BaseUrl}" + '${[ licenseSignedUrl ]}') "adminAccessToken"

# 涓婁紶澶村儚鑾峰彇鍏紑璧勬簮鐢ㄤ簬娴嬭瘯
New-YaakMultipartRequest $script:WorkspaceId $adminFolder "05.04 涓婁紶娴嬭瘯澶村儚" "POST" "${BaseUrl}/identity/media/avatar" "userAccessToken" $TestAvatarPath
New-YaakJsonRequest $script:WorkspaceId $adminFolder "05.05 绠＄悊鍛樹笅杞藉叕寮€澶村儚" "GET" ("${BaseUrl}" + '${[ avatarSignedUrl ]}') "adminAccessToken"

# --- 06 绛惧悕瀹屾暣鎬?---
# 杩欎簺璇锋眰浣跨敤瀹屾暣鐨勭粷瀵?URL 妯℃澘骞跺湪杩愯鏃跺姩鎬佽缃?New-YaakJsonRequest $script:WorkspaceId $sigFolder "06.01 鏈夋晥绛惧悕涓嬭浇" "GET" ("${BaseUrl}" + '${[ privateImageSignedUrl ]}') "peerAccessToken"
# 绡℃敼绛惧悕锛氬皢 signature 鏈€鍚庝竴浣嶄慨鏀?New-YaakJsonRequest $script:WorkspaceId $sigFolder "06.02 绡℃敼绛惧悕涓嬭浇" "GET" ("${BaseUrl}" + '${[ tamperedSignedUrl ]}') "peerAccessToken"
# 缂哄皯 sig 鍙傛暟
New-YaakJsonRequest $script:WorkspaceId $sigFolder "06.03 缂哄皯绛惧悕鍙傛暟涓嬭浇" "GET" ("${BaseUrl}" + '${[ missingSigUrl ]}') "peerAccessToken"
# 涓嶅瓨鍦ㄧ殑 mediaId
New-YaakJsonRequest $script:WorkspaceId $sigFolder "06.04 涓嶅瓨鍦ㄥ獟浣揑D涓嬭浇" "GET" "${BaseUrl}/media/00000000-0000-0000-0000-000000000000?v=1&policy=publicAccess&scope=&sig=invalid" "userAccessToken"

Write-Log ">>> 璇锋眰鍒涘缓瀹屾垚锛屽紑濮嬫墽琛屾祴璇?.." "Yellow"

# ============================================================================
# 娴嬭瘯鎵ц
# ============================================================================

# 鈹€鈹€ 00: 鐧诲綍涓庡噯澶?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-Host "`n$('#' * 60)"
Write-Host "#  00 鐧诲綍涓庡噯澶?
Write-Host "$('#' * 60)"

Write-Log "姝ｅ湪鐧诲綍 test_user..." "Cyan"
$resp = Send-YaakRequestJson "00.01 鐧诲綍 test_user"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        userAccessToken = $resp.data.tokens.accessToken
        testUserId      = [string] $resp.data.userId
    }
    Write-Host "  [env] userAccessToken, testUserId 宸蹭繚瀛?
}

Write-Log "姝ｅ湪鐧诲綍 test_peer..." "Cyan"
$resp = Send-YaakRequestJson "00.02 鐧诲綍 test_peer"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        peerAccessToken = $resp.data.tokens.accessToken
        testPeerId      = [string] $resp.data.userId
    }
    Write-Host "  [env] peerAccessToken 宸蹭繚瀛?
}

Write-Log "姝ｅ湪鐧诲綍 admin..." "Cyan"
$resp = Send-YaakRequestJson "00.03 鐧诲綍 admin"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ adminAccessToken = $resp.data.tokens.accessToken }
    Write-Host "  [env] adminAccessToken 宸蹭繚瀛?
}

# 娉ㄥ唽涓庢縺娲诲晢瀹?Write-Log "姝ｅ湪娉ㄥ唽鍟嗗..." "Cyan"
Send-YaakRequest "00.04 娉ㄥ唽鍟嗗"
$merchantToken = Get-MailHogToken -Recipient $MerchantEmail -TokenPurpose "鍟嗗婵€娲?
Set-YaakEnvironmentVariables $script:EnvironmentId @{ merchantActivationToken = $merchantToken }

Write-Log "姝ｅ湪婵€娲诲晢瀹?.." "Cyan"
Send-YaakRequest "00.05 婵€娲诲晢瀹?

Write-Log "姝ｅ湪鐧诲綍鍟嗗..." "Cyan"
$resp = Send-YaakRequestJson "00.06 鐧诲綍鍟嗗"
$merchantLoginOk = $false
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        merchantAccessToken = $resp.data.tokens.accessToken
        merchantUserId      = [string] $resp.data.userId
    }
    $merchantLoginOk = $true
    Write-Host "  [env] merchantAccessToken, merchantUserId 宸蹭繚瀛?
}

# 寤虹珛绉佽亰濂藉弸鍏崇郴
Write-Log "姝ｅ湪寤虹珛绉佽亰濂藉弸鍏崇郴..." "Cyan"
$resp = Send-YaakRequestJson "00.07 test_user 鍙戦€佸ソ鍙嬬敵璇?
if ($resp.code -eq 200) {
    $friendRequestId = Get-JsonField -Response $resp.data -Field "requestId"
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ friendRequestId = $friendRequestId }
    Send-YaakRequest "00.08 test_peer 鍚屾剰濂藉弸鐢宠"
}

# 鑾峰彇绉佽亰浼氳瘽 ID
Write-Log "姝ｅ湪鑾峰彇绉佽亰浼氳瘽..." "Cyan"
$resp = Send-YaakRequestJson "00.09 鑾峰彇绉佽亰浼氳瘽鍒楄〃"
if ($resp.code -eq 200) {
    $convId = ""
    if ($resp.data.items) {
        $convId = Get-JsonField -Response $resp.data.items[0] -Field "conversationId"
    }
    if (-not $convId) { $convId = Get-JsonField -Response $resp.data -Field "conversationId" }
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ privateConversationId = $convId }
    Write-Host "  [env] privateConversationId = $convId"
}

# 鈹€鈹€ 01: 绉佽亰鍥剧墖閴存潈 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-Host "`n$('#' * 60)"
Write-Host "#  01 绉佽亰鍥剧墖閴存潈"
Write-Host "$('#' * 60)"

Write-Log "姝ｅ湪涓婁紶绉佽亰鍥剧墖..." "Cyan"
$resp = Send-YaakRequestJson "01.01 涓婁紶绉佽亰鍥剧墖"
if ($resp.code -eq 200) {
    $mediaId = Get-JsonField -Response $resp.data -Field "mediaId"
    $signedUrl = Get-JsonField -Response $resp.data -Field "url"
    if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        privateImageMediaId   = $mediaId
        privateImageSignedUrl = $signedUrl
    }
    Write-Host "  [env] privateImageMediaId=$mediaId, signedUrl=$signedUrl"
}

Write-Log "姝ｅ湪鍙戦€佺鑱婂浘鐗囨秷鎭紙瑙﹀彂绛栫暐鍗囩骇涓?conversationMember锛?.." "Cyan"
Send-YaakRequest "01.02 鍙戦€佺鑱婂浘鐗囨秷鎭?

Write-Log "1.1 绉佽亰鎴愬憳 test_peer 涓嬭浇鍥剧墖 鈫?200" "Cyan"
Send-YaakRequest "01.03 绉佽亰鎴愬憳 test_peer 涓嬭浇鍥剧墖" @(200)

Write-Log "1.2 绠＄悊鍛樹笅杞界鑱婂浘鐗?鈫?200" "Cyan"
Send-YaakRequest "01.04 绠＄悊鍛樹笅杞界鑱婂浘鐗? @(200)

Write-Log "1.3 鍖垮悕涓嬭浇绉佽亰鍥剧墖 鈫?401" "Cyan"
Send-YaakRequest "01.05 鍖垮悕涓嬭浇绉佽亰鍥剧墖" @(401)

if ($merchantLoginOk) {
    Write-Log "1.4 闈炴垚鍛樺晢瀹剁敤鎴蜂笅杞界鑱婂浘鐗?鈫?403" "Cyan"
    Send-YaakRequest "01.06 闈炴垚鍛樺晢瀹剁敤鎴蜂笅杞界鑱婂浘鐗? @(403)
}
else {
    Write-Skip "鍟嗗鏈垚鍔熺櫥褰曪紝璺宠繃闈炴垚鍛樹笅杞界鑱婂浘鐗囩敤渚嬨€?
}

# 鈹€鈹€ 02: 缇よ亰鍥剧墖閴存潈 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-Host "`n$('#' * 60)"
Write-Host "#  02 缇よ亰鍥剧墖閴存潈"
Write-Host "$('#' * 60)"

Write-Log "姝ｅ湪鍒涘缓缇よ亰灏忛槦..." "Cyan"
$resp = Send-YaakRequestJson "02.01 鍒涘缓缇よ亰灏忛槦"
if ($resp.code -eq 200) {
    $teamId = Get-JsonField -Response $resp.data -Field "teamId"
    $chatId = Get-JsonField -Response $resp.data -Field "chatId"
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        teamId              = $teamId
        teamConversationId  = $chatId
    }
    Write-Host "  [env] teamId=$teamId, teamConversationId=$chatId"
}

Write-Log "姝ｅ湪 test_peer 鍔犲叆灏忛槦..." "Cyan"
Send-YaakRequest "02.05 test_peer 鍔犲叆灏忛槦"

Write-Log "姝ｅ湪涓婁紶缇よ亰鍥剧墖..." "Cyan"
$resp = Send-YaakRequestJson "02.03 涓婁紶缇よ亰鍥剧墖"
if ($resp.code -eq 200) {
    $mediaId = Get-JsonField -Response $resp.data -Field "mediaId"
    $signedUrl = Get-JsonField -Response $resp.data -Field "url"
    if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        teamImageMediaId   = $mediaId
        teamImageSignedUrl = $signedUrl
    }
    Write-Host "  [env] teamImageMediaId=$mediaId, signedUrl=$signedUrl"
}

Write-Log "姝ｅ湪鍙戦€佺兢鑱婂浘鐗囨秷鎭紙瑙﹀彂绛栫暐鍗囩骇涓?conversationMember锛?.." "Cyan"
Send-YaakRequest "02.04 鍙戦€佺兢鑱婂浘鐗囨秷鎭?

Write-Log "2.1 缇ゆ垚鍛?test_peer 涓嬭浇缇よ亰鍥剧墖 鈫?200" "Cyan"
Send-YaakRequest "02.06 缇ゆ垚鍛?test_peer 涓嬭浇鍥剧墖" @(200)

Write-Log "2.2 绠＄悊鍛樹笅杞界兢鑱婂浘鐗?鈫?200" "Cyan"
Send-YaakRequest "02.07 绠＄悊鍛樹笅杞界兢鑱婂浘鐗? @(200)

# 娉ㄥ唽闈炴垚鍛樼敤鎴峰苟娴嬭瘯
Write-Log "姝ｅ湪娉ㄥ唽闈炴垚鍛樼敤鎴?.." "Cyan"
Send-YaakRequest "02.08 娉ㄥ唽闈炴垚鍛樼敤鎴?
$outsiderToken = Get-MailHogToken -Recipient $outsiderEmail -TokenPurpose "闈炴垚鍛樼敤鎴锋縺娲?
Set-YaakEnvironmentVariables $script:EnvironmentId @{ outsiderActivationToken = $outsiderToken }

Send-YaakRequest "02.09 婵€娲婚潪鎴愬憳鐢ㄦ埛"
$resp = Send-YaakRequestJson "02.10 鐧诲綍闈炴垚鍛樼敤鎴?
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ outsiderAccessToken = $resp.data.tokens.accessToken }
}

Write-Log "2.3 闈炴垚鍛樼敤鎴蜂笅杞界兢鑱婂浘鐗?鈫?403" "Cyan"
Send-YaakRequest "02.11 闈炴垚鍛樼敤鎴蜂笅杞界兢鑱婂浘鐗? @(403)

# 鈹€鈹€ 03: 閫€鍑虹兢鑱婂悗涓嶅彲璁块棶 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-Host "`n$('#' * 60)"
Write-Host "#  03 閫€鍑虹兢鑱婂悗涓嶅彲璁块棶"
Write-Host "$('#' * 60)"

Write-Log "test_peer 閫€鍑哄皬闃?.." "Cyan"
Send-YaakRequest "03.01 test_peer 閫€鍑哄皬闃?

Write-Log "3.1 宸查€€鍑虹殑 test_peer 涓嬭浇缇よ亰鍥剧墖 鈫?403" "Cyan"
Send-YaakRequest "03.02 宸查€€鍑虹敤鎴?test_peer 涓嬭浇鍥剧墖" @(403)

Write-Log "3.2 浠嶅湪缇や腑 test_user 涓嬭浇缇よ亰鍥剧墖 鈫?200" "Cyan"
Send-YaakRequest "03.03 浠嶅湪缇や腑 test_user 涓嬭浇鍥剧墖" @(200)

Write-Log "3.3 绠＄悊鍛樹笅杞藉凡閫€鍑虹兢鑱婄殑鍥剧墖 鈫?200" "Cyan"
Send-YaakRequest "03.04 绠＄悊鍛樹笅杞藉凡閫€鍑虹兢鑱婂浘鐗? @(200)

# 鈹€鈹€ 04: 鍟嗗璧勮川閴存潈 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-Host "`n$('#' * 60)"
Write-Host "#  04 鍟嗗璧勮川閴存潈"
Write-Host "$('#' * 60)"

if (-not $merchantLoginOk) {
    Write-Skip "鍟嗗鏈垚鍔熺櫥褰曪紝璺宠繃鍏ㄩ儴鍟嗗璧勮川閴存潈鐢ㄤ緥銆?
}
else {
    Write-Log "鍟嗗涓婁紶鎵х収..." "Cyan"
    $resp = Send-YaakRequestJson "04.01 鍟嗗涓婁紶鎵х収"
    if ($resp.code -eq 200) {
        $mediaId = Get-JsonField -Response $resp.data -Field "mediaId"
        $signedUrl = Get-JsonField -Response $resp.data -Field "url"
        if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
        Set-YaakEnvironmentVariables $script:EnvironmentId @{
            licenseMediaId  = $mediaId
            licenseSignedUrl = $signedUrl
        }
        Write-Host "  [env] licenseMediaId=$mediaId, signedUrl=$signedUrl"
    }

    Write-Log "4.1 鍟嗗鏈汉涓嬭浇鎵х収 鈫?200" "Cyan"
    Send-YaakRequest "04.02 鍟嗗鏈汉涓嬭浇鎵х収" @(200)

    Write-Log "4.2 绠＄悊鍛樹笅杞藉晢瀹舵墽鐓?鈫?200" "Cyan"
    Send-YaakRequest "04.03 绠＄悊鍛樹笅杞藉晢瀹舵墽鐓? @(200)

    Write-Log "4.3 test_user 闈炴墍鏈夎€呬笅杞藉晢瀹舵墽鐓?鈫?403" "Cyan"
    Send-YaakRequest "04.04 test_user 闈炴墍鏈夎€呬笅杞藉晢瀹舵墽鐓? @(403)

    Write-Log "4.4 鍖垮悕涓嬭浇鍟嗗鎵х収 鈫?401" "Cyan"
    Send-YaakRequest "04.05 鍖垮悕涓嬭浇鍟嗗鎵х収" @(401)
}

# 鈹€鈹€ 05: 绠＄悊鍛樺叏璧勬簮璁块棶 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-Host "`n$('#' * 60)"
Write-Host "#  05 绠＄悊鍛樺叏璧勬簮璁块棶"
Write-Host "$('#' * 60)"

Write-Log "5.1 绠＄悊鍛樹笅杞界鑱婂浘鐗?鈫?200" "Cyan"
Send-YaakRequest "05.01 绠＄悊鍛樹笅杞界鑱婂浘鐗? @(200)

Write-Log "5.2 绠＄悊鍛樹笅杞界兢鑱婂浘鐗?鈫?200" "Cyan"
Send-YaakRequest "05.02 绠＄悊鍛樹笅杞界兢鑱婂浘鐗? @(200)

Write-Log "5.3 绠＄悊鍛樹笅杞藉晢瀹舵墽鐓?鈫?200" "Cyan"
Send-YaakRequest "05.03 绠＄悊鍛樹笅杞藉晢瀹舵墽鐓? @(200)

Write-Log "涓婁紶娴嬭瘯澶村儚..." "Cyan"
$resp = Send-YaakRequestJson "05.04 涓婁紶娴嬭瘯澶村儚"
if ($resp.code -eq 200) {
    $signedUrl = Get-JsonField -Response $resp.data -Field "url"
    if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
    $mediaId = Get-JsonField -Response $resp.data -Field "mediaId"
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        avatarSignedUrl = $signedUrl
        avatarMediaId   = $mediaId
    }
    Write-Host "  [env] avatarSignedUrl=$signedUrl"
}

Write-Log "5.4 绠＄悊鍛樹笅杞藉叕寮€澶村儚 鈫?200" "Cyan"
Send-YaakRequest "05.05 绠＄悊鍛樹笅杞藉叕寮€澶村儚" @(200)

# 鈹€鈹€ 06: 绛惧悕瀹屾暣鎬?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-Host "`n$('#' * 60)"
Write-Host "#  06 绛惧悕瀹屾暣鎬?
Write-Host "$('#' * 60)"

# 鏋勯€犵鏀圭鍚嶅拰缂哄け绛惧悕鐨?URL
$privateUrl = (Invoke-YaakString -Arguments @("environment", "show", $script:EnvironmentId) | ConvertFrom-Json).variables | Where-Object { $_.name -eq "privateImageSignedUrl" } | Select-Object -First 1
$signedUrlValue = if ($privateUrl) { $privateUrl.value } else { "" }

if ($signedUrlValue -and $signedUrlValue -match '&sig=([^&]+)') {
    $origSig = $Matches[1]
    # 绡℃敼绛惧悕锛氫慨鏀规渶鍚庝竴浣?    $tamperedSig = if ($origSig.Length -gt 1) { $origSig.Substring(0, $origSig.Length - 1) + (([int][char]($origSig[-1]) + 1) % 10) } else { "invalid" }
    $tamperedUrl = $signedUrlValue -replace [regex]::Escape("&sig=$origSig"), "&sig=$tamperedSig"
    $missingSigUrl = $signedUrlValue -replace '&sig=[^&]+', ''

    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        tamperedSignedUrl = $tamperedUrl
        missingSigUrl     = $missingSigUrl
    }
    Write-Host "  [env] 宸叉瀯閫犵鏀圭鍚?URL 鍜屾棤绛惧悕 URL"
}
else {
    Write-Skip "鏃犳硶瑙ｆ瀽 signedUrl锛岃烦杩囩鍚嶅畬鏁存€ф祴璇曘€?
}

Write-Log "6.1 鏈夋晥绛惧悕涓嬭浇 鈫?200" "Cyan"
Send-YaakRequest "06.01 鏈夋晥绛惧悕涓嬭浇" @(200)

Write-Log "6.2 绡℃敼绛惧悕涓嬭浇 鈫?403" "Cyan"
Send-YaakRequest "06.02 绡℃敼绛惧悕涓嬭浇" @(403)

Write-Log "6.3 缂哄皯绛惧悕鍙傛暟涓嬭浇 鈫?403" "Cyan"
Send-YaakRequest "06.03 缂哄皯绛惧悕鍙傛暟涓嬭浇" @(403)

Write-Log "6.4 涓嶅瓨鍦ㄥ獟浣揑D涓嬭浇 鈫?404" "Cyan"
Send-YaakRequest "06.04 涓嶅瓨鍦ㄥ獟浣揑D涓嬭浇" @(404)

# 鈹€鈹€ 姹囨€?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
Write-TestSummary
