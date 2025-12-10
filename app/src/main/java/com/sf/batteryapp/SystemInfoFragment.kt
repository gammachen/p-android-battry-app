package com.sf.batteryapp

import android.app.DownloadManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.telephony.TelephonyManager
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import java.util.Date

/**
 * 系统信息Fragment，显示设备的系统信息、网络信息和存储信息
 */
class SystemInfoFragment : Fragment() {

    private lateinit var systemInfoContainer: ViewGroup
    private lateinit var networkInfoContainer: ViewGroup
    private lateinit var storageInfoContainer: ViewGroup
    private lateinit var cameraInfoContainer: ViewGroup
    private lateinit var sensorInfoContainer: ViewGroup
    private lateinit var screenInfoContainer: ViewGroup

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_system_info, container, false)
        
        // 初始化UI组件
        systemInfoContainer = view.findViewById(R.id.system_info_container)
        networkInfoContainer = view.findViewById(R.id.network_info_container)
        storageInfoContainer = view.findViewById(R.id.storage_info_container)
        cameraInfoContainer = view.findViewById(R.id.camera_info_container)
        sensorInfoContainer = view.findViewById(R.id.sensor_info_container)
        screenInfoContainer = view.findViewById(R.id.screen_info_container)
        
        // 加载系统信息
        loadSystemInfo()
        loadNetworkInfo()
        loadStorageInfo()
        loadCameraInfo()
        loadScreenInfo()
        loadSensorInfo()
        
        return view
    }

    /**
     * 加载系统信息
     */
    private fun loadSystemInfo() {
        val systemInfo = mutableMapOf<String, String>()
        
        // 设备信息
        systemInfo[getString(R.string.system_info_device)] = getDeviceInfo()
        systemInfo[getString(R.string.system_info_model)] = Build.MODEL
        systemInfo[getString(R.string.system_info_model_code)] = Build.DEVICE
        systemInfo[getString(R.string.system_info_product_code)] = Build.PRODUCT
        systemInfo[getString(R.string.system_info_device_code)] = Build.ID
        systemInfo[getString(R.string.system_info_board_code)] = Build.BOARD
        systemInfo[getString(R.string.system_info_manufacturer)] = Build.MANUFACTURER
        systemInfo[getString(R.string.system_info_brand)] = Build.BRAND
        
        // 操作系统信息
        systemInfo[getString(R.string.system_info_android_version)] = "Android ${Build.VERSION.RELEASE} (${Build.VERSION.CODENAME})"
        systemInfo[getString(R.string.system_info_api_level)] = Build.VERSION.SDK_INT.toString()
        systemInfo[getString(R.string.system_info_security_patch)] = Build.VERSION.SECURITY_PATCH
        systemInfo[getString(R.string.system_info_build_display)] = Build.DISPLAY
        systemInfo[getString(R.string.system_info_build_fingerprint)] = Build.FINGERPRINT
        systemInfo[getString(R.string.system_info_build_time)] = Date(Build.TIME).toString()
        systemInfo[getString(R.string.system_info_treble_support)] = getString(R.string.system_info_treble_supported) // 模拟数据
        systemInfo[getString(R.string.system_info_root_status)] = getString(R.string.system_info_root_not_obtained) // 简单判断，实际需要更复杂的检测
        
        // 其他系统信息
        systemInfo[getString(R.string.system_info_kernel_version)] = System.getProperty("os.version") ?: getString(R.string.system_info_unknown)
        systemInfo[getString(R.string.system_info_architecture)] = System.getProperty("os.arch") ?: getString(R.string.system_info_unknown)
        systemInfo[getString(R.string.system_info_cpu_abi)] = Build.SUPPORTED_ABIS.joinToString(", ")
        systemInfo[getString(R.string.system_info_screen_density)] = resources.displayMetrics.densityDpi.toString() + "dpi"
        
        addInfoToContainer(systemInfoContainer, systemInfo)
    }

    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /**
     * 加载网络信息
     */
    private fun loadNetworkInfo() {
        val networkInfo = mutableMapOf<String, String>()
        
        try {
            // WiFi信息
            val wifiManager = requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            
            networkInfo[getString(R.string.system_info_bssid)] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wifiInfo.bssid ?: getString(R.string.system_info_not_shown)
            } else {
                wifiInfo.macAddress ?: getString(R.string.system_info_not_shown)
            }
            networkInfo[getString(R.string.system_info_connection_speed)] = "${wifiInfo.linkSpeed} Mbps"
            networkInfo[getString(R.string.system_info_signal_strength)] = "${wifiInfo.rssi} dBm"
            
            // 频率和频段
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val frequency = wifiInfo.frequency
                networkInfo[getString(R.string.system_info_frequency)] = "$frequency MHz"
                networkInfo[getString(R.string.system_info_band)] = if (frequency in 2400..2483) "2.4 GHz" else "5 GHz"
            } else {
                networkInfo[getString(R.string.system_info_frequency)] = getString(R.string.system_info_not_shown)
                networkInfo[getString(R.string.system_info_band)] = getString(R.string.system_info_not_shown)
            }
            
            // IP地址
            networkInfo[getString(R.string.system_info_ipv4_address)] = Formatter.formatIpAddress(wifiInfo.ipAddress)
            
            // 移动数据和SIM卡信息 - 只获取不需要危险权限的信息
            val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            try {
                networkInfo[getString(R.string.system_info_sim_operator)] = telephonyManager.networkOperatorName
            } catch (e: SecurityException) {
                // 忽略没有权限的情况
                Log.e("SystemInfoFragment", "获取SIM卡信息失败: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取网络信息失败: ${e.message}")
        }
        
        addInfoToContainer(networkInfoContainer, networkInfo)
    }

    /**
     * 加载存储信息
     */
    private fun loadStorageInfo() {
        val storageInfo = mutableMapOf<String, String>()
        
        try {
            // 内部存储
            val internalStorage = Environment.getDataDirectory()
            val internalStats = getStorageStats(internalStorage)
            storageInfo[getString(R.string.system_info_internal_total)] = internalStats.first
            storageInfo[getString(R.string.system_info_internal_used)] = internalStats.second
            storageInfo[getString(R.string.system_info_internal_available)] = internalStats.third
            
            // 外部存储
            val externalStorage = Environment.getExternalStorageDirectory()
            val externalStats = getStorageStats(externalStorage)
            storageInfo[getString(R.string.system_info_external_total)] = externalStats.first
            storageInfo[getString(R.string.system_info_external_used)] = externalStats.second
            storageInfo[getString(R.string.system_info_external_available)] = externalStats.third
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取存储信息失败: ${e.message}")
        }
        
        addInfoToContainer(storageInfoContainer, storageInfo)
    }


    /**
     * 获取网络类型名称
     */
    private fun getNetworkType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO 0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO B"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "未知"
        }
    }

    /**
     * 获取存储统计信息
     */
    private fun getStorageStats(path: java.io.File): Triple<String, String, String> {
        val totalBytes = path.totalSpace
        val freeBytes = path.freeSpace
        val usedBytes = totalBytes - freeBytes
        
        return Triple(
            formatBytes(totalBytes),
            formatBytes(usedBytes),
            formatBytes(freeBytes)
        )
    }

    /**
     * 格式化字节大小
     */
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.2f %s", size, units[unitIndex])
    }

    /**
     * 加载相机信息
     */
    private fun loadCameraInfo() {
        val cameraInfo = mutableMapOf<String, String>()
        
        try {
            // 使用Camera2 API获取相机信息
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            
            // TODO: 替换为真实的Camera2 API调用获取相机信息
            /*
            // 前置摄像头信息
            cameraInfo[getString(R.string.system_info_front_camera)] = ""
            cameraInfo[getString(R.string.system_info_megapixels)] = "16.2 MP"
            cameraInfo[getString(R.string.system_info_effective_megapixels)] = "4 MP"
            cameraInfo[getString(R.string.system_info_resolution)] = "2320x1744"
            cameraInfo[getString(R.string.system_info_sensor_size)] = "1/2.8\""
            cameraInfo[getString(R.string.system_info_pixel_size)] = "4.64 x 3.49 mm"
            cameraInfo[getString(R.string.system_info_color_filter_array)] = "GBRG"
            cameraInfo[getString(R.string.system_info_aperture)] = "f/2.0"
            cameraInfo[getString(R.string.system_info_focal_length)] = "3.41 mm"
            cameraInfo[getString(R.string.system_info_35mm_equivalent)] = "25 mm"
            cameraInfo[getString(R.string.system_info_crop_factor)] = "7.5x"
            cameraInfo[getString(R.string.system_info_field_of_view)] = "68.5° 水平"
            cameraInfo[getString(R.string.system_info_shutter_speed)] = "1/50000 - 1/5 s"
            cameraInfo[getString(R.string.system_info_iso_range)] = "100 - 6400"
            cameraInfo[getString(R.string.system_info_flash)] = "×"
            cameraInfo[getString(R.string.system_info_video_stabilization)] = "✓"
            cameraInfo[getString(R.string.system_info_optical_stabilization)] = "×"
            cameraInfo[getString(R.string.system_info_ae_lock)] = "✓"
            cameraInfo[getString(R.string.system_info_wb_lock)] = "✓"
            cameraInfo[getString(R.string.system_info_features)] = "Manual sensor, RAW 模式, 连拍"
            cameraInfo[getString(R.string.system_info_exposure_modes)] = "Manual, External flash"
            cameraInfo[getString(R.string.system_info_autofocus_modes)] = "Manual"
            cameraInfo[getString(R.string.system_info_white_balance_modes)] = "Off, Auto, Incandescent, Fluorescent, Warm, Fluorescent, Daylight, Cloudy, Twilight, Shade"
            cameraInfo[getString(R.string.system_info_scene_modes)] = "Face priority, Action, Portrait, Landscape, Night, Night portrait, Theatre, Beach, Snow, Sunset, Steady, Fireworks, Sports, Party, Candlelight, Barcode, HDR"
            cameraInfo[getString(R.string.system_info_color_effects)] = "Off"
            cameraInfo[getString(R.string.system_info_max_face_count)] = "15"
            cameraInfo[getString(R.string.system_info_face_detection_mode)] = "simple"
            cameraInfo[getString(R.string.system_info_camera2_api_support)] = "Level 3"
            
            // 后置摄像头信息
            cameraInfo[getString(R.string.system_info_rear_camera)] = ""
            cameraInfo["${getString(R.string.system_info_megapixels)} (后)"] = "48.0 MP"
            cameraInfo["${getString(R.string.system_info_effective_megapixels)} (后)"] = "12.0 MP"
            cameraInfo["${getString(R.string.system_info_resolution)} (后)"] = "8000x6000"
            cameraInfo["${getString(R.string.system_info_sensor_size)} (后)"] = "1/1.56\""
            cameraInfo["${getString(R.string.system_info_pixel_size)} (后)"] = "0.80 μm"
            cameraInfo["${getString(R.string.system_info_color_filter_array)} (后)"] = "RGGB"
            cameraInfo["${getString(R.string.system_info_aperture)} (后)"] = "f/1.8"
            cameraInfo["${getString(R.string.system_info_focal_length)} (后)"] = "2.84 mm"
            cameraInfo["${getString(R.string.system_info_35mm_equivalent)} (后)"] = "26 mm"
            cameraInfo["${getString(R.string.system_info_crop_factor)} (后)"] = "6.5x"
            cameraInfo["${getString(R.string.system_info_field_of_view)} (后)"] = "83.5° 水平"
            cameraInfo["${getString(R.string.system_info_shutter_speed)} (后)"] = "1/80000 - 30 s"
            cameraInfo["${getString(R.string.system_info_iso_range)} (后)"] = "50 - 12800"
            cameraInfo["${getString(R.string.system_info_flash)} (后)"] = "✓"
            cameraInfo["${getString(R.string.system_info_video_stabilization)} (后)"] = "✓"
            cameraInfo["${getString(R.string.system_info_optical_stabilization)} (后)"] = "✓"
            cameraInfo["${getString(R.string.system_info_ae_lock)} (后)"] = "✓"
            cameraInfo["${getString(R.string.system_info_wb_lock)} (后)"] = "✓"
            cameraInfo["${getString(R.string.system_info_features)} (后)"] = "Manual sensor, RAW 模式, 连拍, 深度感知"
            */
            
            // 获取相机ID列表
            val cameraIds = cameraManager.cameraIdList
            
            for (cameraId in cameraIds) {
                // 获取相机设备特性
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                
                // 确定相机位置
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val isFrontCamera = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                val isRearCamera = lensFacing == CameraCharacteristics.LENS_FACING_BACK
                
                if (isFrontCamera || isRearCamera) {
                    val prefix = if (isFrontCamera) "" else " (后)"
                    val cameraLabel = if (isFrontCamera) getString(R.string.system_info_front_camera) else getString(R.string.system_info_rear_camera)
                    
                    // 添加相机标签
                    cameraInfo[cameraLabel] = ""
                    
                    // 获取传感器信息
                    val sensorInfo = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    
                    // 计算像素数 (MP)
                    if (pixelArraySize != null) {
                        val totalPixels = pixelArraySize.width * pixelArraySize.height
                        val megapixels = totalPixels.toDouble() / 1_000_000
                        cameraInfo["${getString(R.string.system_info_megapixels)}$prefix"] = String.format("%.1f MP", megapixels)
                    }
                    
                    // 分辨率
                    if (pixelArraySize != null) {
                        cameraInfo["${getString(R.string.system_info_resolution)}$prefix"] = "${pixelArraySize.width}x${pixelArraySize.height}"
                    }
                    
                    // 传感器尺寸
                    if (sensorInfo != null) {
                        cameraInfo["${getString(R.string.system_info_sensor_size)}$prefix"] = String.format("%.2f×%.2f mm", sensorInfo.width, sensorInfo.height)
                    }
                    
                    // 光圈大小
                    val aperture = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (aperture != null && aperture.isNotEmpty()) {
                        cameraInfo["${getString(R.string.system_info_aperture)}$prefix"] = String.format("f/%.1f", aperture[0])
                    }
                    
                    // 焦距
                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (focalLengths != null && focalLengths.isNotEmpty()) {
                        cameraInfo["${getString(R.string.system_info_focal_length)}$prefix"] = String.format("%.2f mm", focalLengths[0])
                    }
                    
                    // Camera2 API 支持级别
                    val camera2Level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) as Int?
                    val levelString = when (camera2Level) {
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                        else -> "Unknown"
                    }
                    cameraInfo["${getString(R.string.system_info_camera2_api_support)}$prefix"] = levelString
                    
                    // 视频防抖
                    val videoStabilizationModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                    val videoStabilization = videoStabilizationModes?.isNotEmpty() == true
                    cameraInfo["${getString(R.string.system_info_video_stabilization)}$prefix"] = if (videoStabilization) "✓" else "×"
                    
                    // 光学防抖
                    val opticalStabilizationModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    val opticalStabilization = opticalStabilizationModes?.isNotEmpty() == true
                    cameraInfo["${getString(R.string.system_info_optical_stabilization)}$prefix"] = if (opticalStabilization) "✓" else "×"
                }
            }
            
            // 如果没有获取到相机信息，显示提示
            if (cameraInfo.isEmpty()) {
                cameraInfo[getString(R.string.system_info_camera_info)] = getString(R.string.system_info_cannot_get_camera_info)
            }
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取相机信息失败: ${e.message}")
            cameraInfo[getString(R.string.system_info_camera_info)] = getString(R.string.system_info_cannot_get_camera_info)
        }
        
        addInfoToContainer(cameraInfoContainer, cameraInfo)
    }
    
    /**
     * 加载屏幕信息
     */
    private fun loadScreenInfo() {
        val screenInfo = mutableMapOf<String, String>()
        
        try {
            // 获取屏幕分辨率和相关信息
            val displayMetrics = resources.displayMetrics
            val widthPixels = displayMetrics.widthPixels
            val heightPixels = displayMetrics.heightPixels
            val density = displayMetrics.density
            val xdpi = displayMetrics.xdpi
            val ydpi = displayMetrics.ydpi
            
            // 分辨率
            screenInfo[getString(R.string.system_info_screen_resolution)] = "${widthPixels}x${heightPixels}"
            
            // 计算宽高比
            val aspectRatio = calculateAspectRatio(widthPixels, heightPixels)
            screenInfo[getString(R.string.system_info_aspect_ratio)] = aspectRatio
            
            // 计算屏幕尺寸（英寸）
            val screenSize = calculateScreenSize(widthPixels, heightPixels, xdpi, ydpi)
            screenInfo[getString(R.string.system_info_screen_size)] = "${String.format("%.1f", screenSize)} 英寸"
            
            // 屏幕密度
            screenInfo[getString(R.string.system_info_screen_density)] = "${displayMetrics.densityDpi} dpi"
            
            // 获取GPU信息
            val gpuInfo = getGpuInfo()
            screenInfo[getString(R.string.system_info_gpu)] = gpuInfo
            
            // 获取屏幕刷新率
            val refreshRate = getRefreshRate()
            screenInfo[getString(R.string.system_info_refresh_rate)] = "${refreshRate} Hz"
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取屏幕信息失败: ${e.message}")
            screenInfo[getString(R.string.system_info_screen_info)] = getString(R.string.system_info_cannot_get_screen_info)
        }
        
        addInfoToContainer(screenInfoContainer, screenInfo)
    }
    
    /**
     * 获取屏幕刷新率
     */
    private fun getRefreshRate(): Int {
        try {
            // 使用Display API获取刷新率
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requireContext().display
            } else {
                @Suppress("DEPRECATION")
                requireActivity().windowManager.defaultDisplay
            }
            
            return if (display != null) {
                val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display.refreshRate
                } else {
                    @Suppress("DEPRECATION")
                    display.refreshRate
                }
                refreshRate.toInt()
            } else {
                60 // 默认值
            }
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取刷新率失败: ${e.message}")
            return 60 // 默认值
        }
    }
    
    /**
     * 计算宽高比
     */
    private fun calculateAspectRatio(width: Int, height: Int): String {
        val gcd = greatestCommonDivisor(width, height)
        val aspectWidth = width / gcd
        val aspectHeight = height / gcd
        return "${aspectWidth}:${aspectHeight}"
    }
    
    /**
     * 计算最大公约数
     */
    private fun greatestCommonDivisor(a: Int, b: Int): Int {
        var tempA = a
        var tempB = b
        while (tempB != 0) {
            val temp = tempB
            tempB = tempA % tempB
            tempA = temp
        }
        return tempA
    }
    
    /**
     * 计算屏幕尺寸（英寸）
     */
    private fun calculateScreenSize(widthPixels: Int, heightPixels: Int, xdpi: Float, ydpi: Float): Double {
        val widthInches = widthPixels / xdpi.toDouble()
        val heightInches = heightPixels / ydpi.toDouble()
        return Math.sqrt(widthInches * widthInches + heightInches * heightInches)
    }
    
    /**
     * 获取GPU信息
     */
    private fun getGpuInfo(): String {
        // 使用系统属性获取GPU信息
        val glVendor = System.getProperty("ro.opengles.version") ?: "未知"
        val gpuModel = System.getProperty("ro.hardware") ?: "未知"
        
        return "${glVendor} - ${gpuModel}"
    }
    
    /**
     * 加载传感器信息
     */
    private fun loadSensorInfo() {
        val sensorInfo = mutableMapOf<String, String>()
        
        try {
            // 使用SensorManager获取传感器信息
            val sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            
            sensorInfo[getString(R.string.system_info_sensor_count)] = "${sensors.size}"
            
            // 遍历所有传感器，获取详细信息
            sensors.forEachIndexed { index, sensor ->
                val sensorType = when (sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> getString(R.string.system_info_sensor_type_accelerometer)
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> getString(R.string.system_info_sensor_type_temperature)
                    Sensor.TYPE_GYROSCOPE -> getString(R.string.system_info_sensor_type_gyroscope)
                    Sensor.TYPE_LIGHT -> getString(R.string.system_info_sensor_type_light)
                    Sensor.TYPE_MAGNETIC_FIELD -> getString(R.string.system_info_sensor_type_magnetic_field)
                    Sensor.TYPE_PRESSURE -> getString(R.string.system_info_sensor_type_pressure)
                    Sensor.TYPE_PROXIMITY -> getString(R.string.system_info_sensor_type_proximity)
                    Sensor.TYPE_RELATIVE_HUMIDITY -> getString(R.string.system_info_sensor_type_humidity)
                    Sensor.TYPE_STEP_COUNTER -> getString(R.string.system_info_sensor_type_step_counter)
                    Sensor.TYPE_STEP_DETECTOR -> getString(R.string.system_info_sensor_type_step_detector)
                    Sensor.TYPE_GRAVITY -> getString(R.string.system_info_sensor_type_gravity)
                    Sensor.TYPE_LINEAR_ACCELERATION -> getString(R.string.system_info_sensor_type_linear_acceleration)
                    Sensor.TYPE_ROTATION_VECTOR -> getString(R.string.system_info_sensor_type_rotation_vector)
                    Sensor.TYPE_HEART_RATE -> getString(R.string.system_info_sensor_type_heart_rate)
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> getString(R.string.system_info_sensor_type_game_rotation_vector)
                    Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> getString(R.string.system_info_sensor_type_gyroscope_uncalibrated)
                    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> getString(R.string.system_info_sensor_type_magnetic_field_uncalibrated)
                    Sensor.TYPE_SIGNIFICANT_MOTION -> getString(R.string.system_info_sensor_type_significant_motion)
                    Sensor.TYPE_STATIONARY_DETECT -> getString(R.string.system_info_sensor_type_stationary_detect)
                    Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> getString(R.string.system_info_sensor_type_accelerometer_uncalibrated)
                    Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> getString(R.string.system_info_sensor_type_low_latency_offbody_detect)
                    Sensor.TYPE_POSE_6DOF -> getString(R.string.system_info_sensor_type_pose_6dof)
                    Sensor.TYPE_HEART_BEAT -> getString(R.string.system_info_sensor_type_heart_beat)
                    Sensor.TYPE_HINGE_ANGLE -> getString(R.string.system_info_sensor_type_hinge_angle)
                    else -> getString(R.string.system_info_sensor_type_unknown)
                }
                
                // 添加传感器详细信息
                sensorInfo["${getString(R.string.system_info_sensor)} ${index + 1}"] = sensor.name
                sensorInfo["${getString(R.string.system_info_sensor_type)} ${index + 1}"] = sensorType
                sensorInfo["${getString(R.string.system_info_sensor_manufacturer)} ${index + 1}"] = sensor.vendor
                sensorInfo["${getString(R.string.system_info_sensor_resolution)} ${index + 1}"] = sensor.resolution.toString()
                sensorInfo["${getString(R.string.system_info_sensor_max_range)} ${index + 1}"] = sensor.maximumRange.toString()
                sensorInfo["${getString(R.string.system_info_sensor_power)} ${index + 1}"] = sensor.power.toString() + " mA"
                sensorInfo["${getString(R.string.system_info_sensor_min_delay)} ${index + 1}"] = sensor.minDelay.toString() + " μs"
                sensorInfo["${getString(R.string.system_info_sensor_version)} ${index + 1}"] = sensor.version.toString()
                sensorInfo[""] = ""
            }
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取传感器信息失败: ${e.message}")
            sensorInfo[getString(R.string.system_info_sensor_info)] = getString(R.string.system_info_cannot_get_sensor_info)
        }
        
        addInfoToContainer(sensorInfoContainer, sensorInfo)
    }
    
    /**
     * 将信息添加到容器中
     */
    private fun addInfoToContainer(container: ViewGroup, infoMap: Map<String, String>) {
        val inflater = LayoutInflater.from(requireContext())
        
        for ((key, value) in infoMap) {
            val infoView = inflater.inflate(R.layout.item_info_row, container, false)
            
            val keyTextView = infoView.findViewById<TextView>(R.id.info_key)
            val valueTextView = infoView.findViewById<TextView>(R.id.info_value)
            
            keyTextView.text = key
            valueTextView.text = value
            
            container.addView(infoView)
        }
    }
}
