param(
    [string] $WorkspaceName = "MayoiStar Media Download Authorization",
    [string] $MailHogApiBase = "http://127.0.0.1:8025",
    [string] $BaseUrl = "http://localhost:8080",
    [int] $MailTimeoutSeconds = 30
)

# ============================================================================
# 种子账号信息（来自 V2__seed_qa_accounts.sql）
# ============================================================================
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
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$script:testResults = [System.Collections.ArrayList]::new()
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$MerchantEmail = "yaak-media-merchant.$stamp@example.com"
$MerchantPassword = "Password123!"
$MerchantName = "MayoiStar Media Test Merchant"
$MerchantNickname = "media-merchant-$stamp"
$OutsiderEmail = "yaak-media-outsider.$stamp@example.com"
$OutsiderNickname = "media-outsider-$stamp"
$TeamName = "媒体鉴权测试小队-$stamp"

$ScriptDir = if ($MyInvocation.MyCommand.Path) { Split-Path -Parent $MyInvocation.MyCommand.Path } else { $PSScriptRoot }
$TestAvatarPath = Join-Path $ScriptDir "test-avatar.png"
$TestLicensePath = Join-Path $ScriptDir "test-license.png"

# 检查基础依赖
if (-not (Get-Command yaak -ErrorAction SilentlyContinue)) {
    throw "yaak CLI 未找到，请先安装 yaak。"
}
if (-not (Test-Path $TestAvatarPath)) {
    throw "测试图片文件未找到: $TestAvatarPath"
}
if (-not (Test-Path $TestLicensePath)) {
    throw "测试执照文件未找到: $TestLicensePath"
}

# 解析 yaak 可执行文件路径
$YaakExecutable = (Get-Command yaak -ErrorAction Stop).Source
$YaakNodeExecutable = $null
$YaakCliScript = $null
if ($YaakExecutable -like "*.ps1") {
    $yaakBaseDir = Split-Path $YaakExecutable -Parent
    $candidateNode = Join-Path $yaakBaseDir "node.exe"
    $YaakNodeExecutable = if (Test-Path $candidateNode) { $candidateNode } else { (Get-Command node -ErrorAction Stop).Source }
    $YaakCliScript = Join-Path $yaakBaseDir "node_modules\@yaakapp\cli\bin\cli.js"
}

# ============================================================================
# 辅助函数（与 run-mailhog-smoke.ps1 一致）
# ============================================================================

function Write-Log ($Message, $Color = "Cyan") {
    $Timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$Timestamp] $Message" -ForegroundColor $Color
}

function Write-Skip {
    param([string] $Reason)
    Write-Host "  [skip] $Reason" -ForegroundColor Yellow
}

