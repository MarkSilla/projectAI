$SdkRoot = "C:\Users\ADMIN\AppData\Local\Android\Sdk"
$CmdToolsDir = Join-Path $SdkRoot "cmdline-tools"
$LatestBin = Join-Path $CmdToolsDir "latest\bin\sdkmanager.bat"

New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null

if (-not (Test-Path $LatestBin)) {
    $zip = Join-Path $env:TEMP "android_cmdline_tools.zip"
    $extractRoot = Join-Path $SdkRoot "cmdline-tools-zip"
    Remove-Item -Recurse -Force $extractRoot -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null

    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $extractRoot -Force

    $sourceDir = Get-ChildItem $extractRoot -Recurse -Directory -Filter "cmdline-tools" | Select-Object -First 1 -ExpandProperty FullName
    if (-not $sourceDir) {
        throw "Downloaded Android command-line tools archive did not contain the expected cmdline-tools folder."
    }

    New-Item -ItemType Directory -Force -Path $CmdToolsDir | Out-Null
    $dest = Join-Path $CmdToolsDir "latest"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Get-ChildItem $sourceDir | Copy-Item -Destination $dest -Recurse -Force
}

$sdkmanager = Join-Path $CmdToolsDir "latest\bin\sdkmanager.bat"
if (Test-Path $sdkmanager) {
    echo y | & $sdkmanager --sdk_root=$SdkRoot --licenses
    & $sdkmanager --sdk_root=$SdkRoot "platform-tools" "platforms;android-35" "build-tools;35.0.0" "cmdline-tools;latest"
}

Set-Content -Path "C:\Users\ADMIN\Documents\projectAI\local.properties" -Value "sdk.dir=C:\\Users\\ADMIN\\AppData\\Local\\Android\\Sdk"
Write-Output "SDK installed at $SdkRoot"
