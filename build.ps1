$ErrorActionPreference = "Stop"
$root = if ($PSScriptRoot) { $PSScriptRoot } else { Get-Location }
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt = "$sdk\build-tools\36.0.0"
$jar = "$sdk\platforms\android-35\android.jar"
Set-Location $root
Remove-Item obj, out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory obj, out | Out-Null

& "$bt\aapt2.exe" link -o out\base.apk -I $jar --manifest AndroidManifest.xml --min-sdk-version 24 --target-sdk-version 27 --version-code 1 --version-name 1.0
if ($LASTEXITCODE) { throw "aapt2 failed" }

$srcs = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
& javac -source 8 -target 8 -Xlint:-options -bootclasspath $jar -d obj $srcs
if ($LASTEXITCODE) { throw "javac failed" }

$classes = Get-ChildItem obj -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --min-api 24 --lib $jar --output out $classes
if ($LASTEXITCODE) { throw "d8 failed" }

# add classes.dex into apk
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$root\out\base.apk", "Update")
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$root\out\classes.dex", "classes.dex") | Out-Null
$zip.Dispose()

& "$bt\zipalign.exe" -f -p 4 out\base.apk out\aligned.apk
if (-not (Test-Path "$root\apex.keystore")) {
    & keytool -genkeypair -keystore "$root\apex.keystore" -storepass apex1234 -keypass apex1234 -alias apex -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Apex Launcher"
}
& "$bt\apksigner.bat" sign --ks "$root\apex.keystore" --ks-pass pass:apex1234 --key-pass pass:apex1234 --out "$root\FusionLauncher.apk" out\aligned.apk
if ($LASTEXITCODE) { throw "sign failed" }

Copy-Item "$root\FusionLauncher.apk" "$root\FusionAmber.apk" -Force
New-Item -ItemType Directory "$root\release" -Force -ErrorAction SilentlyContinue | Out-Null
Copy-Item "$root\FusionLauncher.apk" "$root\release\FusionLauncher.apk" -Force
Remove-Item obj, out -Recurse -Force -ErrorAction SilentlyContinue

Get-Item "$root\FusionLauncher.apk" | Select-Object Name, Length
