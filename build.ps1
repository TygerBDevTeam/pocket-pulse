param(
    [string]$JavaHome,
    [string]$AndroidPlatform,
    [string]$BuildTools,
    [string]$Keystore,
    [string]$SigningPasswordFile
)
$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$localTools = Join-Path (Split-Path $project -Parent) '.toolchain'
if (!$JavaHome) {
    if ($env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME }
    else { $JavaHome = (Get-ChildItem -LiteralPath (Join-Path $localTools 'jdk') -Directory | Select-Object -First 1).FullName }
}
if (!$AndroidPlatform) { $AndroidPlatform = Join-Path $localTools 'platform\android-36' }
if (!$BuildTools) { $BuildTools = Join-Path $localTools 'build-tools\android-16' }
$binarySuffix = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
$java = Join-Path $JavaHome ('bin/java' + $binarySuffix)
$javac = Join-Path $JavaHome ('bin/javac' + $binarySuffix)
$jar = Join-Path $JavaHome ('bin/jar' + $binarySuffix)
$keytool = Join-Path $JavaHome ('bin/keytool' + $binarySuffix)
$androidJar = Join-Path $AndroidPlatform 'android.jar'
$aapt = Join-Path $BuildTools ('aapt2' + $binarySuffix)
$zipalign = Join-Path $BuildTools ('zipalign' + $binarySuffix)
foreach ($path in @($java,$javac,$jar,$keytool,$androidJar,$aapt,$zipalign)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Build tool not found: $path. See README.md for parameters." }
}
function Run-Checked([string]$program, [string[]]$arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed ($LASTEXITCODE): $program" }
}
$build = Join-Path $project ('build\run-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss-fff'))
$classes = Join-Path $build 'classes'
$generated = Join-Path $build 'generated'
$dex = Join-Path $build 'dex'
$testClasses = Join-Path $build 'test-classes'
foreach ($path in @($build,$classes,$generated,$dex,$testClasses,(Join-Path $project 'dist'),(Join-Path $project '.signing'))) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
}
$source = Join-Path $project 'app\src\main'
Run-Checked $aapt @('compile','--dir',(Join-Path $source 'res'),'-o',(Join-Path $build 'resources.zip'))
Run-Checked $aapt @('link','-o',(Join-Path $build 'base.apk'),'-I',$androidJar,'--manifest',(Join-Path $source 'AndroidManifest.xml'),'--java',$generated,'--auto-add-overlay',(Join-Path $build 'resources.zip'))
$javaFiles = @(Get-ChildItem -LiteralPath (Join-Path $source 'java') -Recurse -Filter '*.java' | ForEach-Object FullName)
$javaFiles += @(Get-ChildItem -LiteralPath $generated -Recurse -Filter '*.java' | ForEach-Object FullName)
Run-Checked $javac (@('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',$classes) + $javaFiles)
Run-Checked $jar @("-J-Djava.io.tmpdir=$build",'cf',(Join-Path $build 'classes.jar'),'-C',$classes,'.')
Run-Checked $java @('-cp',(Join-Path $BuildTools 'lib\d8.jar'),'com.android.tools.r8.D8','--release','--min-api','26','--lib',$androidJar,'--output',$dex,(Join-Path $build 'classes.jar'))

# ZIP/JAR insertion must preserve the APK's binary AndroidManifest/resources.
$unsigned = Join-Path $build 'unsigned.apk'
Copy-Item -LiteralPath (Join-Path $build 'base.apk') -Destination $unsigned -Force
Run-Checked $jar @("-J-Djava.io.tmpdir=$build",'uf',$unsigned,'-C',$dex,'classes.dex')
$aligned = Join-Path $build 'aligned.apk'
Run-Checked $zipalign @('-f','-p','4',$unsigned,$aligned)
if (!$Keystore) { $Keystore = Join-Path $project '.signing\pocket-pulse-release.p12' }
if (!$SigningPasswordFile) { $SigningPasswordFile = Join-Path $project '.signing\signing-password.txt' }
if (!(Test-Path -LiteralPath $SigningPasswordFile)) {
    if (Test-Path -LiteralPath $Keystore) { throw 'Signing password file is missing. Restore it from your private backup.' }
    $random = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($random)
    $generator.Dispose()
    [System.IO.File]::WriteAllText($SigningPasswordFile, [Convert]::ToBase64String($random))
}
$previousSigningPassword = $env:POCKET_PULSE_SIGNING_PASSWORD
try {
    $env:POCKET_PULSE_SIGNING_PASSWORD = [System.IO.File]::ReadAllText($SigningPasswordFile).Trim()
    if (!(Test-Path -LiteralPath $Keystore)) {
        Run-Checked $keytool @('-genkeypair','-keystore',$Keystore,'-storetype','PKCS12','-storepass:env','POCKET_PULSE_SIGNING_PASSWORD','-keypass:env','POCKET_PULSE_SIGNING_PASSWORD','-alias','pocketpulse','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Pocket Pulse Release')
    }
    [xml]$manifest = Get-Content -LiteralPath (Join-Path $source 'AndroidManifest.xml')
    $version = $manifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid release version in AndroidManifest.xml' }
    $apkName = 'Pocket-Pulse-' + $version + '.apk'
    $apk = Join-Path $project ('dist\' + $apkName)
    Run-Checked $java @('-jar',(Join-Path $BuildTools 'lib\apksigner.jar'),'sign','--ks',$Keystore,'--ks-key-alias','pocketpulse','--ks-pass','env:POCKET_PULSE_SIGNING_PASSWORD','--out',$apk,$aligned)
} finally { $env:POCKET_PULSE_SIGNING_PASSWORD = $previousSigningPassword }
Run-Checked $java @('-jar',(Join-Path $BuildTools 'lib\apksigner.jar'),'verify','--verbose',$apk)
Run-Checked $zipalign @('-c','-p','4',$apk)

# Regression checks exercise counter math, resets, unsupported readings, and ring wrap.
Run-Checked $javac @('-encoding','UTF-8','-d',$testClasses,(Join-Path $source 'java\dev\pocketpulse\monitor\MetricMath.java'),(Join-Path $project 'tests\MetricMathTest.java'))
Run-Checked $java @('-cp',$testClasses,'dev.pocketpulse.monitor.MetricMathTest')
Run-Checked $javac @('-encoding','UTF-8','-d',$testClasses,(Join-Path $source 'java\dev\pocketpulse\monitor\ReleaseVersion.java'),(Join-Path $project 'tests\ReleaseVersionTest.java'))
Run-Checked $java @('-cp',$testClasses,'dev.pocketpulse.monitor.ReleaseVersionTest')
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText((Join-Path $project 'dist\SHA256SUMS.txt'), ($hash + '  ' + $apkName + [Environment]::NewLine))
Write-Output ('Built: ' + $apk)
Write-Output ('APK size: ' + (Get-Item -LiteralPath $apk).Length + ' bytes')
