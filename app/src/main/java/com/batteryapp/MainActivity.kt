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
import com.google.android.material.bottomnavigation.BottomNavigationView
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
            try {
                // 检查是否有 PACKAGE_USAGE_STATS 权限
                if (!hasPermission()) {
                    // 没有权限，引导用户去设置中授予
                    requestUsageStatsPermission()
                    return
                }
                
                // 初始化 ActivityManager
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                
                // 初始化 UsageStatsManager
                val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
                
                // 获取过去24小时的应用使用情况
                val endTime = System.currentTimeMillis()
                val startTime = endTime - TimeUnit.DAYS.toMillis(1)
                val usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                Log.d(TAG, "获取到的应用使用情况数量: ${usageStatsList.size}")
                
                // 过滤掉系统应用和当前应用
                val userApps = usageStatsList.filter {
                    it.packageName != packageName &&
                    !it.packageName.startsWith("system") &&
                    !it.packageName.startsWith("com.android")
                }
                
                // 按使用时间排序，取前3个
                val top3Apps = userApps.sortedByDescending { it.totalTimeInForeground }
                    .take(3)
                
                Log.d(TAG, "准备结束的前3个应用: ${top3Apps.joinToString { it.packageName }}")
                
                var killedCount = 0
                val killedApps = mutableListOf<String>()
                
                // 结束这些应用
                top3Apps.forEach {
                    try {
                        Log.d(TAG, "准备结束应用: ${it.packageName}")
                        
                        // 结束应用
                        activityManager.killBackgroundProcesses(it.packageName)
                        killedCount++
                        killedApps.add(it.packageName)
                        Log.d(TAG, "已结束应用: ${it.packageName}")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "结束应用失败: ${it.packageName}, ${e.message}")
                    }
                }
                
                // 显示结果浮层
                showAccelerateResultDialog(killedCount, killedApps)
                
            } catch (e: Exception) {
                Log.e(TAG, "系统加速失败: ${e.message}", e)
                // 显示错误提示
                AlertDialog.Builder(this)
                    .setTitle("加速失败")
                    .setMessage("系统加速过程中发生错误: ${e.message}")
                    .setPositiveButton("确定", null)
                    .show()
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
     * 检查应用是否已被授予UsageStats权限
     */
    private fun hasPermission(): Boolean {
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        val currentTime = System.currentTimeMillis()
        // 检查过去一小时内的使用情况
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 60 * 60 * 1000,
            currentTime
        )
        return stats.isNotEmpty()
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
