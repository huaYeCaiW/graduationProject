# api-regression.ps1 - API regression suite for the dongHuan monitoring platform (stage 6)
#
# Prerequisites:
#   1. MySQL (dh-mysql) + EMQX (dh-emqx) running, seed.sql loaded
#   2. Spring Boot app running on port 8080
#   3. For the data-dependent checks (latest / range / snapshot), run simulator.py briefly:
#        python simulator.py th-01
#        python simulator.py th-02
#      If no data is present, those checks are reported as SKIP.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File api-regression.ps1
#   powershell -ExecutionPolicy Bypass -File api-regression.ps1 -Base http://localhost:8080
#   powershell -ExecutionPolicy Bypass -File api-regression.ps1 -Password password
#
# Exit code: 0 = all passed (skips allowed), 1 = at least one failure or server unreachable.

param(
    [string]$Base = 'http://localhost:8080',
    [string]$Password = 'password'
)

$script:rows = @()

function Req($s, $m, $path, $body) {
    try {
        $opt = @{ Uri = "$Base$path"; Method = $m; UseBasicParsing = $true }
        if ($s) { $opt.WebSession = $s }
        if ($body) { $opt.Body = $body; $opt.ContentType = 'application/json' }
        $r = Invoke-WebRequest @opt
        $txt = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return @{ code = [int]$r.StatusCode; body = $txt }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $rd = New-Object System.IO.StreamReader($resp.GetResponseStream())
            return @{ code = [int]$resp.StatusCode; body = $rd.ReadToEnd() }
        }
        return @{ code = 0; body = $_.Exception.Message }
    }
}

function NewSession($u) {
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $b = @{ username = $u; password = $Password } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$Base/api/auth/login" -Method Post -Body $b `
        -ContentType 'application/json' -WebSession $s -UseBasicParsing
    return $s
}

function Check($name, $actual, $expected) {
    $ok = ($actual -eq $expected)
    $script:rows += [pscustomobject]@{ Test = $name; Actual = "$actual"; Expected = "$expected"; State = $(if ($ok) { 'PASS' } else { 'FAIL' }) }
    if ($ok) { Write-Host ("[PASS] {0}" -f $name) -ForegroundColor Green }
    else { Write-Host ("[FAIL] {0}  actual={1} expected={2}" -f $name, $actual, $expected) -ForegroundColor Red }
}

function Skip($name, $reason) {
    $script:rows += [pscustomobject]@{ Test = $name; Actual = '-'; Expected = '-'; State = 'SKIP' }
    Write-Host ("[SKIP] {0}  ({1})" -f $name, $reason) -ForegroundColor Yellow
}

function HexOf($s) {
    return (([System.Text.Encoding]::UTF8.GetBytes($s) | ForEach-Object { $_.ToString('X2') }) -join '')
}

function ToList($json) { return @($json | ConvertFrom-Json) }

# ---------------------------------------------------------------- reachability

$probe = Req $null 'Get' '/api/auth/me'
if ($probe.code -eq 0) {
    Write-Host ("Server not reachable at {0}: {1}" -f $Base, $probe.body) -ForegroundColor Red
    exit 1
}
if ($probe.code -ne 401) {
    Write-Host ("Unexpected /api/auth/me status: {0}" -f $probe.code) -ForegroundColor Red
    exit 1
}

Write-Host "=== 1. Authentication ===" -ForegroundColor Cyan
Check 'anon GET /api/devices -> 401' (Req $null 'Get' '/api/devices').code 401
Check 'anon GET /api/snapshots -> 401' (Req $null 'Get' '/api/snapshots').code 401
Check 'anon GET /api/alarms -> 401' (Req $null 'Get' '/api/alarms').code 401
Check 'anon PUT threshold -> 401' (Req $null 'Put' '/api/points/th-01/temperature/threshold' '{"min":0,"max":100}').code 401
Check 'anon POST confirm -> 401' (Req $null 'Post' '/api/alarms/1/confirm').code 401
Check 'bad password -> 401' (Req $null 'Post' '/api/auth/login' '{"username":"farmer1","password":"wrong"}').code 401

try {
    $s1 = NewSession 'farmer1'
    $s2 = NewSession 'farmer2'
    $so = NewSession 'ops'
    $sa = NewSession 'admin'
} catch {
    Write-Host "Login failed for seed users (farmer1/farmer2/ops/admin). Check seed.sql and -Password." -ForegroundColor Red
    exit 1
}
Check 'GET /api/auth/me -> 200' (Req $s1 'Get' '/api/auth/me').code 200

Write-Host "=== 2. Devices (tenant isolation) ===" -ForegroundColor Cyan
$d1 = ToList (Req $s1 'Get' '/api/devices').body
$d2 = ToList (Req $s2 'Get' '/api/devices').body
$do = ToList (Req $so 'Get' '/api/devices').body
$da = ToList (Req $sa 'Get' '/api/devices').body
Check 'farmer1 device count' $d1.Count 1
Check 'farmer1 device code' $d1[0].deviceCode 'th-01'
Check 'farmer2 device code' $d2[0].deviceCode 'th-02'
Check 'ops device count' $do.Count 2
Check 'admin device count' $da.Count 2
Check 'farmer1 cross device -> 404' (Req $s1 'Get' '/api/devices/th-02').code 404
Check 'farmer1 own device -> 200' (Req $s1 'Get' '/api/devices/th-01').code 200
Check 'device name utf8 (th-01)' (HexOf $d1[0].name) 'E4B880E58FB7E5A4A7E6A39AE6B8A9E6B9BFE5BAA6E4BCA0E6849FE599A8'

