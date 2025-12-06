package com.batteryapp

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
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
import com.batteryapp.model.RunningAppInfo

/**
 * 运行中的APP列表Fragment
 */
class RunningAppFragment : Fragment() {
    
    private lateinit var rvRunningApps: RecyclerView
    private lateinit var adapter: RunningAppAdapter
    private lateinit var activityManager: ActivityManager
    private lateinit var packageManager: PackageManager
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
        val runningProcesses = activityManager.runningAppProcesses
        val runningApps = mutableListOf<RunningAppInfo>()
        
        runningProcesses.forEach { processInfo ->
            try {
                val packageName = processInfo.pkgList[0] // 获取进程的第一个包名
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val isForeground = processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                        processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                
                runningApps.add(
                    RunningAppInfo(
                        packageName = packageName,
                        appName = appName,
                        isForeground = isForeground,
                        pid = processInfo.pid,
                        uid = processInfo.uid
                    )
                )
            } catch (e: Exception) {
                Log.e("RunningAppFragment", "获取应用信息失败: ${e.message}")
            }
        }
        
        // 更新适配器数据
        adapter.updateData(runningApps)
    }
}