# 从 JSON 响应中提取字段值（空值返回空字符串）
# 前置条件：$Response 是合法 JSON；后置条件：若字段存在则返回其字符串值，否则返回空字符串
function Get-JsonField {
    param($Response, [string] $Field)
    try {
        $value = $Response.$Field
        if ($null -eq $value) { return "" }
        return [string] $value
    }
    catch { return "" }
}

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
        $processArguments += @($script:YaakCliScript)
    }
    else {
        $processInfo.FileName = $script:YaakExecutable
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

function Get-YaakWorkspaceId {
    param([string] $Name)
    $workspaces = Invoke-YaakLines -Arguments @("workspace", "list")
    return Get-YaakIdByName -Lines $workspaces -Name $Name
}

function Get-YaakEnvironmentId {
    param([string] $WorkspaceId)
    $environments = Invoke-YaakLines -Arguments @("environment", "list", $WorkspaceId)
    $line = $environments | Select-Object -First 1
    if (-not $line) { throw "No Yaak environment found in workspace $WorkspaceId." }
    return ([regex]::Match($line, "^(?<id>\S+)")).Groups["id"].Value
}

function Get-YaakRequestId {
    param([string] $WorkspaceId, [string] $Name)
    $requests = Invoke-YaakLines -Arguments @("request", "list", $WorkspaceId)
    return Get-YaakIdByName -Lines $requests -Name $Name
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

# 发送请求，显示详情，比对预期 code，返回响应 JSON
# 前置条件：$script:WorkspaceId、$script:EnvironmentId 已设置
function Send-YaakRequestJson {
    param(
        [string] $Name,
        [int[]] $ExpectedCodes = @(200)
    )
    $requestId = Get-YaakRequestId -WorkspaceId $script:WorkspaceId -Name $Name
    $showJson = Invoke-YaakString -Arguments @("request", "show", $requestId)
    $req = $showJson | ConvertFrom-Json

    Write-Host "`n$('=' * 60)"
    Write-Host "  $Name"
    Write-Host "$('-' * 60)"
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

    Write-Host "$('-' * 60)"
    Write-Host "  <- $($httpStatus.Line)"
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
        Write-Host "PASS" -ForegroundColor Green -NoNewline
        Write-Host " (code=$actualCode)"
    }
    else {
        $expectedStr = $ExpectedCodes -join ','
        Write-Host "  " -NoNewline
        Write-Host "FAIL" -ForegroundColor Red -NoNewline
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

# 从 MailHog 获取邮件中的 token
# 前置条件：MailHog API 可访问；后置条件：返回匹配收件人和用途的 token 字符串
function Get-MailHogToken {
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
                Write-Host "  [MailHog] 获取到 $TokenPurpose token"
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
    Write-Host "#  测试结果汇总 - 媒体下载鉴权"
    Write-Host "$('#' * 60)"
    Write-Host ""
    Write-Host "  通过: $($passed.Count) / $($script:testResults.Count)" -ForegroundColor Green
    if ($failed.Count -gt 0) {
        Write-Host "  失败: $($failed.Count)" -ForegroundColor Red
        Write-Host ""
        Write-Host "  失败的测试:"
        foreach ($f in $failed) {
            Write-Host "    " -NoNewline
            Write-Host "FAIL $($f.Name)" -ForegroundColor Red -NoNewline
            Write-Host " (expected=$($f.Expected -join '|'), actual=$($f.Actual))"
        }
    }
    if ($passed.Count -gt 0 -and $passed.Count -eq $script:testResults.Count) {
        Write-Host "  全部通过!" -ForegroundColor Green
    }
}

# ============================================================================
# 获取 Yaak 工作空间与环境
# ============================================================================

Write-Log ">>> 正在连接 Yaak 工作空间..." "Magenta"
$script:WorkspaceId = Get-YaakWorkspaceId -Name $WorkspaceName
$script:EnvironmentId = Get-YaakEnvironmentId -WorkspaceId $script:WorkspaceId
Write-Log "Workspace: $script:WorkspaceId"
Write-Log "Environment: $script:EnvironmentId"

# 设置初始环境变量
Write-Log ">>> 设置初始环境变量..." "Magenta"
Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
    baseUrl              = $BaseUrl
    testUserEmail        = $TestUserEmail
    testUserPassword     = $TestUserPassword
    testPeerEmail        = $TestPeerEmail
    testPeerPassword     = $TestPeerPassword
    adminUsername        = $AdminUsername
    adminPassword        = $AdminPassword
    testUserId           = $TestUserId
    testPeerId           = $TestPeerId
    adminUserId          = $AdminUserId
    merchantEmail        = $MerchantEmail
    merchantPassword     = $MerchantPassword
    merchantNickname     = $MerchantNickname
    merchantName         = $MerchantName
    teamName             = $TeamName
    outsiderEmail        = $OutsiderEmail
    outsiderPassword     = $MerchantPassword
    outsiderNickname     = $OutsiderNickname
    avatarFile           = $TestAvatarPath
    licenseFile          = $TestLicensePath
    merchantActivationToken = ""
    outsiderActivationToken = ""
    userAccessToken      = ""
    peerAccessToken      = ""
    adminAccessToken     = ""
    merchantAccessToken  = ""
    outsiderAccessToken  = ""
    merchantUserId       = ""
    friendRequestId      = ""
    privateConversationId = ""
    teamId               = ""
    teamConversationId   = ""
    privateImageMediaId  = ""
    privateImageSignedUrl = ""
    teamImageMediaId     = ""
    teamImageSignedUrl   = ""
    licenseMediaId       = ""
    licenseSignedUrl     = ""
    avatarSignedUrl      = ""
    tamperedSignedUrl    = ""
    missingSigUrl        = ""
}

# ============================================================================
# 00: 登录与准备
# ============================================================================
Write-Host "`n$('#' * 60)"
Write-Host "#  00 登录与准备"
Write-Host "$('#' * 60)"

Write-Log "正在登录 test_user..." "Cyan"
$resp = Send-YaakRequestJson "00.01 登录 test_user"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        userAccessToken = $resp.data.tokens.accessToken
        testUserId      = [string] $resp.data.userId
    }
    Write-Host "  [env] userAccessToken 已保存"
}

Write-Log "正在登录 test_peer..." "Cyan"
$resp = Send-YaakRequestJson "00.02 登录 test_peer"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        peerAccessToken = $resp.data.tokens.accessToken
        testPeerId      = [string] $resp.data.userId
    }
    Write-Host "  [env] peerAccessToken 已保存"
}

