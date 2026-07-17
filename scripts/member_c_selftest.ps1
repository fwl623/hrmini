$ErrorActionPreference = "Continue"
$Base = "http://localhost:8080/api/v1"
$Report = New-Object System.Collections.Generic.List[object]
$Stamp = Get-Date -Format "yyyyMMddHHmmss"
$Mobile = "139" + $Stamp.Substring(4,8)

function Add-Result($name, $expect, $actual, $pass, $detail) {
    $row = [PSCustomObject]@{ Name=$name; Expect="$expect"; Actual="$actual"; Result=$(if($pass){"PASS"}else{"FAIL"}); Detail="$detail" }
    [void]$Report.Add($row)
    Write-Host ("[{0}] {1} | expect={2} | actual={3} | {4}" -f $(if($pass){"PASS"}else{"FAIL"}), $name, $expect, $actual, $detail)
}

function Login($username, $password) {
    $body = @{ username=$username; password=$password } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$Base/auth/login" -Method POST -Body $body -ContentType "application/json; charset=utf-8"
    if ($r.code -ne 0) { throw "login failed $username" }
    return $r.data.accessToken
}

function Invoke-Api($method, $path, $token, $bodyObj, $name, $expectCode) {
    $headers = @{ Authorization = "Bearer $token" }
    $uri = "$Base$path"
    $jsonBody = $null
    if ($null -ne $bodyObj) { $jsonBody = ($bodyObj | ConvertTo-Json -Depth 8 -Compress) }
    try {
        if ($null -ne $jsonBody) {
            $raw = Invoke-WebRequest -Uri $uri -Method $method -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($jsonBody)) -ContentType "application/json; charset=utf-8" -UseBasicParsing
        } else {
            $raw = Invoke-WebRequest -Uri $uri -Method $method -Headers $headers -UseBasicParsing
        }
        $obj = $raw.Content | ConvertFrom-Json
        $code = $obj.code
        Add-Result $name $expectCode $code ($code -eq $expectCode) ($obj.message)
        return $obj
    } catch {
        $txt = $null
        if ($_.ErrorDetails) { $txt = $_.ErrorDetails.Message }
        if (-not $txt -and $_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $txt = $reader.ReadToEnd()
            } catch {}
        }
        if ($txt) {
            try {
                $obj = $txt | ConvertFrom-Json
                Add-Result $name $expectCode $obj.code ($obj.code -eq $expectCode) ($obj.message)
                return $obj
            } catch {}
        }
        Add-Result $name $expectCode "HTTP_ERR" $false ($_.Exception.Message)
        return $null
    }
}

$tokHR  = Login "13800001001" "Admin@123"
$tokMgr = Login "13800001002" "Admin@123"
$tokEmp = Login "13800001004" "Admin@123"

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
function Sql([string]$q) { & $mysql -uroot -p"20051002" --default-character-set=utf8mb4 -e "USE hrms; $q" 2>$null | Out-Null }

Sql "UPDATE employee SET employment_status=10 WHERE id=101;"

# ---- 1 onboarding ----
$onboardBody = @{
    name = "SelfTestCand$Stamp"
    gender = "MALE"
    mobile = $Mobile
    email = "selftest$Stamp@example.com"
    idNumber = ("11010119900101" + $Stamp.Substring(8,4))
    expectedOnboardDate = "2026-08-01"
    departmentId = 10
    positionId = 20
    employmentType = "fulltime"
    probationMonths = 3
    probationSalaryRatio = 0.8
    managerId = 102
    baseSalary = 15000
    positionStandard = $true
    gradeMaxSalary = 20000
}

$r = Invoke-Api POST "/onboarding/applications" $tokHR $onboardBody "OB-create-draft" 0
$onboardId = $r.data.id
Invoke-Api GET "/onboarding/applications?page=1" $tokHR $null "OB-list" 0 | Out-Null
Invoke-Api GET "/onboarding/applications/stats" $tokHR $null "OB-stats" 0 | Out-Null

