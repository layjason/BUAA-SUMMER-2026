param(
    [string] $WorkspaceName = "MayoiStar AI",
    [string] $BaseUrl = "http://localhost:8080",
    [string] $TestImageFile = (Join-Path $PSScriptRoot "test-avatar.png")
)

$ErrorActionPreference = "Stop"

# 定义带时间的日志输出函数
function Write-Log ($Message, $Color = "Cyan") {
    $Timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$Timestamp] $Message" -ForegroundColor $Color
}

# 测试结果记录
$Script:TestResults = @()

# 依赖检查
if (-not (Get-Command "yaak" -ErrorAction SilentlyContinue)) {
    Write-Log "Error: yaak CLI not found." "Red"
    exit 1
}
if (-not (Get-Command "python3" -ErrorAction SilentlyContinue) -and -not (Get-Command "python" -ErrorAction SilentlyContinue)) {
    Write-Log "Error: python3 or python is required but not found." "Red"
    exit 1
}
$PythonCmd = if (Get-Command "python3" -ErrorAction SilentlyContinue) { "python3" } else { "python" }

if (-not (Test-Path $TestImageFile)) {
    Write-Log "Error: test image not found: $TestImageFile" "Red"
    exit 1
}

# 调用 yaak CLI，返回输出行（去除空行）
function Invoke-YaakLines {
    param([string[]] $Arguments)
    $output = & yaak @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Log "yaak $Arguments failed with exit code $LASTEXITCODE." "Red"
        Write-Host $output
        exit $LASTEXITCODE
    }
    $output -split "`n" | Where-Object { $_.Trim() -ne "" }
}

function Invoke-YaakString {
    param([string[]] $Arguments)
    (Invoke-YaakLines @Arguments) -join "`n"
}

# 从前置条件：传入 yaak verbose 输出；后置条件：提取 JSON 响应体
function Get-ResponseJsonFromYaakOutput {
    param([string] $RawOutput)
    $script = @"
import sys, re
text = sys.stdin.read()
text = re.sub(r'\x1b\[[0-9;]*m', '', text)
lines = text.split('\n')
normalized = []
for line in lines:
    trimmed = line.strip()
    if trimmed.startswith('* '): continue
    if trimmed.startswith('< '):
        trimmed = trimmed[2:].strip()
    normalized.append(trimmed)
text = '\n'.join(normalized)
start = text.rfind('{"code"')
if start < 0:
    sys.stdout.write('')
    sys.exit(0)
depth = 0
in_string = False
escaped = False
for i in range(start, len(text)):
    c = text[i]
    if escaped:
        escaped = False
        continue
    if c == '\\\\':
        escaped = True
        continue
    if c == '"':
        in_string = not in_string
        continue
    if in_string: continue
    if c == '{': depth += 1
    if c == '}':
        depth -= 1
        if depth == 0:
            sys.stdout.write(text[start:i+1])
            sys.exit(0)
sys.stdout.write('')
"@
    $RawOutput | & $PythonCmd -c $script
}

# 从前置条件：传入 yaak verbose 输出；后置条件：提取 HTTP 状态码
function Get-HttpStatusCodeFromYaakOutput {
    param([string] $RawOutput)
    foreach ($line in ($RawOutput -split "`n")) {
        if ($line -match '^<\s*HTTP/[\d.]+\s+(\d{3})') {
            return $Matches[1]
        }
    }
    return $null
}

# 按名称查找 Yaak 条目 ID
function Get-YaakIdByName {
    param([string] $Lines, [string] $Name)
    $line = $Lines -split "`n" | Where-Object { $_ -match "^\S+\s+-\s+.*$([regex]::Escape($Name))$" } | Select-Object -First 1
    if (-not $line) {
        Write-Log "Cannot find Yaak item named '$Name'." "Red"
        exit 1
    }
    ($line -split "\s+")[0]
}

function Get-YaakWorkspaceId {
    param([string] $Name)
    $workspaces = Invoke-YaakLines "workspace", "list"
    Get-YaakIdByName ($workspaces -join "`n") $Name
}

function Get-YaakEnvironmentId {
    param([string] $WorkspaceId)
    $environments = Invoke-YaakLines "environment", "list", $WorkspaceId
    $line = ($environments -join "`n") -split "`n" | Select-Object -First 1
    if (-not $line) {
        Write-Log "No Yaak environment found in workspace $WorkspaceId." "Red"
        exit 1
    }
    ($line -split "\s+")[0]
}

function Get-YaakRequestId {
    param([string] $WorkspaceId, [string] $Name)
    $requests = Invoke-YaakLines "request", "list", $WorkspaceId
    Get-YaakIdByName ($requests -join "`n") $Name
}

