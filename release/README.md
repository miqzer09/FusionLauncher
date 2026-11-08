# FusionLauncher Release Package & Firmware Assets
**Toyota Vios NCP150 (Allwinner T3 / Topway Core)**

This release package contains the complete production binaries, companion apps, firmware partitions, and optimization scripts for the FusionLauncher custom head unit system.

---

## 📦 Directory Structure & Deliverables

### 🚀 Primary Releases (`release/`)
| File | Description | Size |
| :--- | :--- | :--- |
| **`FusionLauncher.apk`** | Latest production launcher APK (Ember & Black theme, Audi Dual-Wing HUD, zero-allocation 60 FPS, brand-authentic vector icons, clean-up manager). | ~62 KB |
| **`Radio_Modded.apk`** | Decompiled, patched, and resigned Topway FM Radio app with broadcast hooks and station metadata sharing. | ~1.2 MB |
| **`Magisk-v27.0.apk`** | Official Magisk Root Manager app for managing root privileges and modules. | ~12.5 MB |
| **`magisk_patched_boot.img`** | Pre-patched boot partition providing permanent systemless root on Allwinner T3 head units. | 33.5 MB |
| **`boot_backup.img`** | Factory stock boot partition backup for instant emergency recovery. | 33.5 MB |
| **`recovery_backup.img`** | Factory stock recovery partition backup. | 33.5 MB |

---

### ⚙️ Performance & Optimization Scripts (`release/scripts/`)
| Script | Purpose |
| :--- | :--- |
| **`cpu_tweak.sh`** | Sets CPU governor to `interactive`, locks minimum frequencies to eliminate audio stuttering, and optimizes I/O scheduler. |
| **`debloat.sh`** | Safely disables resource-heavy telemetry and background bloatware (`com.dofun.variety`, etc.), freeing over 120MB RAM. |
| **`boot_patch.sh`** | Shell script utilizing `magiskboot` to extract, patch, and repack Allwinner boot images. |

---

### 📱 Factory Stock Apps Backup (`release/stock_apps/`)
Original pre-installed Topway/Allwinner system APKs extracted directly from the head unit firmware:
- **`CarChoose.apk`**: Factory vehicle model configuration and steering wheel controls setup.
- **`com.tw.core.apk`**: Topway hardware framework service communicating with CAN bus and MCU.
- **`com.tw.radio.apk`**: Original stock radio application.
- **`dofun.apk`**: Factory stock Variety launcher.
- **`Settings.apk`**: Original Android car settings application.
- **`TDService.apk`**: Topway device background helper service.
- **`TWFileExplore.apk`**: Stock automotive file manager.

---

## 🛠️ Quick Installation

### Install FusionLauncher via ADB:
```bash
adb connect <head_unit_ip>:5555
adb install -r FusionLauncher.apk
adb shell "am start -n com.vios.apex/.MainActivity"
```

### Flash Rooted Boot Image (Fastboot / Root DD):
```bash
adb push magisk_patched_boot.img /sdcard/
adb shell "su -c 'dd if=/sdcard/magisk_patched_boot.img of=/dev/block/by-name/boot'"
adb reboot
```
