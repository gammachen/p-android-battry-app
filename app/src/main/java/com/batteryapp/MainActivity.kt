package com.batteryapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 设置Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 检查并请求UsageStats权限
        checkUsageStatsPermission()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_charging -> {
                    toolbar.title = "实时监控"
                    replaceFragment(ChargingFragment())
                    true
                }
                R.id.nav_battery_usage -> {
                    toolbar.title = "耗电排行"
                    // 跳转到耗电排行前再次检查权限
                    if (checkUsageStatsPermission()) {
                        replaceFragment(BatteryUsageFragment())
                    } else {
                        requestUsageStatsPermission()
                    }
                    true
                }
                R.id.nav_health -> {
                    toolbar.title = "健康评估"
                    replaceFragment(HealthFragment())
                    true
                }
                R.id.nav_system_info -> {
                    toolbar.title = "系统信息"
                    replaceFragment(SystemInfoFragment())
                    true
                }
                else -> false
            }
        }

        // 默认显示实时监控页面
        toolbar.title = "实时监控"
        replaceFragment(ChargingFragment())
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