function Set-YaakEnvironmentVariables {
    param([string] $EnvironmentId, [hashtable] $Variables)
    $envJson = Invoke-YaakString "environment", "show", $EnvironmentId
    $env = $envJson | ConvertFrom-Json

    foreach ($entry in $Variables.GetEnumerator()) {
        $existing = $env.variables | Where-Object { $_.name -eq $entry.Key }
        if ($existing) {
            $existing.value = $entry.Value
        }
    }

    $payload = @{
        id          = $env.id
        workspaceId = $env.workspaceId
        name        = $env.name
        variables   = @($env.variables | ForEach-Object {
            @{ name = $_.name; value = if ($_.value) { "$($_.value)" } else { "" }; enabled = $true }
        } | Sort-Object { $_.name })
    }
    $payloadJson = $payload | ConvertTo-Json -Depth 5 -Compress
    Invoke-YaakLines "environment", "update", "--json=$payloadJson" | Out-Null
}

# 记录测试结果
function Record-Result {
    param([string] $Name, [string] $Expected, [string] $Actual)
    $expectedArr = $Expected -split ','
    $passed = $Actual -in $expectedArr
    $Script:TestResults += @{
        Name     = $Name
        Expected = $Expected
        Actual   = $Actual
        Passed   = $passed
    }
}

# 发送请求，显示详情，比对预期 code，返回响应 JSON
function Send-YaakRequestJson {
    param([string] $Name, [string] $Expected = "200")

    $requestId = Get-YaakRequestId $Script:WorkspaceId $Name
    $showJson = Invoke-YaakString "request", "show", $requestId
    $showObj = $showJson | ConvertFrom-Json

    $method = $showObj.method
    $url = $showObj.url
    $bodyText = $showObj.body.text
    $bodyType = $showObj.bodyType

    Write-Host ""
    Write-Host ("═" * 60)
    Write-Host "  $Name"
    Write-Host ("─" * 60)
    Write-Host "  $method $url"

    foreach ($h in ($showObj.headers | Where-Object { $_.enabled })) {
        Write-Host "  > $($h.name): $($h.value)"
    }

    if ($bodyText) {
        Write-Host "  Body:"
        Write-Host "  $bodyText"
    }
    elseif ($bodyType -eq "multipart/form-data") {
        Write-Host "  Body (form):"
        foreach ($f in ($showObj.body.form | Where-Object { $_.enabled })) {
            $val = if ($f.file) { "[file: $($f.file)]" } else { $f.value }
            Write-Host "    $($f.name) = $val"
        }
    }
    else {
        Write-Host "  Body: (none)"
    }

    $rawOutput = Invoke-YaakString "request", "send", $requestId, "-e", $Script:EnvironmentId, "-v"
    $statusCode = Get-HttpStatusCodeFromYaakOutput $rawOutput
    $respBody = Get-ResponseJsonFromYaakOutput $rawOutput

    Write-Host ("─" * 60)
    Write-Host "  ← HTTP $statusCode"
    Write-Host "  $respBody"

    if (-not $respBody) {
        $resp = @{ code = $statusCode; message = ""; data = @{} } | ConvertTo-Json
    }
    else {
        $resp = $respBody
    }
    $respObj = $resp | ConvertFrom-Json
    $actualCode = "$($respObj.code)"

    Record-Result $Name $Expected $actualCode

    $expectedArr = $Expected -split ','
    $passed = $actualCode -in $expectedArr

    if ($passed) {
        Write-Host "  ✓ PASS (code=$actualCode)" -ForegroundColor Green
    }
    else {
        Write-Host "  ✗ FAIL (expected=$Expected, actual=$actualCode, message=$($respObj.message))" -ForegroundColor Red
    }

    return $resp
}

# 输出跳过信息
function Write-Skip {
    param([string] $Reason)
    Write-Host "  [skip] $Reason" -ForegroundColor Yellow
}

# 打印测试汇总
function Write-Summary {
    $total = $Script:TestResults.Count
    $passed = ($Script:TestResults | Where-Object { $_.Passed }).Count
    $failed = $total - $passed

    Write-Host ""
    Write-Host ("#" * 60)
    Write-Host "#  测试结果汇总"
    Write-Host ("#" * 60)
    Write-Host ""
    Write-Host "  通过: $passed / $total" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Yellow" })
    if ($failed -gt 0) {
        Write-Host "  失败: $failed" -ForegroundColor Red
        Write-Host ""
        Write-Host "  失败的测试:"
        foreach ($r in ($Script:TestResults | Where-Object { -not $_.Passed })) {
            Write-Host "    ✗ $($r.Name) (expected=$($r.Expected), actual=$($r.Actual))" -ForegroundColor Red
        }
    }
    if ($passed -eq $total) {
        Write-Host "  全部通过!" -ForegroundColor Green
    }
}

