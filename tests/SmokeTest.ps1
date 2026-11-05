param([Parameter(Mandatory=$true)][string]$Adb)
$ErrorActionPreference = 'Stop'
$serial = 'emulator-5554'
function Invoke-Adb([string[]]$Command) {
    $result = & $Adb -s $serial @Command
    if ($LASTEXITCODE -ne 0) { throw ('adb failed: ' + ($Command -join ' ')) }
    return ($result -join "`n")
}
function Get-Ui {
    $null = Invoke-Adb @('shell','uiautomator','dump','/sdcard/pocket-pulse-test.xml')
    [xml](Invoke-Adb @('shell','cat','/sdcard/pocket-pulse-test.xml'))
}
function Find-Text($Ui, [string]$Text) {
    @($Ui.SelectNodes('//node') | Where-Object { $_.text -eq $Text }) | Select-Object -First 1
}
function Expect-Text([string]$Text) {
    if (!(Find-Text (Get-Ui) $Text)) { throw ('Expected UI text: ' + $Text) }
    Write-Output ('PASS: ' + $Text)
}
function Click-Text([string]$Text) {
    $node = Find-Text (Get-Ui) $Text
    if (!$node) { throw ('Missing control: ' + $Text) }
    $bounds = [regex]::Matches($node.bounds, '\d+')
    $x = [int](([int]$bounds[0].Value + [int]$bounds[2].Value) / 2)
    $y = [int](([int]$bounds[1].Value + [int]$bounds[3].Value) / 2)
    $null = Invoke-Adb @('shell','input','tap',[string]$x,[string]$y)
}
$null = Invoke-Adb @('shell','am','start','-W','-n','dev.pocketpulse.monitor/.MainActivity')
$ui = Get-Ui
foreach ($label in @('CPU','GPU','SYSTEM RAM','BATTERY','THERMAL STATUS','THIS APP')) {
    if (!(Find-Text $ui $label)) { throw ('Missing metric card: ' + $label) }
}
Write-Output 'PASS: All six metric cards present'
Click-Text 'Pause'
Expect-Text 'PAUSED'
Expect-Text 'Resume'
$null = Invoke-Adb @('shell','dumpsys','gfxinfo','dev.pocketpulse.monitor','reset')
Start-Sleep -Seconds 5
$graphics = Invoke-Adb @('shell','dumpsys','gfxinfo','dev.pocketpulse.monitor')
if ($graphics -notmatch 'Total frames rendered: 0') { throw 'Unexpected drawing while paused' }
Write-Output 'PASS: No redraws during five-second pause'
Click-Text 'Resume'
Expect-Text 'Pause'
Click-Text '2s refresh'
Click-Text '5 seconds · low power'
Expect-Text '5s refresh'
$null = Invoke-Adb @('shell','am','force-stop','dev.pocketpulse.monitor')
$null = Invoke-Adb @('shell','am','start','-W','-n','dev.pocketpulse.monitor/.MainActivity')
Expect-Text '5s refresh'
Write-Output 'PASS: Refresh setting survived process restart'
Click-Text '5s refresh'
Click-Text '2 seconds · balanced'
Click-Text 'Display'
Expect-Text 'One display detected'
Click-Text 'Got it'
Click-Text 'Options'
Expect-Text 'Keep this screen awake'
Expect-Text 'Dim this screen to 25%'
Expect-Text 'Compact cards'
Click-Text 'Done'
$null = Invoke-Adb @('shell','input','keyevent','3')
Start-Sleep -Seconds 1
$null = Invoke-Adb @('shell','dumpsys','gfxinfo','dev.pocketpulse.monitor','reset')
Start-Sleep -Seconds 5
$graphics = Invoke-Adb @('shell','dumpsys','gfxinfo','dev.pocketpulse.monitor')
if ($graphics -notmatch 'Total frames rendered: 0') { throw 'Unexpected drawing while hidden' }
Write-Output 'PASS: No redraws while hidden for five seconds'
$null = Invoke-Adb @('shell','am','start','-W','-n','dev.pocketpulse.monitor/.MainActivity')
Expect-Text 'Pause'
$crashes = Invoke-Adb @('logcat','-d','-b','crash','-s','AndroidRuntime:E')
if ($crashes -match 'dev.pocketpulse.monitor') { throw $crashes }
Write-Output 'PASS: No Pocket Pulse crashes in emulator crash log'
