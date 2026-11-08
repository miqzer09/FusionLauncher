#!/system/bin/sh

echo "=== STARTING SYSTEM DEBLOAT ==="

# 1. Remount system as read-write
mount -o remount,rw /system

# 2. Remove Adups FOTA Telemetry / Spyware
echo "Removing Adups FOTA..."
pm uninstall com.abupdate.fota_demo_iot 2>/dev/null
rm -rf /system/app/FOTA_CarWithIcon
rm -rf /data/data/com.abupdate.fota_demo_iot
rm -rf /sdcard/Android/data/com.abupdate.fota_demo_iot

# 3. Remove Dofun Bloatware (Store and Weather)
echo "Removing Dofun Market & Weather..."
pm uninstall com.dofun.market 2>/dev/null
rm -rf /data/data/com.dofun.market
pm uninstall com.dofun.dofunweather.main 2>/dev/null
rm -rf /data/data/com.dofun.dofunweather.main

# 4. Remove Print Services (Not used in car)
echo "Removing Print Services..."
pm uninstall com.android.printspooler 2>/dev/null
rm -rf /system/app/PrintSpooler
pm uninstall com.android.printservice.recommendation 2>/dev/null
rm -rf /system/app/PrintRecommendationService

# 5. Remove Live Wallpapers & Screensavers
echo "Removing Live Wallpapers & Basic Dreams..."
pm uninstall com.android.wallpaper.livepicker 2>/dev/null
rm -rf /system/app/LiveWallpapersPicker
pm uninstall com.android.dreams.basic 2>/dev/null
rm -rf /system/app/BasicDreams

# 6. Remove Bluetooth MIDI (Not used in car)
echo "Removing Bluetooth MIDI..."
pm uninstall com.android.bluetoothmidiservice 2>/dev/null
rm -rf /system/app/BluetoothMidiService

# 7. Remove Bookmark Provider & Emergency Info
echo "Removing Bookmarks & Emergency Info..."
pm uninstall com.android.bookmarkprovider 2>/dev/null
rm -rf /system/app/BookmarkProvider
pm uninstall com.android.emergency 2>/dev/null
rm -rf /system/priv-app/EmergencyInfo

# 8. Remove temporary exploit staging app
echo "Removing exploit helper..."
pm uninstall com.zjinnova.zlink 2>/dev/null
rm -rf /data/data/com.zjinnova.zlink

# Sync and lock filesystem
sync
mount -o remount,ro /system

echo "=== DEBLOAT COMPLETE ==="
