#!/system/bin/sh

# Set governor to ondemand (responsive dynamic scaling)
echo ondemand > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor

# Set frequency boundaries (480 MHz - 1.2 GHz)
echo 480000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq
echo 1200000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq

# Optimize ondemand governor for car head unit responsiveness & cooler temps
if [ -d /sys/devices/system/cpu/cpufreq/ondemand ]; then
    echo 1 > /sys/devices/system/cpu/cpufreq/ondemand/io_is_busy
    echo 100000 > /sys/devices/system/cpu/cpufreq/ondemand/sampling_rate
    echo 4 > /sys/devices/system/cpu/cpufreq/ondemand/sampling_down_factor
    echo 75 > /sys/devices/system/cpu/cpufreq/ondemand/up_threshold
fi

# Ensure persistence across reboots via Magisk service.d
mkdir -p /data/adb/service.d
cp /data/local/tmp/cpu_tweak.sh /data/adb/service.d/01_cpu_tweak.sh
chmod 755 /data/adb/service.d/01_cpu_tweak.sh

echo "ONDEMAND_OPTIMIZED_SUCCESS"
