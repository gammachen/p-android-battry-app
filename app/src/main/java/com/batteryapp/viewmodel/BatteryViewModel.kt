package com.batteryapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.batteryapp.data.BatteryRepository
import com.batteryapp.model.BatteryData
import com.batteryapp.model.BatteryHealthData
import com.batteryapp.model.AppBatteryUsage
import com.batteryapp.model.BatteryHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

/**
 * 电池数据视图模型，为UI提供数据和状态管理
 */
class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BatteryRepository(application)
    private val viewModelScope = CoroutineScope(Dispatchers.Main)
    
    // 电池数据相关
    private val _batteryData = MutableLiveData<BatteryData?>(null)
    val batteryData: LiveData<BatteryData?> = _batteryData
    
    private val _recentBatteryData = MutableLiveData<List<BatteryData>>(emptyList())
    val recentBatteryData: LiveData<List<BatteryData>> = _recentBatteryData
    
    // 电池健康度相关
    private val _batteryHealthData = MutableLiveData<BatteryHealthData?>(null)
    val batteryHealthData: LiveData<BatteryHealthData?> = _batteryHealthData
    
    private val _recentBatteryHealthData = MutableLiveData<List<BatteryHealthData>>(emptyList())
    val recentBatteryHealthData: LiveData<List<BatteryHealthData>> = _recentBatteryHealthData
    
    // 应用耗电排行榜相关
    private val _appBatteryUsage = MutableLiveData<List<AppBatteryUsage>>(emptyList())
    val appBatteryUsage: LiveData<List<AppBatteryUsage>> = _appBatteryUsage
    
    // 分场景统计相关
    private val _appBatteryUsageByScene = MutableLiveData<List<AppBatteryUsage>>(emptyList())
    val appBatteryUsageByScene: LiveData<List<AppBatteryUsage>> = _appBatteryUsageByScene
    
    // 历史数据相关
    private val _recentBatteryHistory = MutableLiveData<List<BatteryHistory>>(emptyList())
    val recentBatteryHistory: LiveData<List<BatteryHistory>> = _recentBatteryHistory
    
    // 加载状态相关
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // 加载最近的电池数据
    fun loadRecentBatteryData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val data = repository.getRecentBatteryData()
                _recentBatteryData.value = data
                if (data.isNotEmpty()) {
                    _batteryData.value = data[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 加载电池健康度数据
    fun loadBatteryHealthData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 先尝试获取最新的健康度数据
                val latestData = repository.getLatestBatteryHealthData()
                if (latestData != null) {
                    _batteryHealthData.value = latestData
                } else {
                    // 如果没有，计算一个新的健康度数据
                    val newData = repository.calculateBatteryHealth()
                    repository.insertBatteryHealthData(newData)
                    _batteryHealthData.value = newData
                }
                
                // 加载最近的健康度数据
                val recentData = repository.getRecentBatteryHealthData()
                _recentBatteryHealthData.value = recentData
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 加载应用耗电排行榜
    fun loadAppBatteryUsageRanking(rankType: BatteryRepository.BatteryUsageRankType) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                var data = repository.getAppBatteryUsageRanking(rankType)

                Log.d("BatteryViewModel", "loadAppBatteryUsageRanking: $data")
                
                // 如果数据库中没有数据，插入一些模拟数据
                if (data.isEmpty()) {
                    // 插入模拟数据
                    // insertMockAppBatteryUsage()
                    // 
                    Log.w("BatteryViewModel", "数据库中没有应用耗电数据!!!!!")
                }
                
                _appBatteryUsage.value = data
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e("BatteryViewModel", "加载应用耗电排行榜失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 插入模拟应用耗电数据
    private fun insertMockAppBatteryUsage() {
        viewModelScope.launch {
            try {
                val currentTimestamp = System.currentTimeMillis()
                val mockApps = listOf(
                    AppBatteryUsage(
                        timestamp = currentTimestamp,
                        packageName = "com.example.app1",
                        appName = "应用1",
                        totalUsage = 1500.0,
                        backgroundUsage = 500.0,
                        wakelockTime = 3600000,
                        screenOnUsage = 1000.0,
                        screenOffUsage = 500.0,
                        idleUsage = 200.0,
                        wlanUpload = 100.0,
                        wlanDownload = 500.0
                    ),
                    AppBatteryUsage(
                        timestamp = currentTimestamp,
                        packageName = "com.example.app2",
                        appName = "应用2",
                        totalUsage = 1200.0,
                        backgroundUsage = 400.0,
                        wakelockTime = 2400000,
                        screenOnUsage = 800.0,
                        screenOffUsage = 400.0,
                        idleUsage = 150.0,
                        wlanUpload = 80.0,
                        wlanDownload = 400.0
                    ),
                    AppBatteryUsage(
                        timestamp = currentTimestamp,
                        packageName = "com.example.app3",
                        appName = "应用3",
                        totalUsage = 900.0,
                        backgroundUsage = 300.0,
                        wakelockTime = 1800000,
                        screenOnUsage = 600.0,
                        screenOffUsage = 300.0,
                        idleUsage = 100.0,
                        wlanUpload = 60.0,
                        wlanDownload = 300.0
                    ),
                    AppBatteryUsage(
                        timestamp = currentTimestamp,
                        packageName = "com.example.app4",
                        appName = "应用4",
                        totalUsage = 750.0,
                        backgroundUsage = 250.0,
                        wakelockTime = 1200000,
                        screenOnUsage = 500.0,
                        screenOffUsage = 250.0,
                        idleUsage = 80.0,
                        wlanUpload = 50.0,
                        wlanDownload = 250.0
                    ),
                    AppBatteryUsage(
                        timestamp = currentTimestamp,
                        packageName = "com.example.app5",
                        appName = "应用5",
                        totalUsage = 600.0,
                        backgroundUsage = 200.0,
                        wakelockTime = 900000,
                        screenOnUsage = 400.0,
                        screenOffUsage = 200.0,
                        idleUsage = 60.0,
                        wlanUpload = 40.0,
                        wlanDownload = 200.0
                    )
                )
                
                // 插入模拟数据
                mockApps.forEach { app ->
                    repository.insertAppBatteryUsage(app)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 加载分场景统计
    fun loadAppBatteryUsageByScene(sceneType: BatteryRepository.BatteryUsageSceneType) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val data = repository.getAppBatteryUsageByScene(sceneType)
                _appBatteryUsageByScene.value = data
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 加载历史数据
    fun loadRecentBatteryHistory() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val data = repository.getRecentBatteryHistory()
                _recentBatteryHistory.value = data
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 插入电池数据
    fun insertBatteryData(data: BatteryData) {
        viewModelScope.launch {
            try {
                repository.insertBatteryData(data)
                loadRecentBatteryData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 刷新所有数据
    fun refreshAllData() {
        loadRecentBatteryData()
        loadBatteryHealthData()
        loadAppBatteryUsageRanking(BatteryRepository.BatteryUsageRankType.TIME_USAGE)
        loadAppBatteryUsageByScene(BatteryRepository.BatteryUsageSceneType.SCREEN_ON)
        loadRecentBatteryHistory()
    }
    
    // 清空所有数据
    fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.clearAllData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 获取电池设计容量
     */
    fun getBatteryDesignCapacity(): Double {
        return repository.getBatteryDesignCapacity(getApplication())
    }
}