Write-Log "正在登录 admin..." "Cyan"
$resp = Send-YaakRequestJson "00.03 登录 admin"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ adminAccessToken = $resp.data.tokens.accessToken }
    Write-Host "  [env] adminAccessToken 已保存"
}

# 注册与激活商家
Write-Log "正在注册商家..." "Cyan"
Send-YaakRequest "00.04 注册商家"

Write-Log "正在获取商家激活 token..." "Cyan"
$merchantToken = Get-MailHogToken -Recipient $MerchantEmail -TokenPurpose "商家激活"
Set-YaakEnvironmentVariables $script:EnvironmentId @{ merchantActivationToken = $merchantToken }

Write-Log "正在激活商家..." "Cyan"
Send-YaakRequest "00.05 激活商家"

Write-Log "正在登录商家..." "Cyan"
$resp = Send-YaakRequestJson "00.06 登录商家"
$merchantLoginOk = $false
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        merchantAccessToken = $resp.data.tokens.accessToken
        merchantUserId      = [string] $resp.data.userId
    }
    $merchantLoginOk = $true
    Write-Host "  [env] merchantAccessToken 已保存"
}

# 建立私聊好友关系
Write-Log "正在建立私聊好友关系..." "Cyan"
$resp = Send-YaakRequestJson "00.07 test_user 发送好友申请"
if ($resp.code -eq 200) {
    $friendRequestId = Get-JsonField -Response $resp.data -Field "requestId"
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ friendRequestId = $friendRequestId }
    Write-Log "正在同意好友申请..." "Cyan"
    Send-YaakRequest "00.08 test_peer 同意好友申请"
}

# 获取私聊会话 ID
Write-Log "正在获取私聊会话..." "Cyan"
$resp = Send-YaakRequestJson "00.09 获取私聊会话列表"
if ($resp.code -eq 200) {
    $convId = ""
    if ($resp.data.items) {
        $convId = Get-JsonField -Response $resp.data.items[0] -Field "conversationId"
    }
    if (-not $convId) { $convId = Get-JsonField -Response $resp.data -Field "conversationId" }
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ privateConversationId = $convId }
    Write-Host "  [env] privateConversationId = $convId"
}

# ============================================================================
# 01: 私聊图片鉴权
# ============================================================================
Write-Host "`n$('#' * 60)"
Write-Host "#  01 私聊图片鉴权"
Write-Host "$('#' * 60)"

Write-Log "正在上传私聊图片..." "Cyan"
$resp = Send-YaakRequestJson "01.01 上传私聊图片"
if ($resp.code -eq 200) {
    $mediaId = Get-JsonField -Response $resp.data -Field "mediaId"
    $signedUrl = Get-JsonField -Response $resp.data -Field "url"
    if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        privateImageMediaId   = $mediaId
        privateImageSignedUrl = $signedUrl
    }
    Write-Host "  [env] privateImageMediaId=$mediaId"
}

Write-Log "正在发送私聊图片消息（触发策略升级为 conversationMember）..." "Cyan"
$resp = Send-YaakRequestJson "01.02 发送私聊图片消息"
if ($resp.code -eq 200 -and $resp.data.image) {
    $signedUrl = Get-JsonField -Response $resp.data.image -Field "signedUrl"
    if ($signedUrl) {
        Set-YaakEnvironmentVariables $script:EnvironmentId @{ privateImageSignedUrl = $signedUrl }
        Write-Host "  [env] privateImageSignedUrl 已刷新为 conversationMember 签名"
    }
}

Write-Log "1.1 私聊成员 test_peer 下载图片 => 200" "Cyan"
Send-YaakRequest "01.03 私聊成员 test_peer 下载图片" @(200)

Write-Log "1.2 管理员下载私聊图片 => 200" "Cyan"
Send-YaakRequest "01.04 管理员下载私聊图片" @(200)

Write-Log "1.3 匿名下载私聊图片 => 401" "Cyan"
Send-YaakRequest "01.05 匿名下载私聊图片" @(401)

if ($merchantLoginOk) {
    Write-Log "1.4 非成员商家下载私聊图片 => 403" "Cyan"
    Send-YaakRequest "01.06 非成员商家下载私聊图片" @(403)
}
else {
    Write-Skip "商家未成功登录，跳过非成员下载私聊图片用例。"
}

