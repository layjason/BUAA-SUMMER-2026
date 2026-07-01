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

function ConvertTo-WindowsProcessArgument {
    param([string] $Argument)

    if ($null -eq $Argument) {
        return '""'
    }
    if ($Argument -notmatch '[\s"]') {
        return $Argument
    }

    $builder = [System.Text.StringBuilder]::new()
    [void] $builder.Append('"')
    $backslashCount = 0
    foreach ($char in $Argument.ToCharArray()) {
        if ($char -eq '\') {
            $backslashCount++
            continue
        }
        if ($char -eq '"') {
            [void] $builder.Append('\' * (($backslashCount * 2) + 1))
            [void] $builder.Append('"')
            $backslashCount = 0
            continue
        }
        if ($backslashCount -gt 0) {
            [void] $builder.Append('\' * $backslashCount)
            $backslashCount = 0
        }
        [void] $builder.Append($char)
    }
    if ($backslashCount -gt 0) {
        [void] $builder.Append('\' * ($backslashCount * 2))
    }
    [void] $builder.Append('"')
    return $builder.ToString()
}

function Invoke-YaakLines {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

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
        foreach ($argument in $processArguments) {
            [void] $processInfo.ArgumentList.Add($argument)
        }
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
    if ([string]::IsNullOrWhiteSpace($stdout)) {
        return @()
    }
    return $stdout -split "\r?\n" | Where-Object { $_ -ne "" }
}

function Get-YaakIdByName {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Lines,
        [Parameter(Mandatory = $true)]
        [string] $Name
    )

    $escaped = [regex]::Escape($Name)
    $line = $Lines | Where-Object { $_ -match "^(?<id>\S+)\s+-\s+.*$escaped$" } | Select-Object -First 1
    if (-not $line) {
        throw "Cannot find Yaak item named '$Name'."
    }
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
    if (-not $line) {
        throw "No Yaak environment found in workspace $WorkspaceId."
    }
    return ([regex]::Match($line, "^(?<id>\S+)")).Groups["id"].Value
}

function Get-YaakRequestId {
    param(
        [string] $WorkspaceId,
        [string] $Name
    )

    $requests = Invoke-YaakLines -Arguments @("request", "list", $WorkspaceId)
    return Get-YaakIdByName -Lines $requests -Name $Name
}

function Set-YaakEnvironmentVariables {
    param(
        [string] $EnvironmentId,
        [hashtable] $Variables
    )

    $environmentJson = Invoke-YaakLines -Arguments @("environment", "show", $EnvironmentId) | Out-String
    $environment = $environmentJson | ConvertFrom-Json
    $merged = @{}
    foreach ($variable in $environment.variables) {
        $merged[$variable.name] = [string] $variable.value
    }
    foreach ($key in $Variables.Keys) {
        $merged[$key] = [string] $Variables[$key]
    }

    $updatedVariables = @()
    foreach ($key in ($merged.Keys | Sort-Object)) {
        $updatedVariables += @{
            name = $key
            value = $merged[$key]
            enabled = $true
        }
    }

    $payload = @{
        id = $environment.id
        workspaceId = $environment.workspaceId
        name = $environment.name
        variables = $updatedVariables
    } | ConvertTo-Json -Depth 8 -Compress

    Invoke-YaakLines -Arguments @("environment", "update", "--json=$payload") | Out-Null
}

# 发送请求并返回响应 JSON 对象，供后续 token 提取使用
function Send-YaakRequestJson {
    param([string] $Name)

    $requestId = Get-YaakRequestId -WorkspaceId $script:WorkspaceId -Name $Name
    Write-Host "Sending: $Name"
    $output = Invoke-YaakLines -Arguments @("request", "send", $requestId, "-e", $script:EnvironmentId)
    return ($output -join "") | ConvertFrom-Json
}

# 发送请求，不解析响应（用于不需要提取 token 的错误场景）
function Send-YaakRequest {
    param([string] $Name)

    Send-YaakRequestJson -Name $Name | Out-Null
}

# 从 MailHog 获取邮件中的 token
function Get-MailHogToken {
    param(
        [string] $Recipient,
        [string] $TokenPurpose
    )

    $deadline = (Get-Date).AddSeconds($MailTimeoutSeconds)
    do {
        $response = Invoke-RestMethod -Uri "$MailHogApiBase/api/v2/messages" -Method Get
        $items = @()
        if ($response.items) {
            $items = @($response.items)
        } elseif ($response.Items) {
            $items = @($response.Items)
        }

        foreach ($message in $items) {
            $body = [string] $message.Content.Body
            $headers = $message.Content.Headers
            $toHeader = ""
            if ($headers.To) {
                $toHeader = ($headers.To -join ",")
            }
            if ($toHeader -notmatch [regex]::Escape($Recipient) -and $body -notmatch [regex]::Escape($Recipient)) {
                continue
            }
            $match = [regex]::Match($body, 'token=([^\"&<>\s]+)')
            if ($match.Success) {
                return [System.Uri]::UnescapeDataString($match.Groups[1].Value)
            }
        }

        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    throw "Cannot find $TokenPurpose token email for $Recipient in MailHog."
}

# 获取管理请求 ID 的辅助函数（仅获取，不发送）
function Get-RequestId {
    param([string] $Name)
    return Get-YaakRequestId -WorkspaceId $script:WorkspaceId -Name $Name
}

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$personalEmail = "yaak-p.$stamp@example.com"
$merchantEmail = "yaak-m.$stamp@example.com"
$personalNickname = "yaak-p-$stamp"
$merchantNickname = "yaak-m-$stamp"

$script:WorkspaceId = Get-YaakWorkspaceId -Name $WorkspaceName
$script:WorkspaceId = $script:WorkspaceId
$script:EnvironmentId = Get-YaakEnvironmentId -WorkspaceId $script:WorkspaceId

# 获取脚本所在目录，用于文件上传路径
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $scriptDir) {
    $scriptDir = $PSScriptRoot
}

Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
    baseUrl = $BaseUrl
    personalEmail = $personalEmail
    personalPassword = $Password
    personalNewPassword = "Password456!"
    personalNickname = $personalNickname
    personalNewNickname = "${personalNickname}-new"
    merchantEmail = $merchantEmail
    merchantPassword = $Password
    merchantNickname = $merchantNickname
    merchantNewNickname = "${merchantNickname}-new"
    merchantName = "MayoiStar Test Merchant"
    adminUsername = "testadminyaak"
    adminPassword = "AdminPass123!"
    avatarFile = (Join-Path $scriptDir "test-avatar.png")
    licenseFile = (Join-Path $scriptDir "test-license.png")
    activationToken = ""
    merchantActivationToken = ""
    resetToken = ""
    avatarMediaId = ""
    licenseMediaId = ""
    personalAccessToken = ""
    personalRefreshToken = ""
    personalUserId = ""
    merchantAccessToken = ""
    merchantRefreshToken = ""
    merchantUserId = ""
    adminAccessToken = ""
    adminRefreshToken = ""
    adminUserId = ""
}

Write-Host "========== 00 公共接口 =========="
Send-YaakRequest "00.01 Get interest tags"
Send-YaakRequest "00.02 Check nickname available"

Write-Host "`n========== 01 个人用户认证与资料 =========="
Send-YaakRequest "01.01 Register personal"

# 激活前尝试登录，应失败
Send-YaakRequest "01.02 Login personal before activation should fail"

$activationToken = Get-MailHogToken -Recipient $personalEmail -TokenPurpose "personal activation"
Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ activationToken = $activationToken }
Send-YaakRequest "01.03 Activate personal account"

