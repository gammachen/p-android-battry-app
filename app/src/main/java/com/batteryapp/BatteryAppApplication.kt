package com.batteryapp

import android.app.Application
import android.util.Log
import androidx.work.*
import com.batteryapp.worker.AppBatteryUsageWorker
import com.batteryapp.worker.BatteryUsageWorker
import java.util.concurrent.TimeUnit

/**
 * 应用程序类，用于初始化应用级组件
 */
class BatteryAppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        Log.d("BatteryAppApplication", "应用启动，初始化WorkManager")
        
        // 初始化WorkManager
        initWorkManager()
        
        // 清空旧的测试数据
        clearTestData()
    }
    
    /**
     * 清空旧的测试数据
     */
    private fun clearTestData() {
        // 在应用启动时清空旧数据
        val viewModel = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(this)
            .create(com.batteryapp.viewmodel.BatteryViewModel::class.java)

        viewModel.clearAllData()
        Log.d("BatteryAppApplication", "已清空旧的测试数据-------------------")
    }

    /**
     * 初始化WorkManager，设置周期性任务
     */
    private fun initWorkManager() {
        // 1. 电池状态采集任务配置
        val batteryConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // 降低约束条件，更容易被触发
            .build()

        val batteryWorkRequest = PeriodicWorkRequestBuilder<BatteryUsageWorker>(
            repeatInterval = 2,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(batteryConstraints)
            .build()

        // 2. 应用耗电统计任务配置
        val appUsageConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // 降低约束条件，更容易被触发
            .setRequiresCharging(false)
            .build()

        val appUsageWorkRequest = PeriodicWorkRequestBuilder<AppBatteryUsageWorker>(
            repeatInterval = 3,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(appUsageConstraints)
            .build()

        // 3. 加入调度队列
        val workManager = WorkManager.getInstance(this)
        workManager.apply {
            // 电池状态采集 - 每2分钟执行一次
            enqueueUniquePeriodicWork(
                "BatteryUsageWork",
                ExistingPeriodicWorkPolicy.KEEP,
                batteryWorkRequest
            )
            
            // 应用耗电统计 - 每3分钟执行一次
            enqueueUniquePeriodicWork(
                "AppBatteryUsageWork",
                ExistingPeriodicWorkPolicy.KEEP,
                appUsageWorkRequest
            )
        }
        
        // 立即触发一次应用耗电统计任务，用于测试
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<AppBatteryUsageWorker>()
            .setConstraints(appUsageConstraints)
            .build()
        workManager.enqueue(oneTimeWorkRequest)
        Log.d("BatteryAppApplication", "已立即触发一次AppBatteryUsageWorker任务")
        
        Log.d("BatteryAppApplication", "WorkManager初始化完成")
        Log.d("BatteryAppApplication", "电池状态采集任务配置: 每2分钟执行一次")
        Log.d("BatteryAppApplication", "应用耗电统计任务配置: 每3分钟执行一次")
    }
}