param(
    [Parameter(Mandatory=$true)][string]$Adb,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidPlatform,
    [Parameter(Mandatory=$true)][string]$BuildTools
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$output = Join-Path $project 'build/android-validation'
New-Item -ItemType Directory -Path $output -Force | Out-Null
function Adb([string[]]$Command) {
    $result = & $Adb -s emulator-5554 @Command
    if ($LASTEXITCODE -ne 0) { throw ('adb failed: ' + ($Command -join ' ')) }
    return ($result -join "`n")
}
function Ui {
    $null = Adb @('shell','uiautomator','dump','/sdcard/pocket-pulse-review.xml')
    [xml](Adb @('shell','cat','/sdcard/pocket-pulse-review.xml'))
}
function Click([string]$Text) {
    $node = @((Ui).SelectNodes('//node') | Where-Object { $_.text -eq $Text }) | Select-Object -First 1
    if (!$node) { throw "Missing control: $Text" }
    $b = [regex]::Matches($node.bounds,'\d+')
    $null = Adb @('shell','input','tap',[string]([int](([int]$b[0].Value+[int]$b[2].Value)/2)),[string]([int](([int]$b[1].Value+[int]$b[3].Value)/2)))
}
function Capture([string]$Name) {
    $null = Adb @('shell','screencap','-p',('/sdcard/' + $Name + '.png'))
    $null = Adb @('pull',('/sdcard/' + $Name + '.png'),(Join-Path $output ($Name + '.png')))
    (Ui).OuterXml | Set-Content -LiteralPath (Join-Path $output ($Name + '.xml'))
}
function Set-Window([string]$Size,[string]$Density) {
    $null = Adb @('shell','wm','size',$Size)
    $null = Adb @('shell','wm','density',$Density)
    $null = Adb @('shell','am','force-stop','dev.pocketpulse.monitor')
    $null = Adb @('shell','am','start','-W','-n','dev.pocketpulse.monitor/.MainActivity')
    Start-Sleep -Seconds 2
}
$null = Adb @('shell','settings','put','system','accelerometer_rotation','0')
$null = Adb @('shell','settings','put','system','user_rotation','0')
$null = Adb @('shell','input','keyevent','82')
$null = Adb @('install','-r',(Join-Path $project 'dist/Pocket-Pulse-0.2.0.apk'))
Set-Window '960x720' '200'
$null = Adb @('shell','cmd','media_session','volume','--stream','3','--set','5')
Capture 'landscape-4-3'
& (Join-Path $PSScriptRoot 'AndroidRegression.ps1') -Adb $Adb -JavaHome $JavaHome -AndroidPlatform $AndroidPlatform -BuildTools $BuildTools
& (Join-Path $PSScriptRoot 'SmokeTest.ps1') -Adb $Adb
Click 'Options'
Capture 'options'
Click 'Check for updates'
Start-Sleep -Seconds 10
$check = (Ui).OuterXml
if ($check -notmatch 'No newer APK found') { Capture 'update-check-failed'; throw 'Expected successful live GitHub update check' }
Capture 'update-check'
Click 'OK'
Set-Window '1240x1080' '320'
Capture 'thor-lower-screen'
Set-Window '480x360' '160'
Capture 'compact-window'
$scroll = @((Ui).SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.ScrollView' }) | Select-Object -First 1
if (!$scroll -or $scroll.scrollable -ne 'true') { throw 'Compact cards must be scrollable' }
$bounds = [regex]::Matches($scroll.bounds,'\d+')
$scrollX = [string]([int](([int]$bounds[0].Value + [int]$bounds[2].Value) / 2))
$scrollTop = [int]$bounds[1].Value
$scrollHeight = [int]$bounds[3].Value - $scrollTop
$scrollStart = [string]([int]($scrollTop + $scrollHeight * 0.8))
$scrollEnd = [string]([int]($scrollTop + $scrollHeight * 0.2))
for ($i = 0; $i -lt 8; $i++) { $null = Adb @('shell','input','swipe',$scrollX,$scrollStart,$scrollX,$scrollEnd,'400') }
$lastCompactCard = @((Ui).SelectNodes('//node') | Where-Object { $_.text -eq 'THIS APP' })
if ($lastCompactCard.Count -eq 0) { throw 'Could not reach the last compact card' }
Capture 'compact-window-scrolled'
Set-Window '720x960' '200'
Capture 'portrait'
Set-Window '960x720' '200'
$null = Adb @('shell','settings','put','system','font_scale','1.3')
Start-Sleep -Seconds 2
Capture 'large-font'
$null = Adb @('shell','settings','put','system','font_scale','1.0')
Set-Window '960x720' '200'
Capture 'landscape-4-3'
$crashes = Adb @('logcat','-d','-b','crash','-s','AndroidRuntime:E')
$crashes | Set-Content -LiteralPath (Join-Path $output 'crash-log.txt')
if ($crashes -match 'dev.pocketpulse.monitor') { throw $crashes }
Write-Output 'PASS: Android layouts, live GitHub update check and crash-log validation'