# ============================================================================
# 02: 群聊图片鉴权
# ============================================================================
Write-Host "`n$('#' * 60)"
Write-Host "#  02 群聊图片鉴权"
Write-Host "$('#' * 60)"

Write-Log "正在创建群聊小队..." "Cyan"
$resp = Send-YaakRequestJson "02.01 创建群聊小队"
if ($resp.code -eq 200) {
    $teamId = Get-JsonField -Response $resp.data -Field "teamId"
    $chatId = Get-JsonField -Response $resp.data -Field "chatId"
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        teamId             = $teamId
        teamConversationId = $chatId
    }
    Write-Host "  [env] teamId=$teamId, teamConversationId=$chatId"
}

Write-Log "正在 test_peer 加入小队..." "Cyan"
Send-YaakRequest "02.02 test_peer 加入小队"

Write-Log "正在上传群聊图片..." "Cyan"
$resp = Send-YaakRequestJson "02.03 上传群聊图片"
if ($resp.code -eq 200) {
    $mediaId = Get-JsonField -Response $resp.data -Field "mediaId"
    $signedUrl = Get-JsonField -Response $resp.data -Field "url"
    if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        teamImageMediaId   = $mediaId
        teamImageSignedUrl = $signedUrl
    }
    Write-Host "  [env] teamImageMediaId=$mediaId"
}

Write-Log "正在发送群聊图片消息（触发策略升级为 conversationMember）..." "Cyan"
$resp = Send-YaakRequestJson "02.04 发送群聊图片消息"
if ($resp.code -eq 200 -and $resp.data.image) {
    $signedUrl = Get-JsonField -Response $resp.data.image -Field "signedUrl"
    if ($signedUrl) {
        Set-YaakEnvironmentVariables $script:EnvironmentId @{ teamImageSignedUrl = $signedUrl }
        Write-Host "  [env] teamImageSignedUrl 已刷新为 conversationMember 签名"
    }
}

Write-Log "2.1 群成员 test_peer 下载群聊图片 => 200" "Cyan"
Send-YaakRequest "02.05 群成员 test_peer 下载图片" @(200)

Write-Log "2.2 管理员下载群聊图片 => 200" "Cyan"
Send-YaakRequest "02.06 管理员下载群聊图片" @(200)

# 注册非成员用户并测试
Write-Log "正在注册非成员用户..." "Cyan"
Send-YaakRequest "02.07 注册非成员用户"

Write-Log "正在获取非成员用户激活 token..." "Cyan"
$outsiderToken = Get-MailHogToken -Recipient $OutsiderEmail -TokenPurpose "非成员用户激活"
Set-YaakEnvironmentVariables $script:EnvironmentId @{ outsiderActivationToken = $outsiderToken }

Send-YaakRequest "02.08 激活非成员用户"

Write-Log "正在登录非成员用户..." "Cyan"
$resp = Send-YaakRequestJson "02.09 登录非成员用户"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ outsiderAccessToken = $resp.data.tokens.accessToken }
}

Write-Log "2.3 非成员用户下载群聊图片 => 403" "Cyan"
Send-YaakRequest "02.10 非成员用户下载群聊图片" @(403)

# ============================================================================
# 03: 退出群聊后不可访问
# ============================================================================
Write-Host "`n$('#' * 60)"
Write-Host "#  03 退出群聊后不可访问"
Write-Host "$('#' * 60)"

Write-Log "test_peer 退出小队..." "Cyan"
Send-YaakRequest "03.01 test_peer 退出小队"

Write-Log "3.1 已退出的 test_peer 下载群聊图片 => 403" "Cyan"
Send-YaakRequest "03.02 已退出用户 test_peer 下载图片" @(403)

Write-Log "3.2 仍在群中 test_user 下载群聊图片 => 200" "Cyan"
Send-YaakRequest "03.03 仍在群中 test_user 下载图片" @(200)

Write-Log "3.3 管理员下载已退出群聊图片 => 200" "Cyan"
Send-YaakRequest "03.04 管理员下载已退出群聊图片" @(200)

# ============================================================================
# 04: 商家资质鉴权
# ============================================================================
Write-Host "`n$('#' * 60)"
Write-Host "#  04 商家资质鉴权"
Write-Host "$('#' * 60)"

