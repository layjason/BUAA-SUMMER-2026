param(
    [string] $WorkspaceName = "MayoiStar Identity and Merchant Qualification",
    [string] $MailHogApiBase = "http://127.0.0.1:8025",
    [string] $BaseUrl = "http://localhost:8080",
    [string] $Password = "Password123!",
    [int] $MailTimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"
$YaakExecutable = (Get-Command yaak -ErrorAction Stop).Source
$YaakNodeExecutable = $null
$YaakCliScript = $null
if ($YaakExecutable -like "*.ps1") {
    $yaakBaseDir = Split-Path $YaakExecutable -Parent
    $candidateNode = Join-Path $yaakBaseDir "node.exe"
    $YaakNodeExecutable = if (Test-Path $candidateNode) { $candidateNode } else { (Get-Command node -ErrorAction Stop).Source }
    $YaakCliScript = Join-Path $yaakBaseDir "node_modules\@yaakapp\cli\bin\cli.js"
}

$script:testResults = [System.Collections.ArrayList]::new()

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
    } else {
        $processInfo.FileName = $script:YaakExecutable
    }
    $processArguments += $Arguments
    $processInfo.UseShellExecute = $false
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    if ($null -ne $processInfo.ArgumentList) {
        foreach ($argument in $processArguments) { [void] $processInfo.ArgumentList.Add($argument) }
    } else {
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

    Write-Host "`n$('═' * 60)"
    Write-Host "  $Name"
    Write-Host "$('─' * 60)"
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
        } elseif ($req.body.form) {
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

    $inResponse = $false
    $statusLine = ""
    $respBodyLines = @()
    foreach ($line in ($rawOutput -split "\r?\n")) {
        if ($line -match '^< HTTP/[\d.]+ (.+)') {
            $statusLine = $matches[1].Trim()
            $inResponse = $true
            continue
        }
        if ($inResponse) {
            if ($line.StartsWith('< ') -or $line.StartsWith('* ')) { continue }
            if ($line.Trim() -ne '') { $respBodyLines += $line }
        }
    }

    Write-Host "$('─' * 60)"
    Write-Host "  ← $statusLine"
    $respBody = $respBodyLines -join "`n"
    Write-Host "  $respBody"

    # 解析响应并判断测试结果
    $resp = $respBody | ConvertFrom-Json
    $actualCode = $resp.code
    $passed = $ExpectedCodes -contains $actualCode

    if ($passed) {
        Write-Host "  " -NoNewline
        Write-Host "✓ PASS" -ForegroundColor Green -NoNewline
        Write-Host " (code=$actualCode)"
    } else {
        $expectedStr = $ExpectedCodes -join ','
        Write-Host "  " -NoNewline
        Write-Host "✗ FAIL" -ForegroundColor Red -NoNewline
        Write-Host " (expected=$expectedStr, actual=$actualCode, message=$($resp.message))"
    }

    [void] $script:testResults.Add(@{
        Name = $Name
        Expected = $ExpectedCodes
        Actual = $actualCode
        Passed = $passed
    })

    return $resp
}

# 发送请求（不需要提取响应字段的错误场景）
function Send-YaakRequest {
    param(
        [string] $Name,
        [int[]] $ExpectedCodes = @(200)
    )
    Send-YaakRequestJson -Name $Name -ExpectedCodes $ExpectedCodes | Out-Null
}

# 从 MailHog 获取邮件中的 token
function Get-MailHogToken {
    param([string] $Recipient, [string] $TokenPurpose)

    $deadline = (Get-Date).AddSeconds($MailTimeoutSeconds)
    do {
        $response = Invoke-RestMethod -Uri "$MailHogApiBase/api/v2/messages" -Method Get
        $items = if ($response.items) { @($response.items) } elseif ($response.Items) { @($response.Items) } else { @() }
        foreach ($message in $items) {
            $body = [string] $message.Content.Body
            $headers = $message.Content.Headers
            $toHeader = if ($headers.To) { $headers.To -join "," } else { "" }
            if ($toHeader -notmatch [regex]::Escape($Recipient) -and $body -notmatch [regex]::Escape($Recipient)) { continue }
            $match = [regex]::Match($body, 'token=([^\"&<>\s]+)')
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

# 打印测试汇总
function Write-TestSummary {
    $passed = $script:testResults | Where-Object { $_.Passed }
    $failed = $script:testResults | Where-Object { -not $_.Passed }

    Write-Host "`n$('#' * 60)"
    Write-Host "#  测试结果汇总"
    Write-Host "$('#' * 60)"
    Write-Host ""
    Write-Host "  通过: $($passed.Count) / $($script:testResults.Count)" -ForegroundColor Green
    if ($failed.Count -gt 0) {
        Write-Host "  失败: $($failed.Count)" -ForegroundColor Red
        Write-Host ""
        Write-Host "  失败的测试:"
        foreach ($f in $failed) {
            Write-Host "    " -NoNewline
            Write-Host "✗ $($f.Name)" -ForegroundColor Red -NoNewline
            Write-Host " (expected=$($f.Expected -join '|'), actual=$($f.Actual))"
        }
    }
    if ($passed.Count -gt 0 -and $passed.Count -eq $script:testResults.Count) {
        Write-Host "  全部通过!" -ForegroundColor Green
    }
}

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$personalEmail = "yaak-p.$stamp@example.com"
$merchantEmail = "yaak-m.$stamp@example.com"
$personalNickname = "yaak-p-$stamp"
$merchantNickname = "yaak-m-$stamp"

$script:WorkspaceId = Get-YaakWorkspaceId -Name $WorkspaceName
$script:EnvironmentId = Get-YaakEnvironmentId -WorkspaceId $script:WorkspaceId

$scriptDir = if ($MyInvocation.MyCommand.Path) { Split-Path -Parent $MyInvocation.MyCommand.Path } else { $PSScriptRoot }

Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
    baseUrl = $BaseUrl; personalEmail = $personalEmail; personalPassword = $Password
    personalNewPassword = "Password456!"; personalNickname = $personalNickname
    personalNewNickname = "${personalNickname}-new"; merchantEmail = $merchantEmail
    merchantPassword = $Password; merchantNickname = $merchantNickname
    merchantNewNickname = "${merchantNickname}-new"; merchantName = "MayoiStar Test Merchant"
    adminUsername = "testadminyaak"; adminPassword = "AdminPass123!"
    avatarFile = (Join-Path $scriptDir "test-avatar.png")
    licenseFile = (Join-Path $scriptDir "test-license.png")
    activationToken = ""; merchantActivationToken = ""; resetToken = ""
    avatarMediaId = ""; licenseMediaId = ""
    personalAccessToken = ""; personalRefreshToken = ""; personalUserId = ""
    merchantAccessToken = ""; merchantRefreshToken = ""; merchantUserId = ""
    adminAccessToken = ""; adminRefreshToken = ""; adminUserId = ""
}

Write-Host "`n$('#' * 60)"
Write-Host "#  00 公共接口"
Write-Host "$('#' * 60)"

Send-YaakRequest "00.01 Get interest tags"
Send-YaakRequest "00.02 Check nickname available"

Write-Host "`n$('#' * 60)"
Write-Host "#  01 个人用户认证与资料"
Write-Host "$('#' * 60)"

Send-YaakRequest "01.01 Register personal"
Send-YaakRequest "01.02 Login personal before activation should fail" -ExpectedCodes @(10004)

$activationToken = Get-MailHogToken -Recipient $personalEmail -TokenPurpose "personal activation"
Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ activationToken = $activationToken }
Send-YaakRequest "01.03 Activate personal account"

$resp = Send-YaakRequestJson "01.04 Login personal"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        personalAccessToken = $resp.data.tokens.accessToken
        personalRefreshToken = $resp.data.tokens.refreshToken
        personalUserId = [string] $resp.data.userId
    }
    Write-Host "  [env] personalAccessToken 已保存"
}

