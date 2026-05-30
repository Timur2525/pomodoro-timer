$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppRoot = Join-Path $ProjectRoot "android-native"
$MusicRoot = Join-Path $ProjectRoot "music"
$SdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "C:\tmp\android-sdk" }
$BuildTools = Join-Path $SdkRoot "build-tools\30.0.3"
$AndroidJar = Join-Path $SdkRoot "platforms\android-30\android.jar"
$Out = Join-Path $AppRoot "build"
$StagedRes = Join-Path $Out "res-staged"
$Gen = Join-Path $Out "gen"
$Classes = Join-Path $Out "classes"
$Dex = Join-Path $Out "dex"
$ResZip = Join-Path $Out "res.zip"
$UnsignedApk = Join-Path $Out "unsigned.apk"
$AlignedApk = Join-Path $Out "aligned.apk"
$FinalApk = Join-Path $Out "pomodoro-student.apk"
$KeyStore = Join-Path $AppRoot "debug.keystore"
$Javac = (Get-Command javac -ErrorAction SilentlyContinue).Source

if (!$Javac) {
    $AndroidJavac = "C:\Program Files\Android\jdk\jdk-8.0.302.8-hotspot\jdk8u302-b08\bin\javac.exe"
    if (Test-Path $AndroidJavac) {
        $Javac = $AndroidJavac
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string] $File,
        [string[]] $Arguments
    )

    & $File @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Команда завершилась с ошибкой ($LASTEXITCODE): $File $Arguments"
    }
}

if (!(Test-Path $AndroidJar)) {
    throw "Не найден Android SDK platform android-30: $AndroidJar"
}

if (!(Test-Path (Join-Path $BuildTools "aapt2.exe"))) {
    throw "Не найдены Android build-tools 30.0.3: $BuildTools"
}

if (!$Javac) {
    throw "Не найден javac.exe. Установите JDK или добавьте javac в PATH."
}

$JdkBin = Split-Path $Javac -Parent
$Java = Join-Path $JdkBin "java.exe"
$Jar = Join-Path $JdkBin "jar.exe"
$KeyTool = Join-Path $JdkBin "keytool.exe"
$D8Jar = Join-Path $BuildTools "lib\d8.jar"
$ApkSignerJar = Join-Path $BuildTools "lib\apksigner.jar"

if (!(Test-Path $Java)) {
    throw "Не найден java.exe рядом с javac: $Java"
}

if (!(Test-Path $Jar)) {
    throw "Не найден jar.exe рядом с javac: $Jar"
}

if (!(Test-Path $KeyTool)) {
    throw "Не найден keytool.exe рядом с javac: $KeyTool"
}

if (!(Test-Path $D8Jar)) {
    throw "Не найден d8.jar: $D8Jar"
}

if (!(Test-Path $ApkSignerJar)) {
    throw "Не найден apksigner.jar: $ApkSignerJar"
}

Remove-Item $Out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $Gen, $Classes, $Dex | Out-Null
New-Item -ItemType Directory -Force $StagedRes | Out-Null
Copy-Item (Join-Path $AppRoot "res\*") $StagedRes -Recurse
New-Item -ItemType Directory -Force (Join-Path $StagedRes "raw") | Out-Null

$MusicFile = Get-ChildItem -Path $MusicRoot -Filter "*.mp3" -File | Sort-Object Name | Select-Object -First 1

if (!$MusicFile) {
    throw "No MP3 file found in music folder: $MusicRoot"
}

Copy-Item $MusicFile.FullName (Join-Path $StagedRes "raw\music.mp3")
Write-Host "Music: $($MusicFile.Name)"

Invoke-Checked (Join-Path $BuildTools "aapt2.exe") @("compile", "--dir", $StagedRes, "-o", $ResZip)
Invoke-Checked (Join-Path $BuildTools "aapt2.exe") @(
    "link",
    "-o", $UnsignedApk,
    "-I", $AndroidJar,
    "--manifest", (Join-Path $AppRoot "AndroidManifest.xml"),
    "--java", $Gen,
    $ResZip
)

$JavaFiles = @(
    (Join-Path $AppRoot "src\com\student\pomodoro\MainActivity.java"),
    (Join-Path $Gen "com\student\pomodoro\R.java")
)
$JavacArgs = @("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-bootclasspath", $AndroidJar, "-d", $Classes) + $JavaFiles

Invoke-Checked $Javac $JavacArgs
$ClassFiles = Get-ChildItem -Path $Classes -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
$D8Args = @("-cp", $D8Jar, "com.android.tools.r8.D8", "--lib", $AndroidJar, "--output", $Dex) + $ClassFiles
Invoke-Checked $Java $D8Args
Invoke-Checked $Jar @("uf", $UnsignedApk, "-C", $Dex, "classes.dex")
Invoke-Checked (Join-Path $BuildTools "zipalign.exe") @("-p", "-f", "4", $UnsignedApk, $AlignedApk)

if (!(Test-Path $KeyStore)) {
    Invoke-Checked $KeyTool @(
        "-genkeypair",
        "-keystore", $KeyStore,
        "-storepass", "android",
        "-alias", "androiddebugkey",
        "-keypass", "android",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Student Pomodoro,O=Student,C=RU"
    )
}

Invoke-Checked $Java @(
    "-jar", $ApkSignerJar,
    "sign",
    "--ks", $KeyStore,
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    "--out", $FinalApk,
    $AlignedApk
)

Invoke-Checked $Java @("-jar", $ApkSignerJar, "verify", "--verbose", $FinalApk)

Write-Host "APK готов: $FinalApk"