# 登录并提取 token
$resp = Send-YaakRequestJson "01.04 Login personal"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        personalAccessToken = $resp.data.tokens.accessToken
        personalRefreshToken = $resp.data.tokens.refreshToken
        personalUserId = [string] $resp.data.userId
    }
    Write-Host "  -> personalAccessToken saved"
}

# 重复注册应失败
Send-YaakRequest "01.05 Duplicate personal email should fail"

# 无 Token 访问应失败
Send-YaakRequest "01.06 Profile without token should fail"

Send-YaakRequest "01.07 Get personal profile"
Send-YaakRequest "01.08 Update personal profile"

# 上传头像并提取 mediaId
$resp = Send-YaakRequestJson "01.09 Upload avatar"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        avatarMediaId = $resp.data.mediaId
    }
    Write-Host "  -> avatarMediaId saved"

    Send-YaakRequest "01.10 Attach avatar to profile"
}

# 刷新 token 并更新
$resp = Send-YaakRequestJson "01.11 Refresh personal token"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        personalAccessToken = $resp.data.accessToken
        personalRefreshToken = $resp.data.refreshToken
    }
    Write-Host "  -> tokens refreshed"
}

Send-YaakRequest "01.12 Change password with wrong old password"
Send-YaakRequest "01.13 Logout personal"

# 登出后刷新应失败
Send-YaakRequest "01.14 Refresh after logout should fail"

Write-Host "`n========== 02 商家用户认证与资质 =========="
Send-YaakRequest "02.01 Register merchant"

$merchantActivationToken = Get-MailHogToken -Recipient $merchantEmail -TokenPurpose "merchant activation"
Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ merchantActivationToken = $merchantActivationToken }
Send-YaakRequest "02.02 Activate merchant account"

# 登录并提取商家 token
$resp = Send-YaakRequestJson "02.03 Login merchant"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        merchantAccessToken = $resp.data.tokens.accessToken
        merchantRefreshToken = $resp.data.tokens.refreshToken
        merchantUserId = [string] $resp.data.userId
    }
    Write-Host "  -> merchantAccessToken saved"
}

Send-YaakRequest "02.04 Get merchant profile"
Send-YaakRequest "02.05 Update merchant profile"

# 上传执照并提取 mediaId
$resp = Send-YaakRequestJson "02.06 Upload merchant license"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        licenseMediaId = $resp.data.mediaId
    }
    Write-Host "  -> licenseMediaId saved"

    Send-YaakRequest "02.07 Submit merchant qualification"
    Send-YaakRequest "02.08 Get merchant profile after qualification"
}

# 个人 token 访问商家资料应被拒
Send-YaakRequest "02.09 Personal token cannot access merchant profile"

Write-Host "`n========== 03 密码重置与安全 =========="
Send-YaakRequest "03.01 Resend activation email"
Send-YaakRequest "03.02 Send password reset email"

$resetToken = Get-MailHogToken -Recipient $personalEmail -TokenPurpose "password reset"
Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{ resetToken = $resetToken }
Write-Host "  -> resetToken saved"

Send-YaakRequest "03.03 Reset password with token"
Send-YaakRequest "03.04 Wrong password attempt"

Write-Host "`n========== 04 管理员操作 =========="
# 管理员登录并提取 token
$resp = Send-YaakRequestJson "04.01 Admin login"
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        adminAccessToken = $resp.data.tokens.accessToken
    }
    Write-Host "  -> adminAccessToken saved"

    Send-YaakRequest "04.02 Admin get merchant profile placeholder"
    Send-YaakRequest "04.03 Admin review merchant placeholder"
}
else {
    Write-Host "  WARNING: Admin login failed (code=$($resp.code)). 请确认 DevDataInitializer 已创建管理员。"
}

Write-Host "`n========== 全量测试完成 =========="