Send-YaakRequest "01.05 Duplicate personal email should fail" -ExpectedCodes @(10001)
Send-YaakRequest "01.06 Profile without token should fail" -ExpectedCodes @(401)
Send-YaakRequest "01.07 Get personal profile"
Send-YaakRequest "01.08 Update personal profile"

$resp = Send-YaakRequestJson "01.09 Upload avatar"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ avatarMediaId = $resp.data.mediaId }
    Write-Host "  [env] avatarMediaId 已保存"
    Send-YaakRequest "01.10 Attach avatar to profile"
}

$resp = Send-YaakRequestJson "01.11 Refresh personal token"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        personalAccessToken = $resp.data.accessToken
        personalRefreshToken = $resp.data.refreshToken
    }
    Write-Host "  [env] tokens 已刷新"
}

Send-YaakRequest "01.12 Change password with wrong old password" -ExpectedCodes @(10016)
Send-YaakRequest "01.13 Logout personal"
Send-YaakRequest "01.14 Refresh after logout should fail" -ExpectedCodes @(10007)

Write-Host "`n$('#' * 60)"
Write-Host "#  02 商家用户认证与资质"
Write-Host "$('#' * 60)"

Send-YaakRequest "02.01 Register merchant"
$merchantActivationToken = Get-MailHogToken -Recipient $merchantEmail -TokenPurpose "merchant activation"
Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ merchantActivationToken = $merchantActivationToken }
Send-YaakRequest "02.02 Activate merchant account"

$resp = Send-YaakRequestJson "02.03 Login merchant"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        merchantAccessToken = $resp.data.tokens.accessToken
        merchantRefreshToken = $resp.data.tokens.refreshToken
        merchantUserId = [string] $resp.data.userId
    }
    Write-Host "  [env] merchantAccessToken 已保存"
}

Send-YaakRequest "02.04 Get merchant profile"
Send-YaakRequest "02.05 Update merchant profile"

$resp = Send-YaakRequestJson "02.06 Upload merchant license"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ licenseMediaId = $resp.data.mediaId }
    Write-Host "  [env] licenseMediaId 已保存"
    Send-YaakRequest "02.07 Submit merchant qualification"
    Send-YaakRequest "02.08 Get merchant profile after qualification"
}

Send-YaakRequest "02.09 Personal token cannot access merchant profile" -ExpectedCodes @(10008)

Write-Host "`n$('#' * 60)"
Write-Host "#  03 密码重置与安全"
Write-Host "$('#' * 60)"

Send-YaakRequest "03.01 Resend activation email" -ExpectedCodes @(200, 10012)
Send-YaakRequest "03.02 Send password reset email"

$resetToken = Get-MailHogToken -Recipient $personalEmail -TokenPurpose "password reset"
Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ resetToken = $resetToken }
Send-YaakRequest "03.03 Reset password with token"
Send-YaakRequest "03.04 Wrong password attempt" -ExpectedCodes @(10003)

Write-Host "`n$('#' * 60)"
Write-Host "#  04 管理员操作"
Write-Host "$('#' * 60)"

$resp = Send-YaakRequestJson "04.01 Admin login"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ adminAccessToken = $resp.data.tokens.accessToken }
    Write-Host "  [env] adminAccessToken 已保存"
    Send-YaakRequest "04.02 Admin get merchant profile placeholder"
    Send-YaakRequest "04.03 Admin review merchant placeholder"
} else {
    Write-Host "  ⚠ 管理员登录失败，请确认 V2 迁移已执行。" -ForegroundColor Yellow
}

Write-TestSummary
