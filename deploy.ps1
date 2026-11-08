$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$root = if ($PSScriptRoot) { $PSScriptRoot } else { Get-Location }

& $adb connect 192.168.3.32:5555
Start-Sleep -Seconds 1
& $adb install -r "$root\FusionLauncher.apk"
Start-Sleep -Seconds 1
& $adb shell "am start -n com.vios.apex/.MainActivity"
Write-Output "Deployed FusionLauncher successfully!"
