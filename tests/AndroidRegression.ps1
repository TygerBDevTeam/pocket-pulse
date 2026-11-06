param(
    [Parameter(Mandatory=$true)][string]$Adb,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidPlatform,
    [Parameter(Mandatory=$true)][string]$BuildTools
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$build = Join-Path $project ('build\android-tests-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss'))
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
New-Item -ItemType Directory -Path $classes,$dex -Force | Out-Null
function Run([string]$Program,[string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Android regression command failed: $Program" }
}
$androidJar = Join-Path $AndroidPlatform 'android.jar'
$suffix = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
$source = Join-Path $project 'app\src\main\java\dev\pocketpulse\monitor'
Run (Join-Path $JavaHome ('bin/javac' + $suffix)) @('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',$classes,(Join-Path $source 'ReleaseVersion.java'),(Join-Path $source 'UpdateChecker.java'),(Join-Path $PSScriptRoot 'AndroidRegression.java'))
Run (Join-Path $JavaHome ('bin/jar' + $suffix)) @("-J-Djava.io.tmpdir=$build",'cf',(Join-Path $build 'tests.jar'),'-C',$classes,'.')
Run (Join-Path $JavaHome ('bin/java' + $suffix)) @('-cp',(Join-Path $BuildTools 'lib\d8.jar'),'com.android.tools.r8.D8','--release','--min-api','26','--lib',$androidJar,'--output',$dex,(Join-Path $build 'tests.jar'))
Run (Join-Path $JavaHome ('bin/jar' + $suffix)) @("-J-Djava.io.tmpdir=$build",'cf',(Join-Path $build 'tests-dex.jar'),'-C',$dex,'classes.dex')
Run $Adb @('-s','emulator-5554','push',(Join-Path $build 'tests-dex.jar'),'/data/local/tmp/pocket-pulse-regression.jar')
Run $Adb @('-s','emulator-5554','shell','dalvikvm','-cp','/data/local/tmp/pocket-pulse-regression.jar','dev.pocketpulse.monitor.AndroidRegression')
