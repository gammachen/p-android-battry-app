package com.sf.batteryapp

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sf.batteryapp.model.RunningAppInfo

/**
 * 运行中的APP列表Fragment
 */
class RunningAppFragment : Fragment() {
    
    private lateinit var rvRunningApps: RecyclerView
    private lateinit var adapter: RunningAppAdapter
    private lateinit var activityManager: ActivityManager
    private lateinit var packageManager: PackageManager
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 1000L // 1秒刷新一次
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadRunningApps()
            handler.postDelayed(this, refreshInterval)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_running_app, container, false)
        rvRunningApps = view.findViewById(R.id.rv_running_apps)
        
        activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        packageManager = requireContext().packageManager
        usageStatsManager = requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        setupRecyclerView()
        
        return view
    }
    
    override fun onResume() {
        super.onResume()
        // 开始定时刷新
        handler.post(refreshRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // 停止定时刷新
        handler.removeCallbacks(refreshRunnable)
    }
    
    private fun setupRecyclerView() {
        adapter = RunningAppAdapter(emptyList())
        rvRunningApps.layoutManager = LinearLayoutManager(requireContext())
        rvRunningApps.adapter = adapter
    }
    
    private fun loadRunningApps() {
        val runningApps = mutableListOf<RunningAppInfo>()
        
        // 获取所有安装的应用
        val installedApplications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        // 获取最近的应用使用统计
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 5 // 最近5分钟内的使用统计
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )
        
        // 创建包名到UsageStats的映射
        val usageStatsMap = mutableMapOf<String, UsageStats>()
        usageStatsList.forEach {
            usageStatsMap[it.packageName] = it
        }
        
        // 获取运行中的进程信息
        val runningProcesses = activityManager.runningAppProcesses
        val runningProcessMap = mutableMapOf<String, ActivityManager.RunningAppProcessInfo>()
        runningProcesses?.forEach { processInfo ->
            processInfo.pkgList.forEach {
                runningProcessMap[it] = processInfo
            }
        }
        
        // 遍历所有安装的应用，只添加正在运行的应用
        installedApplications.forEach { appInfo ->
            try {
                val packageName = appInfo.packageName
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                // 判断应用是否在运行
                val isRunning = usageStatsMap.containsKey(packageName) || runningProcessMap.containsKey(packageName)
                Log.w("RunningAppFragment", "应用 $appName ($packageName) 是否在运行：$isRunning")
                
                // TODO 理论上只要判断是否在运行中就可以，后面的逻辑备用，用于设计是否前台或者后台运行中
                if (isRunning) {
                    var isForeground = false
                    var pid = 0
                    
                    // 从runningAppProcesses获取更准确的状态信息
                    if (runningProcessMap.containsKey(packageName)) {
                        val processInfo = runningProcessMap[packageName]!!
                        pid = processInfo.pid
                        
                        // 判断是否在前台运行
                        isForeground = processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE

                        Log.w("RunningAppFragment", "应用 $packageName $pid 被列为运行中（前台运行：${isForeground}），但 importance 为 ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND = ${ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND} ${ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE}， importance 为 ${processInfo.importance}")
                    } else if (usageStatsMap.containsKey(packageName)) {
                        // 如果在runningProcessMap中找不到，但在usageStatsMap中找到了，说明应用可能在后台运行
                        Log.w("RunningAppFragment", "应用 $packageName $pid 在统计信息中捕获到！")
                        val usageStats = usageStatsMap[packageName]!!
                        
                        // 判断是否最近被使用过（最近10秒内）
                        val currentTime = System.currentTimeMillis()
                        if (usageStats.lastTimeUsed > currentTime - 10 * 1000) {
                            Log.w("RunningAppFragment", "应用 $packageName $pid 最近被使用过！")
                            isForeground = true
                        } else {
                            Log.w("RunningAppFragment", "应用 $packageName $pid 最近未被使用过！")
                            isForeground = false
                        }
                    }
                    
                    // 创建RunningAppInfo对象
                    runningApps.add(
                        RunningAppInfo(
                            packageName = packageName,
                            appName = appName,
                            isForeground = isForeground,
                            isRunning = isRunning,
                            pid = pid,
                            uid = appInfo.uid
                        )
                    )
                } else {
                    Log.w("RunningAppFragment", "应用 $packageName 未被列为运行中！")
                    
                    // 创建RunningAppInfo对象
                    runningApps.add(
                        RunningAppInfo(
                            packageName = packageName,
                            appName = appName,
                            isForeground = false,
                            isRunning = isRunning,
                            pid = 0,
                            uid = appInfo.uid
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("RunningAppFragment", "获取应用信息失败: ${e.message}")
            }
        }
        
        // 更新适配器数据
        adapter.updateData(runningApps)
    }
}