$editBody = @{
    name = "SelfTestRenamed$Stamp"
    gender = "MALE"
    mobile = $Mobile
    email = "selftest$Stamp@example.com"
    idNumber = ("11010119900101" + $Stamp.Substring(8,4))
    expectedOnboardDate = "2026-08-01"
    departmentId = 10
    positionId = 20
    employmentType = "fulltime"
    probationMonths = 3
    probationSalaryRatio = 0.8
    managerId = 102
    baseSalary = 15000
    positionStandard = $true
    gradeMaxSalary = 20000
}
Invoke-Api PUT "/onboarding/applications/$onboardId" $tokHR $editBody "OB-edit-draft" 0 | Out-Null
Invoke-Api POST "/onboarding/applications/$onboardId/submit" $tokHR $null "OB-submit" 0 | Out-Null
Invoke-Api POST "/onboarding/applications/$onboardId/withdraw" $tokHR $null "OB-withdraw" 0 | Out-Null
Invoke-Api POST "/onboarding/applications/$onboardId/submit" $tokHR $null "OB-submit-again" 0 | Out-Null
Invoke-Api POST "/onboarding/applications/$onboardId/abandon" $tokHR $null "OB-abandon" 0 | Out-Null
$rDel = Invoke-Api DELETE "/onboarding/applications/$onboardId" $tokHR $null "OB-delete-after-abandon" 0
if (-not $rDel -or $rDel.code -ne 0) {
    $m2 = "138" + $Stamp.Substring(2,8)
    $b2 = @{
        name="DeletePath$Stamp"; gender="MALE"; mobile=$m2; email="d$Stamp@ex.com"
        idNumber=("11010119900202"+$Stamp.Substring(8,4)); expectedOnboardDate="2026-08-01"
        departmentId=10; positionId=20; employmentType="fulltime"; probationMonths=3
        probationSalaryRatio=0.8; managerId=102; baseSalary=15000; positionStandard=$true
    }
    $r2 = Invoke-Api POST "/onboarding/applications" $tokHR $b2 "OB-delete-path-create" 0
    $delId = $r2.data.id
    Invoke-Api POST "/onboarding/applications/$delId/submit" $tokHR $null "OB-delete-path-submit" 0 | Out-Null
    Invoke-Api POST "/onboarding/applications/$delId/withdraw" $tokHR $null "OB-delete-path-withdraw" 0 | Out-Null
    Invoke-Api DELETE "/onboarding/applications/$delId" $tokHR $null "OB-delete-after-withdraw" 0 | Out-Null
}

# ---- 2 regularization ----
Invoke-Api GET "/regularization/applications/pending" $tokHR $null "REG-pending-list" 0 | Out-Null
Invoke-Api POST "/regularization/applications" $tokHR @{ employeeId=101; performanceEvaluation="good"; approvalResult="PASS" } "REG-create" 0 | Out-Null

# ---- 3 transfer ----
$r = Invoke-Api POST "/transfers" $tokHR @{
    employeeId=104; newDepartmentId=12; newPositionId=24; newJobLevel="S2"; newManagerId=105
    salaryAdjustment=0; effectiveDate="2026-08-01"; reason="selftest-transfer"
} "XFER-create" 0
$xferId = $null; if ($r -and $r.data) { $xferId = $r.data.id }
Invoke-Api POST "/transfers" $tokHR @{
    employeeId=103; newDepartmentId=11; newPositionId=22; newJobLevel="S2"; effectiveDate="2026-08-01"; reason="same-dept"
} "XFER-same-dept-expect-30004" 30004 | Out-Null
Invoke-Api GET "/transfers?page=1" $tokHR $null "XFER-list" 0 | Out-Null
if ($xferId) { Invoke-Api GET "/transfers/$xferId" $tokHR $null "XFER-detail" 0 | Out-Null }
else { Add-Result "XFER-detail" 0 "SKIP" $false "no xfer id" }