if (-not $merchantLoginOk) {
    Write-Skip "商家未成功登录，跳过全部商家资质鉴权用例。"
}
else {
    Write-Log "商家上传执照..." "Cyan"
    $resp = Send-YaakRequestJson "04.01 商家上传执照"
    if ($resp.code -eq 200) {
        $mediaId = Get-JsonField -Response $resp.data -Field "mediaId"
        $signedUrl = Get-JsonField -Response $resp.data -Field "url"
        if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
        Set-YaakEnvironmentVariables $script:EnvironmentId @{
            licenseMediaId  = $mediaId
            licenseSignedUrl = $signedUrl
        }
        Write-Host "  [env] licenseMediaId=$mediaId"
    }

    Write-Log "4.1 商家本人下载执照 => 200" "Cyan"
    Send-YaakRequest "04.02 商家本人下载执照" @(200)

    Write-Log "4.2 管理员下载商家执照 => 200" "Cyan"
    Send-YaakRequest "04.03 管理员下载商家执照" @(200)

    Write-Log "4.3 test_user 非所有者下载商家执照 => 403" "Cyan"
    Send-YaakRequest "04.04 test_user 非所有者下载执照" @(403)

    Write-Log "4.4 匿名下载商家执照 => 401" "Cyan"
    Send-YaakRequest "04.05 匿名下载商家执照" @(401)
}

# ============================================================================
# 05: 管理员全资源访问
# ============================================================================
Write-Host "`n$('#' * 60)"
Write-Host "#  05 管理员全资源访问"
Write-Host "$('#' * 60)"

Write-Log "上传测试头像..." "Cyan"
$resp = Send-YaakRequestJson "05.01 上传测试头像"
if ($resp.code -eq 200) {
    $signedUrl = Get-JsonField -Response $resp.data -Field "url"
    if (-not $signedUrl) { $signedUrl = Get-JsonField -Response $resp.data -Field "signedUrl" }
    Set-YaakEnvironmentVariables $script:EnvironmentId @{ avatarSignedUrl = $signedUrl }
    Write-Host "  [env] avatarSignedUrl 已保存"
}

Write-Log "5.1 管理员下载私聊图片 => 200" "Cyan"
Send-YaakRequest "05.02 管理员下载私聊图片" @(200)

Write-Log "5.2 管理员下载群聊图片 => 200" "Cyan"
Send-YaakRequest "05.03 管理员下载群聊图片" @(200)

Write-Log "5.3 管理员下载商家执照 => 200" "Cyan"
Send-YaakRequest "05.04 管理员下载商家执照" @(200)

Write-Log "5.4 管理员下载公开头像 => 200" "Cyan"
Send-YaakRequest "05.05 管理员下载公开头像" @(200)

# ============================================================================
# 06: 签名完整性
# ============================================================================
Write-Host "`n$('#' * 60)"
Write-Host "#  06 签名完整性"
Write-Host "$('#' * 60)"

# 构造篡改签名和缺失签名的 URL
$envShow = Invoke-YaakString -Arguments @("environment", "show", $script:EnvironmentId)
$envObj = $envShow | ConvertFrom-Json
$privateUrlVar = $envObj.variables | Where-Object { $_.name -eq "privateImageSignedUrl" } | Select-Object -First 1
$signedUrlValue = if ($privateUrlVar) { $privateUrlVar.value } else { "" }

if ($signedUrlValue -and $signedUrlValue -match '&sig=([^&]+)') {
    $origSig = $Matches[1]
    $tamperedSig = if ($origSig.Length -gt 1) {
        $origSig.Substring(0, $origSig.Length - 1) + (([int][char]($origSig[-1]) + 1) % 10).ToString()
    }
    else { "invalid" }
    $tamperedUrl = $signedUrlValue -replace [regex]::Escape("&sig=$origSig"), "&sig=$tamperedSig"
    $missingSigUrl = $signedUrlValue -replace '&sig=[^&]+', ''

    Set-YaakEnvironmentVariables $script:EnvironmentId @{
        tamperedSignedUrl = $tamperedUrl
        missingSigUrl     = $missingSigUrl
    }
    Write-Host "  [env] 已构造篡改签名 URL 和无签名 URL"
}
else {
    Write-Skip "无法解析 signedUrl，跳过签名完整性测试。"
}

Write-Log "6.1 有效签名下载 => 200" "Cyan"
Send-YaakRequest "06.01 有效签名下载" @(200)

Write-Log "6.2 篡改签名下载 => 403" "Cyan"
Send-YaakRequest "06.02 篡改签名下载" @(403)

Write-Log "6.3 缺少签名参数下载 => 403" "Cyan"
Send-YaakRequest "06.03 缺少签名参数下载" @(403)

Write-Log "6.4 不存在媒体ID下载 => 404" "Cyan"
Send-YaakRequest "06.04 不存在媒体ID下载" @(404)

# ============================================================================
# 汇总
# ============================================================================
Write-TestSummary
