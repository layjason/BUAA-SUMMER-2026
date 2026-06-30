param(
    [string] $WorkspaceName = "MayoiStar Identity and Merchant Qualification",
    [string] $MailHogApiBase = "http://127.0.0.1:8025",
    [string] $BaseUrl = "http://localhost:8080",
    [string] $Password = "Password123!",
    [int] $MailTimeoutSeconds = 30,
    [switch] $SkipMerchant
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

function Send-YaakRequest {
    param(
        [string] $WorkspaceId,
        [string] $EnvironmentId,
        [string] $Name
    )

    $requestId = Get-YaakRequestId -WorkspaceId $WorkspaceId -Name $Name
    Write-Host "Sending: $Name"
    Invoke-YaakLines -Arguments @("request", "send", $requestId, "-e", $EnvironmentId) | Out-Null
}

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
            $match = [regex]::Match($body, "token=([^`"'&<>\s]+)")
            if ($match.Success) {
                return [System.Uri]::UnescapeDataString($match.Groups[1].Value)
            }
        }

        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    throw "Cannot find $TokenPurpose token email for $Recipient in MailHog."
}

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$personalEmail = "personal.yaak.$stamp@example.com"
$merchantEmail = "merchant.yaak.$stamp@example.com"
$personalNickname = "yaak-personal-$stamp"
$merchantNickname = "yaak-merchant-$stamp"

$workspaceId = Get-YaakWorkspaceId -Name $WorkspaceName
$environmentId = Get-YaakEnvironmentId -WorkspaceId $workspaceId

Set-YaakEnvironmentVariables -EnvironmentId $environmentId -Variables @{
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
}

Send-YaakRequest -WorkspaceId $workspaceId -EnvironmentId $environmentId -Name "00.01 Get interest tags"

Send-YaakRequest -WorkspaceId $workspaceId -EnvironmentId $environmentId -Name "01.01 Register personal"
$activationToken = Get-MailHogToken -Recipient $personalEmail -TokenPurpose "personal activation"
Set-YaakEnvironmentVariables -EnvironmentId $environmentId -Variables @{ activationToken = $activationToken }
Send-YaakRequest -WorkspaceId $workspaceId -EnvironmentId $environmentId -Name "01.03 Activate personal account"
Send-YaakRequest -WorkspaceId $workspaceId -EnvironmentId $environmentId -Name "01.04 Login personal"

if (-not $SkipMerchant) {
    Send-YaakRequest -WorkspaceId $workspaceId -EnvironmentId $environmentId -Name "02.01 Register merchant"
    $merchantActivationToken = Get-MailHogToken -Recipient $merchantEmail -TokenPurpose "merchant activation"
    Set-YaakEnvironmentVariables -EnvironmentId $environmentId -Variables @{ merchantActivationToken = $merchantActivationToken }
    Send-YaakRequest -WorkspaceId $workspaceId -EnvironmentId $environmentId -Name "02.02 Activate merchant account"
    Send-YaakRequest -WorkspaceId $workspaceId -EnvironmentId $environmentId -Name "02.03 Login merchant"
}

Write-Host "MailHog + Yaak smoke flow completed."