# ---- 4 resignation employee ----
$r = Invoke-Api POST "/profile/resignation-requests" $tokEmp @{
    expectedResignDate="2026-08-01"; reasonCategory="VOLUNTARY"; resignationType="resignation"; reasonDetail="selftest"
} "RES-EMP-create" 0
$empReqId = $null; if ($r -and $r.data) { $empReqId = $r.data.id }
Invoke-Api GET "/profile/resignation-requests" $tokEmp $null "RES-EMP-list" 0 | Out-Null
if ($empReqId) { Invoke-Api POST "/profile/resignation-requests/$empReqId/cancel" $tokEmp $null "RES-EMP-cancel" 0 | Out-Null }
$r = Invoke-Api POST "/profile/resignation-requests" $tokEmp @{
    expectedResignDate="2026-08-01"; reasonCategory="VOLUNTARY"; resignationType="resignation"; reasonDetail="for-hr"
} "RES-EMP-create-for-hr" 0
$empReqId2 = $null; if ($r -and $r.data) { $empReqId2 = $r.data.id }

# ---- 5 resignation HR ----
Invoke-Api GET "/resignation-requests" $tokHR $null "RES-HR-request-list" 0 | Out-Null
$r = Invoke-Api POST "/resignations" $tokHR @{
    employeeId=103; requestId=$empReqId2; resignationDate="2026-08-15"; reasonCategory="VOLUNTARY"
    resignationType="resignation"; reasonDetail="hr-formal"; handoverEmployeeId=101
} "RES-HR-formal" 0
$resignId = $null; if ($r -and $r.data) { $resignId = $r.data.id }
Invoke-Api GET "/resignations?page=1" $tokHR $null "RES-HR-list" 0 | Out-Null
Invoke-Api GET "/resignations/stats" $tokHR $null "RES-HR-stats" 0 | Out-Null
if ($resignId) { Invoke-Api GET "/resignations/$resignId" $tokHR $null "RES-HR-detail" 0 | Out-Null }
else { Add-Result "RES-HR-detail" 0 "SKIP" $false "no resign id" }

# ---- 6 approvals ----
function New-Onboard([string]$suffix, [string]$name) {
    $b = @{
        name=$name; gender="FEMALE"; mobile=("137"+$suffix); email=("a"+$suffix+"@ex.com")
        idNumber=("11010119910101"+$suffix.Substring(0,[Math]::Min(4,$suffix.Length)).PadRight(4,"0"))
        expectedOnboardDate="2026-09-01"; departmentId=10; positionId=20; employmentType="fulltime"
        probationMonths=3; probationSalaryRatio=1; managerId=102; baseSalary=12000; positionStandard=$true
    }
    $resp = Invoke-Api POST "/onboarding/applications" $tokHR $b ("APPR-prep-create-"+$name) 0
    $id = $resp.data.id
    Invoke-Api POST "/onboarding/applications/$id/submit" $tokHR $null ("APPR-prep-submit-"+$name) 0 | Out-Null
    return $id
}

$sb = $Stamp.Substring(6,8)
New-Onboard ($sb+"01") "ApproveCase" | Out-Null
New-Onboard ($sb+"02") "RejectCase" | Out-Null
New-Onboard ($sb+"03") "ForwardCase" | Out-Null
New-Onboard ($sb+"04") "RemindCase" | Out-Null
New-Onboard ($sb+"05") "WithdrawCase" | Out-Null

Invoke-Api GET "/approvals/tasks/stats" $tokMgr $null "APPR-stats" 0 | Out-Null
Invoke-Api GET "/approvals/tasks?page=1" $tokMgr $null "APPR-todo-list" 0 | Out-Null

function Get-PendingTasks {
    $resp = Invoke-RestMethod -Uri ($Base + "/approvals/tasks?page=1&pageSize=50") -Headers @{ Authorization = ("Bearer " + $tokMgr) } -Method GET
    if ($resp.data.list) { return @($resp.data.list) }
    if ($resp.data -is [System.Array]) { return @($resp.data) }
    return @()
}