# ======== 开始测试 ========

Write-Host ""
Write-Host ("#" * 60)
Write-Host "#  MayoiStar AI 图片分类自动化测试"
Write-Host ("#" * 60)

$Script:WorkspaceId = Get-YaakWorkspaceId $WorkspaceName
$Script:EnvironmentId = Get-YaakEnvironmentId $Script:WorkspaceId

$envVars = @{
    baseUrl             = $BaseUrl
    testUserEmail       = "test_user@mayoistar.qa"
    testUserPassword    = "4g9Pf6KNpw4rxe3NL7hij9l2"
    testImageFile       = $TestImageFile
    nonexistentMediaId  = "00000000-0000-0000-0000-000000000000"
    accessToken         = ""
    testImageMediaId    = ""
    testImageMediaId2   = ""
}
Set-YaakEnvironmentVariables $Script:EnvironmentId $envVars

# ======== 00 登录 ========
Write-Host ""
Write-Host ("#" * 60)
Write-Host "#  00 登录"
Write-Host ("#" * 60)

$resp = Send-YaakRequestJson "个人用户登录 test_user"
$respObj = $resp | ConvertFrom-Json
$loginOk = $false
if ($respObj.code -eq 200) {
    $vars = @{ accessToken = "$($respObj.data.tokens.accessToken)" }
    Set-YaakEnvironmentVariables $Script:EnvironmentId $vars
    $loginOk = $true
    Write-Host "  [env] accessToken 已保存" -ForegroundColor Cyan
}

if (-not $loginOk) {
    Write-Log "登录失败，终止测试" "Red"
    Write-Summary
    exit 1
}

# ======== 01 图片上传 ========
Write-Host ""
Write-Host ("#" * 60)
Write-Host "#  01 图片上传"
Write-Host ("#" * 60)

$resp = Send-YaakRequestJson "上传活动图片 1"
$respObj = $resp | ConvertFrom-Json
$uploadOk = $false
if ($respObj.code -eq 200) {
    $vars = @{ testImageMediaId = "$($respObj.data.mediaId)" }
    Set-YaakEnvironmentVariables $Script:EnvironmentId $vars
    $uploadOk = $true
    Write-Host "  [env] testImageMediaId 已保存" -ForegroundColor Cyan
}

if ($uploadOk) {
    $resp = Send-YaakRequestJson "上传活动图片 2"
    $respObj = $resp | ConvertFrom-Json
    if ($respObj.code -eq 200) {
        $vars = @{ testImageMediaId2 = "$($respObj.data.mediaId)" }
        Set-YaakEnvironmentVariables $Script:EnvironmentId $vars
        Write-Host "  [env] testImageMediaId2 已保存" -ForegroundColor Cyan
    }
}
else {
    Write-Skip "图片上传失败，跳过后续分类用例。"
}

# ======== 02 图片分类 - 正常流程 ========
if ($uploadOk) {
    Write-Host ""
    Write-Host ("#" * 60)
    Write-Host "#  02 图片分类 - 正常流程"
    Write-Host ("#" * 60)

    $resp = Send-YaakRequestJson "分类单张图片"
    $respObj = $resp | ConvertFrom-Json
    if ($respObj.code -eq 200) {
        $dataStatus = $respObj.data.status
        $itemsCount = $respObj.data.items.Count
        Write-Host "  [info] status=$dataStatus, items=$itemsCount" -ForegroundColor Cyan
        if ($dataStatus -eq "succeeded" -and $itemsCount -ge 1) {
            $firstTag = $respObj.data.items[0].suggestedTags[0]
            $firstConf = $respObj.data.items[0].confidence
            Write-Host "  [info] 第一张分类: tag=$firstTag, confidence=$firstConf" -ForegroundColor Cyan
        }
    }

    Send-YaakRequestJson "分类多张图片" | Out-Null
}
else {
    Write-Skip "testImageMediaId 未就绪，跳过正常流程分类用例。"
}

# ======== 03 图片分类 - 异常与边界 ========
Write-Host ""
Write-Host ("#" * 60)
Write-Host "#  03 图片分类 - 异常与边界"
Write-Host ("#" * 60)

Send-YaakRequestJson "空 mediaIds 列表" | Out-Null

# 不存在的 mediaId（CLIP 可用时返回 30003，不可用时返回 30001）
Send-YaakRequestJson "不存在的 mediaId" "30001,30003" | Out-Null

# 未认证访问
Send-YaakRequestJson "未认证访问" "401" | Out-Null

# ======== 汇总 ========
Write-Summary

$total = $Script:TestResults.Count
$passed = ($Script:TestResults | Where-Object { $_.Passed }).Count
if ($passed -eq $total) { exit 0 } else { exit 1 }