Write-Host "=== 3. Points (latest / range / threshold) ===" -ForegroundColor Cyan
Check 'latest cross tenant -> 404' (Req $s1 'Get' '/api/points/th-02/temperature/latest').code 404
$start = [DateTime]::UtcNow.AddHours(-2).ToString('yyyy-MM-ddTHH:mm:ssZ')
$end = [DateTime]::UtcNow.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ssZ')
$r1 = Req $s1 'Get' "/api/points/th-01/temperature/range?start=$start&end=$end"
Check 'range own -> 200' $r1.code 200
$r2 = Req $s1 'Get' "/api/points/th-02/temperature/range?start=$start&end=$end"
Check 'range cross -> 200' $r2.code 200
Check 'range cross empty' (ToList $r2.body).Count 0
Check 'threshold cross tenant -> 404' (Req $s1 'Put' '/api/points/th-02/temperature/threshold' '{"min":0,"max":100}').code 404
Check 'threshold unknown point -> 404' (Req $s1 'Put' '/api/points/th-01/nope/threshold' '{"min":0,"max":100}').code 404

$hasData = (ToList $r1.body).Count -gt 0
if ($hasData) {
    Check 'latest own -> 200' (Req $s1 'Get' '/api/points/th-01/temperature/latest').code 200
    Check 'range own nonempty' ((ToList $r1.body).Count -gt 0) $true
} else {
    Skip 'latest own -> 200' 'no point_value rows in the last 2h, run simulator.py'
    Skip 'range own nonempty' 'no point_value rows in the last 2h, run simulator.py'
}

Write-Host "=== 4. Alarms ===" -ForegroundColor Cyan
$a1 = ToList (Req $s1 'Get' '/api/alarms').body
$a2 = ToList (Req $s2 'Get' '/api/alarms').body
$ao = ToList (Req $so 'Get' '/api/alarms').body
$aa = ToList (Req $sa 'Get' '/api/alarms').body
Check 'farmer1 alarms own points only' (@($a1 | Where-Object { $_.pointId -notin 1, 2 }).Count) 0
Check 'farmer2 alarms own points only' (@($a2 | Where-Object { $_.pointId -notin 3, 4 }).Count) 0
Check 'ops sees all alarms' $ao.Count ($a1.Count + $a2.Count)
Check 'admin sees all alarms' $aa.Count ($a1.Count + $a2.Count)
Check 'ops confirm missing alarm -> 404' (Req $so 'Post' '/api/alarms/999999/confirm').code 404
if ($a1.Count -gt 0) {
    Check 'farmer confirm -> 403' (Req $s1 'Post' "/api/alarms/$($a1[0].id)/confirm").code 403
} else {
    Skip 'farmer confirm -> 403' 'farmer1 has no alarms'
}

Write-Host "=== 5. Snapshots ===" -ForegroundColor Cyan
$j1 = (Req $s1 'Get' '/api/snapshots').body | ConvertFrom-Json
$j2 = (Req $s2 'Get' '/api/snapshots').body | ConvertFrom-Json
$jo = (Req $so 'Get' '/api/snapshots').body | ConvertFrom-Json
$ja = (Req $sa 'Get' '/api/snapshots').body | ConvertFrom-Json
$k1 = @($j1.PSObject.Properties.Name)
$k2 = @($j2.PSObject.Properties.Name)
$ko = @($jo.PSObject.Properties.Name)
$ka = @($ja.PSObject.Properties.Name)
if ($ka.Count -eq 0) {
    Skip 'snapshot isolation (4 checks)' 'snapshot is empty, run simulator.py against th-01 and th-02'
} else {
    Check 'farmer1 snapshot keys' $k1.Count 2
    Check 'farmer1 snapshot only th-01' (@($k1 | Where-Object { $_ -notlike 'th-01:*' }).Count) 0
    Check 'farmer2 snapshot only th-02' (@($k2 | Where-Object { $_ -notlike 'th-02:*' }).Count) 0
    Check 'ops snapshot keys' $ko.Count 4
    Check 'admin snapshot keys' $ka.Count 4
    $m = $j1.'th-01:temperature'
    Check 'snapshot metric utf8' (HexOf $m.metric) 'E6B8A9E5BAA6'
    Check 'snapshot unit utf8' (HexOf $m.unit) 'E28483'
    Check 'snapshot value in simulator range' (($m.value -ge 20) -and ($m.value -le 32)) $true
}

# ---------------------------------------------------------------- summary

$pass = @($script:rows | Where-Object { $_.State -eq 'PASS' }).Count
$fail = @($script:rows | Where-Object { $_.State -eq 'FAIL' }).Count
$skip = @($script:rows | Where-Object { $_.State -eq 'SKIP' }).Count
Write-Host ""
Write-Host ("TOTAL={0} PASS={1} FAIL={2} SKIP={3}" -f $script:rows.Count, $pass, $fail, $skip) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 } else { exit 0 }