$pending = Get-PendingTasks
if ($pending.Count -eq 0) { Add-Result "APPR-no-pending" 0 "EMPTY" $false "mgr has no pending tasks" }

if ($pending.Count -gt 0) {
    $tid = $pending[0].taskId; if (-not $tid) { $tid = $pending[0].id }
    Invoke-Api GET "/approvals/tasks/$tid" $tokMgr $null "APPR-detail" 0 | Out-Null
}

$pending = Get-PendingTasks
if ($pending.Count -gt 0) {
    $tid = $pending[0].taskId; if (-not $tid) { $tid = $pending[0].id }
    Invoke-Api POST "/approvals/tasks/$tid/action" $tokMgr @{ action="APPROVE"; comment="ok" } "APPR-approve" 0 | Out-Null
}
$pending = Get-PendingTasks
if ($pending.Count -gt 0) {
    $tid = $pending[0].taskId; if (-not $tid) { $tid = $pending[0].id }
    Invoke-Api POST "/approvals/tasks/$tid/action" $tokMgr @{ action="REJECT"; comment="incomplete" } "APPR-reject" 0 | Out-Null
}
$pending = Get-PendingTasks
if ($pending.Count -gt 0) {
    $tid = $pending[0].taskId; if (-not $tid) { $tid = $pending[0].id }
    Invoke-Api POST "/approvals/tasks/$tid/action" $tokMgr @{ action="FORWARD"; targetUserId=1004; comment="to-sunqi" } "APPR-forward" 0 | Out-Null
}
$pending = Get-PendingTasks
if ($pending.Count -gt 0) {
    $tid = $pending[0].taskId; if (-not $tid) { $tid = $pending[0].id }
    Invoke-Api POST "/approvals/tasks/$tid/remind" $tokHR $null "APPR-remind" 0 | Out-Null
} else { Add-Result "APPR-remind" 0 "SKIP" $false "no pending" }

$inst = Invoke-Api GET "/approvals/instances" $tokHR $null "APPR-my-instances" 0
$instanceId = $null
if ($inst -and $inst.data) {
    $ilist = @()
    if ($inst.data.list) { $ilist = @($inst.data.list) }
    elseif ($inst.data -is [System.Array]) { $ilist = @($inst.data) }
    if ($ilist.Count -gt 0) {
        $instanceId = $ilist[0].instanceId
        if (-not $instanceId) { $instanceId = $ilist[0].id }
    }
}
if ($instanceId) { Invoke-Api POST "/approvals/instances/$instanceId/withdraw" $tokHR $null "APPR-withdraw-instance" 0 | Out-Null }
else { Add-Result "APPR-withdraw-instance" 0 "SKIP" $false "no instance" }

# ---- 7 delegation ----
$r = Invoke-Api POST "/approvals/delegations" $tokMgr @{
    delegateUserId=1004; startDate="2026-07-01"; endDate="2026-12-31"; reason="vacation"
} "DELG-create" 0
$delId = $null; if ($r -and $r.data) { $delId = $r.data.id }
Invoke-Api POST "/approvals/delegations" $tokMgr @{
    delegateUserId=1004; startDate="2026-07-01"; endDate="2026-12-31"; reason="vacation"
} "DELG-duplicate-expect-60003" 60003 | Out-Null
Invoke-Api GET "/approvals/delegations" $tokMgr $null "DELG-list" 0 | Out-Null
if (-not $delId) {
    $list = Invoke-RestMethod -Uri ($Base + "/approvals/delegations") -Headers @{ Authorization = ("Bearer " + $tokMgr) }
    $dlist = @(); if ($list.data.list) { $dlist=@($list.data.list) } elseif ($list.data -is [System.Array]) { $dlist=@($list.data) }
    if ($dlist.Count -gt 0) { $delId = $dlist[0].id }
}
if ($delId) { Invoke-Api DELETE "/approvals/delegations/$delId" $tokMgr $null "DELG-cancel" 0 | Out-Null }
else { Add-Result "DELG-cancel" 0 "SKIP" $false "no del id" }

