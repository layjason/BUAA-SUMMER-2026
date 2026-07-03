param(
    [string] $WorkspaceName = "MayoiStar AI",
    [string] $BaseUrl = "http://localhost:8080"
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
        if ($escaped) {
            $escaped = $false
            continue
        }
        if ($char -eq '\') {
            $escaped = $true
            continue
        }
        if ($char -eq '"') {
            $inString = -not $inString
            continue
        }
        if ($inString) { continue }
        if ($char -eq '{') { $depth++ }
        if ($char -eq '}') {
            $depth--
            if ($depth -eq 0) {
                return $normalized.Substring($start, $index - $start + 1)
            }
        }
    }
    return ""
}

function Get-HttpStatusCodeFromYaakOutput {
    param([Parameter(Mandatory = $true)] [string] $RawOutput)

    foreach ($line in ($RawOutput -split "\r?\n")) {
        if ($line -match '^< HTTP/[\d.]+\s+(?<status>\d{3})(?<reason>.*)$') {
            return @{
                Code = [int] $matches["status"]
                Line = "$($matches["status"])$($matches["reason"])".Trim()
            }
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

    Write-Host "$('─' * 60)"
    Write-Host "  ← $($httpStatus.Line)"
    Write-Host "  $respBody"

    # 解析响应并判断测试结果
    if ([string]::IsNullOrWhiteSpace($respBody)) {
        $resp = [pscustomobject]@{ code = $httpStatus.Code; message = ""; data = @{} }
    }
    else {
        $resp = $respBody | ConvertFrom-Json
    }
    $actualCode = $resp.code
    $passed = $ExpectedCodes -contains $actualCode

    if ($passed) {
        Write-Host "  " -NoNewline
        Write-Host "✓ PASS" -ForegroundColor Green -NoNewline
        Write-Host " (code=$actualCode)"
    }
    else {
        $expectedStr = $ExpectedCodes -join ','
        Write-Host "  " -NoNewline
        Write-Host "✗ FAIL" -ForegroundColor Red -NoNewline
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

# 发送请求（不需要提取响应字段的错误场景）
function Send-YaakRequest {
    param(
        [string] $Name,
        [int[]] $ExpectedCodes = @(200)
    )
    Send-YaakRequestJson -Name $Name -ExpectedCodes $ExpectedCodes | Out-Null
}

function Write-Skip {
    param([string] $Reason)

    Write-Host "  [skip] $Reason" -ForegroundColor Yellow
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

# ======== 开始测试 ========

$scriptDir = if ($MyInvocation.MyCommand.Path) { Split-Path -Parent $MyInvocation.MyCommand.Path } else { $PSScriptRoot }
$testImageFile = Join-Path $scriptDir "test-avatar.png"

Write-Host "`n$('#' * 60)"
Write-Host "#  MayoiStar AI 图片分类自动化测试"
Write-Host "$('#' * 60)"

$script:WorkspaceId = Get-YaakWorkspaceId -Name $WorkspaceName
$script:EnvironmentId = Get-YaakEnvironmentId -WorkspaceId $script:WorkspaceId

Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
    baseUrl = $BaseUrl
    testUserEmail = "test_user@mayoistar.qa"
    testUserPassword = "4g9Pf6KNpw4rxe3NL7hij9l2"
    testImageFile = $testImageFile
    nonexistentMediaId = "00000000-0000-0000-0000-000000000000"
    accessToken = ""
    testImageMediaId = ""
    testImageMediaId2 = ""
}

# ======== 00 登录 ========
Write-Host "`n$('#' * 60)"
Write-Host "#  00 登录"
Write-Host "$('#' * 60)"

$resp = Send-YaakRequestJson "个人用户登录 test_user"
$loginOk = $false
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        accessToken = [string] $resp.data.tokens.accessToken
    }
    $loginOk = $true
    Write-Host "  [env] accessToken 已保存"
}

if (-not $loginOk) {
    Write-Host "  登录失败，终止测试" -ForegroundColor Red
    Write-TestSummary
    exit 1
}

# ======== 01 图片上传 ========
Write-Host "`n$('#' * 60)"
Write-Host "#  01 图片上传"
Write-Host "$('#' * 60)"

$resp = Send-YaakRequestJson "上传活动图片 1"
$uploadOk = $false
if ($resp.code -eq 200) {
    Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
        testImageMediaId = [string] $resp.data.mediaId
    }
    $uploadOk = $true
    Write-Host "  [env] testImageMediaId 已保存"
}

if ($uploadOk) {
    $resp = Send-YaakRequestJson "上传活动图片 2"
    if ($resp.code -eq 200) {
        Set-YaakEnvironmentVariables -EnvironmentId $script:EnvironmentId -Variables @{
            testImageMediaId2 = [string] $resp.data.mediaId
        }
        Write-Host "  [env] testImageMediaId2 已保存"
    }
}
else {
    Write-Skip "图片上传失败，跳过后续分类用例。"
}

# ======== 02 图片分类 - 正常流程 ========
if ($uploadOk) {
    Write-Host "`n$('#' * 60)"
    Write-Host "#  02 图片分类 - 正常流程"
    Write-Host "$('#' * 60)"

    $resp = Send-YaakRequestJson "分类单张图片"
    if ($resp.code -eq 200 -and $resp.data.status -eq "succeeded") {
        $itemsCount = if ($resp.data.items) { @($resp.data.items).Count } else { 0 }
        Write-Host "  [info] status=$($resp.data.status), items=$itemsCount" -ForegroundColor Cyan
        if ($itemsCount -ge 1) {
            $firstTag = $resp.data.items[0].suggestedTags[0]
            $firstConf = $resp.data.items[0].confidence
            Write-Host "  [info] 第一张分类: tag=$firstTag, confidence=$firstConf" -ForegroundColor Cyan
        }
    }

    Send-YaakRequest "分类多张图片"
}
else {
    Write-Skip "testImageMediaId 未就绪，跳过正常流程分类用例。"
}

# ======== 03 图片分类 - 异常与边界 ========
Write-Host "`n$('#' * 60)"
Write-Host "#  03 图片分类 - 异常与边界"
Write-Host "$('#' * 60)"

Send-YaakRequest "空 mediaIds 列表"

# 不存在的 mediaId（CLIP 可用时返回 30003，不可用时返回 30001）
Send-YaakRequest "不存在的 mediaId" -ExpectedCodes @(30001, 30003)

# 未认证访问
Send-YaakRequest "未认证访问" -ExpectedCodes @(401)

# ======== 汇总 ========
Write-TestSummary

$passed = ($script:testResults | Where-Object { $_.Passed }).Count
if ($passed -eq $script:testResults.Count) { exit 0 } else { exit 1 }
