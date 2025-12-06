package com.batteryapp

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.batteryapp.data.BatteryRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var btnSystemAccelerate: Button
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 设置Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 初始化系统加速按钮
        btnSystemAccelerate = findViewById(R.id.btn_system_accelerate)
        btnSystemAccelerate.setOnClickListener {
            handleSystemAccelerate()
        }

        // 检查并请求UsageStats权限
        checkUsageStatsPermission()
        // 检查NetworkStats API是否可用
        checkNetworkStatsPermission()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_charging -> {
                    toolbar.title = "实时监控"
                    currentFragment = ChargingFragment()
                    replaceFragment(currentFragment!!)
                    true
                }
                R.id.nav_battery_usage -> {
                    toolbar.title = "耗电排行"
                    // 跳转到耗电排行前再次检查权限
                    if (checkUsageStatsPermission()) {
                        // 检查NetworkStats API是否可用
                        val isNetworkStatsAvailable = checkNetworkStatsPermission()
                        if (!isNetworkStatsAvailable) {
                            // TrafficStats API不可用，显示提示信息
                            Log.w(TAG, "无法获取应用网络流量数据，预估电量可能不准确")
                        }
                        currentFragment = BatteryUsageFragment()
                        replaceFragment(currentFragment!!)
                    } else {
                        requestUsageStatsPermission()
                    }
                    true
                }
                R.id.nav_health -> {
                    toolbar.title = "健康评估"
                    currentFragment = HealthFragment()
                    replaceFragment(currentFragment!!)
                    true
                }
                R.id.nav_system_info -> {
                    toolbar.title = "系统信息"
                    currentFragment = SystemInfoFragment()
                    replaceFragment(currentFragment!!)
                    true
                }
                else -> false
            }
        }

        // 默认显示实时监控页面
        toolbar.title = "实时监控"
        currentFragment = ChargingFragment()
        replaceFragment(currentFragment!!)
    }

    /**
     * 处理系统加速按钮点击事件
     */
    private fun handleSystemAccelerate() {
        // 只有在耗电排行页面才显示加速功能
        if (currentFragment is BatteryUsageFragment) {
            // 检查是否有 PACKAGE_USAGE_STATS 权限
            if (!hasPermission()) {
                // 没有权限，引导用户去设置中授予
                requestUsageStatsPermission()
                return
            }
            
            // 初始化 ActivityManager
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            
            // 在协程中调用suspend函数
            lifecycleScope.launch {
                try {
                    // 获取BatteryRepository实例
                    val batteryRepository = BatteryRepository(applicationContext)
                    
                    // 获取耗电量最多的前10个应用
                    val topApps = batteryRepository.getAppBatteryUsageRanking(
                        BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION
                    )
                    // .take(10)
                    
                    Log.d(TAG, "当前应用：${packageName}，获取到的耗电量最多的应用数量: ${topApps.size} ${topApps.joinToString { it.packageName }}")
                    
                    // 过滤掉系统应用和当前应用（TODO：只是简单的使用packageName判断，后续可以考虑使用更完善的方法）
                    val userApps = topApps.filter {
                        it.packageName != packageName &&
                        !it.packageName.startsWith("system") &&
                        !it.packageName.startsWith("com.android")
                    }
                    
                    Log.d(TAG, "过滤后可处理的应用数量: ${userApps.size}")
                    
                    Log.d(TAG, "前10个耗电量最多的应用: ${userApps.joinToString { it.packageName }}")
                
                    // 获取所有运行的进程并打印日志，方便调试
                    val runningProcesses = activityManager.runningAppProcesses
                    Log.d(TAG, "获取到的运行进程数量: ${runningProcesses.size}")
                    runningProcesses.forEach { process ->
                        Log.d(TAG, "运行进程: ${process.processName}, 包名列表: ${process.pkgList.joinToString()}")
                    }
                    
                    // 获取这些应用的内存使用情况
                    val appMemoryMap = mutableMapOf<String, Int>()
                    
                    userApps.forEach { appBatteryUsage ->
                        try {
                            Log.d(TAG, "正在处理应用: ${appBatteryUsage.packageName}")

                            // 查找该应用的进程，使用更灵活的匹配方式
                            val appProcesses = runningProcesses.filter {
                                process -> 
                                // 精确匹配或前缀匹配（处理多进程应用）
                                process.pkgList.contains(appBatteryUsage.packageName) ||
                                process.processName == appBatteryUsage.packageName ||
                                process.processName.startsWith("${appBatteryUsage.packageName}:")
                            }

                            Log.d(TAG, "该应用进程数量: ${appProcesses.size}")
                            
                            // 计算该应用的总内存消耗
                            var totalPss = 0
                            appProcesses.forEach {
                                try {
                                    val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(it.pid))
                                    
                                    if (memoryInfoArray.isNotEmpty()) {
                                        totalPss += memoryInfoArray[0].totalPss
                                        Log.d(TAG, "进程: ${it.processName}, PID: ${it.pid}, 内存: ${memoryInfoArray[0].totalPss}KB")
                                    } else {
                                        Log.w(TAG, "获取进程内存信息为空: ${it.pid} ${it.processName}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "获取进程内存失败: ${it.processName}, PID: ${it.pid}, ${e.message}")
                                }
                            }
                            
                            if (totalPss > 0) {
                                appMemoryMap[appBatteryUsage.packageName] = totalPss
                                Log.d(TAG, "应用: ${appBatteryUsage.packageName}, 总PSS内存: ${totalPss}KB")
                            } else {
                                Log.w(TAG, "应用: ${appBatteryUsage.packageName}, 总PSS内存为0KB或无法获取")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "处理应用内存失败: ${appBatteryUsage.packageName}, ${e.message}")
                        }
                    }
                    
                    // 确定最终要结束的应用列表
                    val finalApps: List<Pair<String, Int>> = if (appMemoryMap.isNotEmpty()) {
                        // 按内存消耗排序，取前3个
                        Log.d(TAG, "根据内存使用情况选择应用")
                        appMemoryMap.entries
                            .sortedByDescending { it.value }
                            //.take(6)
                            .map { Pair(it.key, it.value) }
                    } else if (userApps.isNotEmpty()) {
                        // 无法获取内存信息，使用过滤后的用户应用按耗电量排序，取前3个
                        Log.w(TAG, "无法获取应用内存信息，使用过滤后的用户应用按耗电量排序")
                        userApps
                            //.take(6)
                            .map { appBatteryUsage ->
                            Pair(appBatteryUsage.packageName, 0)
                        }
                    } else {
                        // 没有可处理的用户应用，使用原始列表按耗电量排序，取前3个（过滤掉当前应用）
                        Log.w(TAG, "没有可处理的用户应用，使用原始列表按耗电量排序")
                        topApps.filter { it.packageName != packageName }
                            //.take(6)
                            .map { appBatteryUsage ->
                                Pair(appBatteryUsage.packageName, 0)
                            }
                    }
                    
                    Log.d(TAG, "最终选择的应用数量: ${finalApps.size}")
                    finalApps.forEach { (pkg, mem) ->
                        Log.d(TAG, "选择的应用: $pkg, 内存: ${mem}KB")
                    }
                    
                    Log.d(TAG, "准备结束的前多个应用: ${finalApps.joinToString { "${it.first}(${it.second}KB)" }}")
                    
                    var killedCount = 0
                    val killedApps = mutableListOf<String>()
                    
                    // 结束这些应用
                    finalApps.forEach {
                        try {
                            val packageName = it.first
                            Log.d(TAG, "准备结束应用: $packageName")
                            
                            // 结束应用
                            activityManager.killBackgroundProcesses(packageName)
                            killedCount++
                            killedApps.add(packageName)
                            Log.d(TAG, "已结束应用: $packageName")
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "结束应用失败: ${it.first}, ${e.message}")
                        }
                    }
                    
                    // 显示结果浮层
                    showAccelerateResultDialog(killedCount, killedApps)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "系统加速失败: ${e.message}", e)
                    // 显示错误提示
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("加速失败")
                        .setMessage("系统加速过程中发生错误: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        } else {
            // 不是耗电排行页面，提示用户
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("请先切换到耗电排行页面，再使用系统加速功能")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    /**
     * 显示加速结果浮层
     */
    private fun showAccelerateResultDialog(killedCount: Int, killedApps: List<String>) {
        val message = if (killedCount > 0) {
            val appsString = killedApps.joinToString("\n")
            "系统加速完成！\n\n已结束 $killedCount 个应用：\n$appsString\n\n系统运行更流畅了！"
        } else {
            "当前没有可加速的应用\n\n系统已经处于最佳状态！"
        }
        
        AlertDialog.Builder(this)
            .setTitle("加速结果")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .setCancelable(false)
            .show()
    }

    /**
     * 检查是否有PACKAGE_USAGE_STATS权限
     */
    private fun checkUsageStatsPermission(): Boolean {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        val packageManager = packageManager
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
        return resolveInfoList.isNotEmpty() && hasPermission()
    }

    /**
     * 检查是否有NETWORK_STATS和READ_NETWORK_USAGE_HISTORY权限
     */
    private fun checkNetworkStatsPermission(): Boolean {
        // 对于NETWORK_STATS和READ_NETWORK_USAGE_HISTORY权限，需要系统权限，普通应用无法通过常规方式获取
        // 这里主要检查TrafficStats是否可用
        val testUid = android.os.Process.myUid()
        val rxBytes = android.net.TrafficStats.getUidRxBytes(testUid)
        val txBytes = android.net.TrafficStats.getUidTxBytes(testUid)
        
        // 如果获取到的值不是UNSUPPORTED，说明TrafficStats基本可用
        val isAvailable = rxBytes != android.net.TrafficStats.UNSUPPORTED.toLong() || 
                          txBytes != android.net.TrafficStats.UNSUPPORTED.toLong()
        
        if (!isAvailable) {
            Log.w(TAG, "TrafficStats API不可用，无法获取应用网络流量数据")
        }
        
        return isAvailable
    }

    /**
     * 检查应用是否已被授予UsageStats权限和KILL_BACKGROUND_PROCESSES权限
     */
    private fun hasPermission(): Boolean {
        // 检查UsageStats权限
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        val currentTime = System.currentTimeMillis()
        // 检查过去一小时内的使用情况
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 60 * 60 * 1000,
            currentTime
        )
        if (stats.isEmpty()) {
            Log.w(TAG, "没有获取到应用使用情况")
        }
        
        // 检查KILL_BACKGROUND_PROCESSES权限
        val hasKillPermission = checkSelfPermission(android.Manifest.permission.KILL_BACKGROUND_PROCESSES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "UsageStats权限: ${stats.isNotEmpty()}, KILL_BACKGROUND_PROCESSES权限: $hasKillPermission")
        
        return stats.isNotEmpty() && hasKillPermission
    }

    /**
     * 请求UsageStats权限，引导用户到设置页面
     */
    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle("权限请求")
            .setMessage("为了显示准确的应用耗电排行，需要您授予\"应用使用情况访问权限\"。请在设置页面中找到\"电池检测\"应用并启用权限。")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用使用情况设置页面
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