# ---- BOUNDARY ----
Write-Host "===== BOUNDARY ====="
Invoke-Api POST "/onboarding/applications" $tokHR @{
    name="DupMobile"; gender="MALE"; mobile="13800001001"; email="dup@ex.com"; idNumber="110101199003033333"
    expectedOnboardDate="2026-08-01"; departmentId=10; positionId=20; employmentType="fulltime"
    probationMonths=3; probationSalaryRatio=1; managerId=102; baseSalary=12000; positionStandard=$true
} "B-OB-mobile-dup-expect-30005" 30005 | Out-Null

Invoke-Api POST "/onboarding/applications" $tokHR @{
    name="BadDept"; gender="MALE"; mobile=("136"+$Stamp.Substring(4,8)); email="bd@ex.com"; idNumber="110101199004044444"
    expectedOnboardDate="2026-08-01"; departmentId=999999; positionId=20; employmentType="fulltime"
    probationMonths=3; probationSalaryRatio=1; managerId=102; baseSalary=12000; positionStandard=$true
} "B-OB-dept-missing-expect-404" 404 | Out-Null

Invoke-Api POST "/onboarding/applications/999999001/submit" $tokHR $null "B-OB-submit-deleted-expect-404" 404 | Out-Null

$rab = Invoke-Api POST "/onboarding/applications" $tokHR @{
    name="AbandonThenSubmit"; gender="MALE"; mobile=("135"+$Stamp.Substring(4,8)); email="ab@ex.com"; idNumber="110101199005055555"
    expectedOnboardDate="2026-08-01"; departmentId=10; positionId=20; employmentType="fulltime"
    probationMonths=3; probationSalaryRatio=1; managerId=102; baseSalary=12000; positionStandard=$true
} "B-OB-abandon-create" 0
$abId = $rab.data.id
Invoke-Api POST "/onboarding/applications/$abId/abandon" $tokHR $null "B-OB-abandon" 0 | Out-Null
Invoke-Api POST "/onboarding/applications/$abId/submit" $tokHR $null "B-OB-submit-abandoned-expect-biz-reject" 10001 | Out-Null

$rcf = Invoke-Api POST "/onboarding/applications" $tokHR @{
    name="ConfirmEarly"; gender="MALE"; mobile=("134"+$Stamp.Substring(4,8)); email="cf@ex.com"; idNumber="110101199006066666"
    expectedOnboardDate="2026-08-01"; departmentId=10; positionId=20; employmentType="fulltime"
    probationMonths=3; probationSalaryRatio=1; managerId=102; baseSalary=12000; positionStandard=$true
} "B-OB-confirm-create" 0
$cfId = $rcf.data.id
Invoke-Api POST "/onboarding/applications/$cfId/confirm" $tokHR $null "B-OB-confirm-not-approved-expect-biz-reject" 10001 | Out-Null

Sql "UPDATE employee SET employment_status=40 WHERE id=105;"
Invoke-Api POST "/transfers" $tokHR @{
    employeeId=105; newDepartmentId=10; newPositionId=20; effectiveDate="2026-08-01"; reason="resigned"
} "B-XFER-resigned-expect-30003" 30003 | Out-Null
Sql "UPDATE employee SET employment_status=20 WHERE id=105;"

Invoke-Api POST "/transfers" $tokHR @{
    employeeId=102; newDepartmentId=10; newPositionId=23; effectiveDate="2026-08-01"; reason="same"
} "B-XFER-same-dept-expect-30004" 30004 | Out-Null

Invoke-Api POST "/transfers" $tokHR @{
    employeeId=102; newDepartmentId=12; newPositionId=24; effectiveDate="2026-07-01"; reason="past"
} "B-XFER-past-date-expect-10001" 10001 | Out-Null

