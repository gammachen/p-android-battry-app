package com.batteryapp

import android.app.DownloadManager
import android.content.Context
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

/**
 * 系统信息Fragment，显示设备的系统信息、网络信息和存储信息
 */
class SystemInfoFragment : Fragment() {

    private lateinit var systemInfoContainer: ViewGroup
    private lateinit var networkInfoContainer: ViewGroup
    private lateinit var storageInfoContainer: ViewGroup

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
        
        // 加载系统信息
        loadSystemInfo()
        loadNetworkInfo()
        loadStorageInfo()
        
        return view
    }

    /**
     * 加载系统信息
     */
    private fun loadSystemInfo() {
        val systemInfo = mapOf(
            "设备" to getDeviceInfo(),
            "型号" to Build.MODEL,
            "产品" to Build.PRODUCT,
            "主板" to Build.BOARD,
            "厂商" to Build.MANUFACTURER,
            "品牌" to Build.BRAND,
            "操作系统" to "Android ${Build.VERSION.RELEASE} (${Build.VERSION.CODENAME})",
            "API 版本" to Build.VERSION.SDK_INT.toString(),
            "安全补丁级别" to Build.VERSION.SECURITY_PATCH,
            "编译版本" to Build.DISPLAY,
            "内核版本" to System.getProperty("os.version"),
            "架构" to System.getProperty("os.arch"),
            "CPU 架构" to Build.SUPPORTED_ABIS.joinToString(", "),
            "屏幕密度" to resources.displayMetrics.densityDpi.toString() + "dpi"
        )
        
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
            
            networkInfo["WiFi 名称"] = wifiInfo.ssid.replace("\"", "")
            networkInfo["WiFi IP"] = Formatter.formatIpAddress(wifiInfo.ipAddress)
            networkInfo["WiFi MAC"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wifiInfo.bssid ?: "未知"
            } else {
                wifiInfo.macAddress
            }
            networkInfo["WiFi 信号强度"] = wifiInfo.rssi.toString() + " dBm"
            networkInfo["WiFi 连接速度"] = wifiInfo.linkSpeed.toString() + " Mbps"
            
            // 移动数据信息 - 只获取不需要危险权限的信息
            val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            try {
                networkInfo["SIM 运营商"] = telephonyManager.networkOperatorName
            } catch (e: SecurityException) {
                // 忽略没有权限的情况
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
