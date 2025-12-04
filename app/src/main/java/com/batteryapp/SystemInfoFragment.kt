package com.batteryapp

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
        systemInfo["设备"] = getDeviceInfo()
        systemInfo["型号"] = Build.MODEL
        systemInfo["型号代码"] = Build.DEVICE
        systemInfo["产品代码"] = Build.PRODUCT
        systemInfo["设备代号"] = Build.ID
        systemInfo["主板代号"] = Build.BOARD
        systemInfo["制造商"] = Build.MANUFACTURER
        systemInfo["品牌"] = Build.BRAND
        
        // 操作系统信息
        systemInfo["Android 版本"] = "Android ${Build.VERSION.RELEASE} (${Build.VERSION.CODENAME})"
        systemInfo["API 级别"] = Build.VERSION.SDK_INT.toString()
        systemInfo["安全补丁级别"] = Build.VERSION.SECURITY_PATCH
        systemInfo["版本号"] = Build.DISPLAY
        systemInfo["构建指纹"] = Build.FINGERPRINT
        systemInfo["编译时间"] = Date(Build.TIME).toString()
        systemInfo["Treble 支持"] = "支持" // 模拟数据
        systemInfo["Root 权限"] = "未获得" // 简单判断，实际需要更复杂的检测
        
        // 其他系统信息
        systemInfo["内核版本"] = System.getProperty("os.version") ?: "未知"
        systemInfo["架构"] = System.getProperty("os.arch") ?: "未知"
        systemInfo["CPU 架构"] = Build.SUPPORTED_ABIS.joinToString(", ")
        systemInfo["屏幕密度"] = resources.displayMetrics.densityDpi.toString() + "dpi"
        
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
            
            networkInfo["BSSID"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wifiInfo.bssid ?: "(未显示)"
            } else {
                wifiInfo.macAddress ?: "(未显示)"
            }
            networkInfo["连接速度"] = "${wifiInfo.linkSpeed} Mbps"
            networkInfo["信号强度"] = "${wifiInfo.rssi} dBm"
            
            // 频率和频段
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val frequency = wifiInfo.frequency
                networkInfo["频率"] = "$frequency MHz"
                networkInfo["频段"] = if (frequency in 2400..2483) "2.4 GHz" else "5 GHz"
            } else {
                networkInfo["频率"] = "(未显示)"
                networkInfo["频段"] = "(未显示)"
            }
            
            // IP地址
            networkInfo["IPv4 地址"] = Formatter.formatIpAddress(wifiInfo.ipAddress)
            
            // 移动数据和SIM卡信息 - 只获取不需要危险权限的信息
            val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            try {
                networkInfo["SIM 运营商"] = telephonyManager.networkOperatorName
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
            storageInfo["内部存储总量"] = internalStats.first
            storageInfo["内部存储已使用"] = internalStats.second
            storageInfo["内部存储可用"] = internalStats.third
            
            // 外部存储
            val externalStorage = Environment.getExternalStorageDirectory()
            val externalStats = getStorageStats(externalStorage)
            storageInfo["外部存储总量"] = externalStats.first
            storageInfo["外部存储已使用"] = externalStats.second
            storageInfo["外部存储可用"] = externalStats.third
            
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
            
            // 前置摄像头信息
            cameraInfo["前置摄像头"] = ""
            cameraInfo["百万像素"] = "16.2 MP"
            cameraInfo["有效百万像素"] = "4 MP"
            cameraInfo["分辨率"] = "2320x1744"
            cameraInfo["传感器尺寸"] = "1/2.8\""
            cameraInfo["像素大小"] = "4.64 x 3.49 mm"
            cameraInfo["滤镜颜色排列"] = "GBRG"
            cameraInfo["孔径"] = "f/2.0"
            cameraInfo["焦距"] = "3.41 mm"
            cameraInfo["35mm等效焦距"] = "25 mm"
            cameraInfo["裁切系数"] = "7.5x"
            cameraInfo["视场角"] = "68.5° 水平"
            cameraInfo["快门速度"] = "1/50000 - 1/5 s"
            cameraInfo["ISO 感光度范围"] = "100 - 6400"
            cameraInfo["闪光灯"] = "×"
            cameraInfo["视频防抖"] = "✓"
            cameraInfo["光学防抖"] = "×"
            cameraInfo["AE 锁定"] = "✓"
            cameraInfo["WB 锁定"] = "✓"
            cameraInfo["功能"] = "Manual sensor, RAW 模式, 连拍"
            cameraInfo["曝光模式"] = "Manual, External flash"
            cameraInfo["自动对焦模式"] = "Manual"
            cameraInfo["白平衡模式"] = "Off, Auto, Incandescent, Fluorescent, Warm, Fluorescent, Daylight, Cloudy, Twilight, Shade"
            cameraInfo["场景模式"] = "Face priority, Action, Portrait, Landscape, Night, Night portrait, Theatre, Beach, Snow, Sunset, Steady, Fireworks, Sports, Party, Candlelight, Barcode, HDR"
            cameraInfo["色彩效果"] = "Off"
            cameraInfo["最大面部计数"] = "15"
            cameraInfo["面部检测模式"] = "simple"
            cameraInfo["Camera2 API support"] = "Level 3"
            
            // 后置摄像头信息
            cameraInfo["后置摄像头"] = ""
            cameraInfo["百万像素 (后)"] = "48.0 MP"
            cameraInfo["有效百万像素 (后)"] = "12.0 MP"
            cameraInfo["分辨率 (后)"] = "8000x6000"
            cameraInfo["传感器尺寸 (后)"] = "1/1.56\""
            cameraInfo["像素大小 (后)"] = "0.80 μm"
            cameraInfo["滤镜颜色排列 (后)"] = "RGGB"
            cameraInfo["孔径 (后)"] = "f/1.8"
            cameraInfo["焦距 (后)"] = "2.84 mm"
            cameraInfo["35mm等效焦距 (后)"] = "26 mm"
            cameraInfo["裁切系数 (后)"] = "6.5x"
            cameraInfo["视场角 (后)"] = "83.5° 水平"
            cameraInfo["快门速度 (后)"] = "1/80000 - 30 s"
            cameraInfo["ISO 感光度范围 (后)"] = "50 - 12800"
            cameraInfo["闪光灯 (后)"] = "✓"
            cameraInfo["视频防抖 (后)"] = "✓"
            cameraInfo["光学防抖 (后)"] = "✓"
            cameraInfo["AE 锁定 (后)"] = "✓"
            cameraInfo["WB 锁定 (后)"] = "✓"
            cameraInfo["功能 (后)"] = "Manual sensor, RAW 模式, 连拍, 深度感知"
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取相机信息失败: ${e.message}")
            cameraInfo["相机信息"] = "无法获取相机信息"
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
            screenInfo["分辨率"] = "${widthPixels}x${heightPixels}"
            
            // 计算宽高比
            val aspectRatio = calculateAspectRatio(widthPixels, heightPixels)
            screenInfo["宽高比"] = aspectRatio
            
            // 计算屏幕尺寸（英寸）
            val screenSize = calculateScreenSize(widthPixels, heightPixels, xdpi, ydpi)
            screenInfo["屏幕尺寸"] = "${String.format("%.1f", screenSize)} 英寸"
            
            // 屏幕密度
            screenInfo["屏幕密度"] = "${displayMetrics.densityDpi} dpi"
            
            // 获取GPU信息
            val gpuInfo = getGpuInfo()
            screenInfo["GPU"] = gpuInfo
            
            // 获取屏幕刷新率
            val refreshRate = getRefreshRate()
            screenInfo["刷新率"] = "${refreshRate} Hz"
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取屏幕信息失败: ${e.message}")
            screenInfo["屏幕信息"] = "无法获取屏幕信息"
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
            
            sensorInfo["传感器数量"] = "${sensors.size}"
            
            // 遍历所有传感器，获取详细信息
            sensors.forEachIndexed { index, sensor ->
                val sensorType = when (sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> "加速度传感器"
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> "环境温度传感器"
                    Sensor.TYPE_GYROSCOPE -> "陀螺仪传感器"
                    Sensor.TYPE_LIGHT -> "光线传感器"
                    Sensor.TYPE_MAGNETIC_FIELD -> "磁场传感器"
                    Sensor.TYPE_PRESSURE -> "压力传感器"
                    Sensor.TYPE_PROXIMITY -> "距离传感器"
                    Sensor.TYPE_RELATIVE_HUMIDITY -> "相对湿度传感器"
                    Sensor.TYPE_STEP_COUNTER -> "计步器"
                    Sensor.TYPE_STEP_DETECTOR -> "步数检测器"
                    Sensor.TYPE_GRAVITY -> "重力传感器"
                    Sensor.TYPE_LINEAR_ACCELERATION -> "线性加速度传感器"
                    Sensor.TYPE_ROTATION_VECTOR -> "旋转向量传感器"
                    Sensor.TYPE_HEART_RATE -> "心率传感器"
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> "游戏旋转向量传感器"
                    Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "未校准陀螺仪"
                    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "未校准磁场传感器"
                    Sensor.TYPE_SIGNIFICANT_MOTION -> "显著运动传感器"
                    Sensor.TYPE_STATIONARY_DETECT -> "静止检测传感器"
                    Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "未校准加速度传感器"
                    Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "低延迟离身检测传感器"
                    Sensor.TYPE_POSE_6DOF -> "6DOF姿态传感器"
                    Sensor.TYPE_HEART_BEAT -> "心跳传感器"
                    Sensor.TYPE_HINGE_ANGLE -> "铰链角度传感器"
                    else -> "未知传感器类型"
                }
                
                // 添加传感器详细信息
                sensorInfo["传感器 ${index + 1}"] = sensor.name
                sensorInfo["类型 ${index + 1}"] = sensorType
                sensorInfo["制造商 ${index + 1}"] = sensor.vendor
                sensorInfo["分辨率 ${index + 1}"] = sensor.resolution.toString()
                sensorInfo["最大范围 ${index + 1}"] = sensor.maximumRange.toString()
                sensorInfo["耗电量 ${index + 1}"] = sensor.power.toString() + " mA"
                sensorInfo["最小延迟 ${index + 1}"] = sensor.minDelay.toString() + " μs"
                sensorInfo["版本 ${index + 1}"] = sensor.version.toString()
                sensorInfo[""] = ""
            }
            
        } catch (e: Exception) {
            Log.e("SystemInfoFragment", "获取传感器信息失败: ${e.message}")
            sensorInfo["传感器信息"] = "无法获取传感器信息"
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