Invoke-Api POST "/resignations" $tokHR @{
    employeeId=104; requestId=999999; resignationDate="2026-08-20"; reasonCategory="VOLUNTARY"
    resignationType="resignation"; handoverEmployeeId=101
} "B-RES-missing-request-expect-404" 404 | Out-Null

Invoke-Api POST "/profile/resignation-requests" $tokEmp @{
    expectedResignDate="2026-07-01"; reasonCategory="VOLUNTARY"; resignationType="resignation"
} "B-RES-past-date-expect-10001" 10001 | Out-Null

if ($empReqId2) {
    Sql ("UPDATE employee_resignation_request SET status='APPROVED' WHERE id=" + $empReqId2 + ";")
    Invoke-Api POST "/profile/resignation-requests/$empReqId2/cancel" $tokEmp $null "B-RES-cancel-approved-expect-biz-reject" 10001 | Out-Null
}

$pending = Get-PendingTasks
if ($pending.Count -eq 0) { New-Onboard ($Stamp.Substring(4,8)+"77") "BoundAppr" | Out-Null; $pending = Get-PendingTasks }
if ($pending.Count -gt 0) {
    $tid = $pending[0].taskId; if (-not $tid) { $tid = $pending[0].id }
    Invoke-Api POST "/approvals/tasks/$tid/action" $tokMgr @{ action="REJECT"; comment="" } "B-APPR-reject-empty-comment-expect-10001" 10001 | Out-Null
    Invoke-Api POST "/approvals/tasks/$tid/action" $tokMgr @{ action="FORWARD" } "B-APPR-forward-no-target-expect-10001" 10001 | Out-Null
}
Invoke-Api POST "/approvals/tasks/999999001/action" $tokMgr @{ action="APPROVE" } "B-APPR-missing-task-expect-404" 404 | Out-Null

$pending = Get-PendingTasks
if ($pending.Count -gt 0) {
    $tid = $pending[0].taskId; if (-not $tid) { $tid = $pending[0].id }
    Invoke-Api POST "/approvals/tasks/$tid/action" $tokMgr @{ action="APPROVE"; comment="first" } "B-APPR-first-approve" 0 | Out-Null
    Invoke-Api POST "/approvals/tasks/$tid/action" $tokMgr @{ action="APPROVE"; comment="again" } "B-APPR-dup-approve-expect-60001" 60001 | Out-Null
} else { Add-Result "B-APPR-dup-approve-expect-60001" 60001 "SKIP" $false "no pending" }

Invoke-Api POST "/approvals/delegations" $tokMgr @{
    delegateUserId=1002; startDate="2026-07-01"; endDate="2026-12-31"
} "B-DELG-self-expect-10001" 10001 | Out-Null
Invoke-Api POST "/approvals/delegations" $tokMgr @{
    delegateUserId=1004; startDate="2026-12-31"; endDate="2026-07-01"
} "B-DELG-bad-range-expect-10001" 10001 | Out-Null
Invoke-Api DELETE "/approvals/delegations/999999001" $tokMgr $null "B-DELG-missing-expect-404" 404 | Out-Null

$passN = @($Report | Where-Object { $_.Result -eq "PASS" }).Count
$failN = @($Report | Where-Object { $_.Result -eq "FAIL" }).Count
Write-Host ("===== SUMMARY PASS={0} FAIL={1} TOTAL={2} =====" -f $passN, $failN, $Report.Count)

$outDir = "E:\桌面\数字马力\人资管理系统系分"
$Report | Export-Csv -Path (Join-Path $outDir "成员C-郭策-自测原始结果.csv") -NoTypeInformation -Encoding UTF8
$Report | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $outDir "成员C-郭策-自测原始结果.json") -Encoding UTF8
$Report | Format-Table -AutoSize | Out-String -Width 220 | Write-Host