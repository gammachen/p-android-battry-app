package com.batteryapp.model

/**
 * 运行中的应用信息数据类
 */
data class RunningAppInfo(
    val packageName: String,
    val appName: String,
    val isForeground: Boolean,
    val isRunning: Boolean,
    val pid: Int,
    val uid: Int
)
