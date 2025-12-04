package com.batteryapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class ChargingFragment : Fragment() {

    private lateinit var tvBatteryPercentage: TextView
    private lateinit var tvBatteryState: TextView
    private lateinit var tvPowerValue: TextView
    private lateinit var tvChargingCurrent: TextView
    private lateinit var tvVoltage: TextView
    private lateinit var tvBatteryTemperature: TextView
    private lateinit var tvSpeedValue: TextView
    private lateinit var tvPower: TextView
    private lateinit var tvEstimatedCapacityValue: TextView
    private lateinit var tvCardPower: TextView
    
    private lateinit var batteryReceiver: BroadcastReceiver
    
    // 用于计算充电速度的变量
    private var lastBatteryPercentage = -1
    private var lastUpdateTime = 0L
    private val batteryCapacity = 4040 // 电池设计容量，单位mAh
    
    // 用于计算估计容量的变量
    private var estimatedCapacity = 0 // 估计电池容量，单位mAh

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_charging, container, false)
        
        // 初始化UI组件
        initViews(view)
        
        // 注册电池状态广播接收器
        registerBatteryReceiver()
        
        // 初始更新电池状态
        updateBatteryStatus()
        
        return view
    }
    
    private fun initViews(view: View) {
        tvBatteryPercentage = view.findViewById(R.id.tv_battery_percentage)
        tvBatteryState = view.findViewById(R.id.tv_battery_state)
        tvPowerValue = view.findViewById(R.id.tv_power_value)
        tvChargingCurrent = view.findViewById(R.id.tv_charging_current)
        tvVoltage = view.findViewById(R.id.tv_voltage)
        tvBatteryTemperature = view.findViewById(R.id.tv_battery_temperature)
        tvSpeedValue = view.findViewById(R.id.tv_speed_value)
        tvPower = view.findViewById(R.id.tv_power)
        tvEstimatedCapacityValue = view.findViewById(R.id.tv_estimated_capacity_value)
        tvCardPower = view.findViewById(R.id.tv_card_power)
    }
    
    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    updateBatteryStatus(intent)
                }
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        requireContext().registerReceiver(batteryReceiver, filter)
    }
    
    private fun updateBatteryStatus() {
        val intent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let {
            updateBatteryStatus(it)
        }
    }
    
    private fun updateBatteryStatus(intent: Intent) {
        // 打印intent中的所有额外数据，用于调试
        Log.d("BatteryInfo", "Intent extras: ${intent.extras}")
        
        // 使用BatteryManager API估计电池容量
        val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        // 获取当前剩余电荷（微安时 µAh）
        val chargeCounter = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            } else {
                // 旧版本使用intent获取
                intent.getLongExtra("charge_counter", -1)
            }
        } catch (e: Exception) {
            // 兜底方案
            intent.getLongExtra("charge_counter", -1)
        }
        
        // 获取当前电量百分比
        val percentage = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                // 旧版本使用intent计算
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    (level * 100 / scale)
                } else {
                    -1
                }
            }
        } catch (e: Exception) {
            // 兜底方案
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                (level * 100 / scale)
            } else {
                -1
            }
        }
        
        Log.d("BatteryInfo", "chargeCounter: $chargeCounter μAh, percentage: $percentage%")
        
        // 基于电量百分比估算总容量：估算总容量 = (当前电荷 / 当前百分比) * 100% 
        estimatedCapacity = if (chargeCounter > 0 && percentage > 0) {
            // 转换为mAh：μAh / 1000 = mAh
            val chargeInmAh = chargeCounter / 1000.0
            // 计算总容量
            ((chargeInmAh / percentage) * 100).toInt()
        } else {
            // 如果获取不到有效数据，使用设计容量和健康度估算
            val batteryHealth = 0.92f // 假设电池健康度为92% 
            (batteryCapacity * batteryHealth).toInt()
        }
        
        Log.d("BatteryInfo", "estimatedCapacity: $estimatedCapacity mAh")
        
        // 获取电池状态信息
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        // 使用已经通过BatteryManager API获取的percentage值
        val actualPercentage = if (percentage > 0) percentage else (level * 100 / scale)
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL
        // 电池是否正在充电
        
        // 尝试获取所有可能的电流相关数据
        val currentNow = intent.getIntExtra("current_now", -1)
        val currentRaw = intent.getIntExtra("current", -1)
        // 使用已经通过BatteryManager API获取的chargeCounter值
        // 注意：上面已经定义了chargeCounter变量
        
        Log.d("BatteryInfo", "current_now: $currentNow μA")
        Log.d("BatteryInfo", "current: $currentRaw mA")
        Log.d("BatteryInfo", "charge_counter: $chargeCounter μA")
        Log.d("BatteryInfo", "isCharging: $isCharging")

        // 12-02 09:50:58.151 27293 27293 D BatteryInfo: Intent extras: Bundle[mParcelledData.dataSize=1120]
        // 12-02 09:50:58.152 27293 27293 D BatteryInfo: current_now: -1 μA
        // 12-02 09:50:58.152 27293 27293 D BatteryInfo: current: -1 mA
        // 12-02 09:50:58.152 27293 27293 D BatteryInfo: charge_counter: 2586400 μA
        // 12-02 09:50:58.153 27293 27293 D BatteryInfo: isCharging: true
        // 12-02 09:50:58.153 27293 27293 D BatteryInfo: Final current: 2586.4 mA
        
        // 获取电压（mV），确保电压值有效
        val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3800) // 默认3800mV
        val voltage = if (voltageRaw > 0) voltageRaw / 1000.0 else 3.8 // 默认3.8V
        Log.d("BatteryInfo", "voltageRaw: $voltageRaw mV")
        Log.d("BatteryInfo", "Final voltage: $voltage V")

        // 获取温度（°C）   
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        
        // 电流（mA），如果获取不到实际值则使用备选方案或默认值（TODO 这里面进行了多个值的选择，其实很容易导致问题）
        val current = when {
            currentNow != -1 -> currentNow.toDouble() / 1000.0
            chargeCounter != -1L -> chargeCounter.toDouble() / 1000.0
            currentRaw != -1 -> currentRaw.toDouble()
            else -> {
                // 如果获取不到实际电流值，根据充电状态使用合理的默认值
                if (isCharging) 1500.0 else -500.0
            }
        }
        
        Log.d("BatteryInfo", "Final current: $current mA")
        
        // 计算功率（W）= 电压（V） * 电流（A）= 电压（V） * 电流（mA） / 1000
        val power = (voltage * current) / 1000.0
        
        // 确保功率值有意义
        val displayPower = if (power == 0.0 && isCharging) {
            // 如果正在充电但功率为0，显示一个默认值
            5.7 // 假设一个默认充电功率（3.8V * 1500mA = 5.7W）
        } else if (power == 0.0 && !isCharging) {
            // 如果放电但功率为0，显示一个默认值
            -1.9 // 假设一个默认放电功率（3.8V * 500mA = 1.9W）
        } else {
            power
        }
        
        // 更新UI
        tvBatteryPercentage.text = "$actualPercentage%"
        
        // 更新充电状态
        val stateText = if (isCharging) {
            getString(R.string.charging_state)
        } else {
            getString(R.string.discharging_state)
        }
        tvBatteryState.text = stateText
        
        // 更新水平进度条
        view?.findViewById<ProgressBar>(R.id.horizontalProgressBar)?.progress = actualPercentage
        
        // 更新功率值
        tvPowerValue.text = String.format("%.2f w", displayPower)
        
        // 更新充电电流
        val currentText = if (isCharging) {
            String.format("+%.0f mA", current)
        } else {
            String.format("%.0f mA", current)
        }
        tvChargingCurrent.text = currentText
        
        // 更新电压
        tvVoltage.text = String.format("%.3f v", voltage)
        
        // 更新温度
        tvBatteryTemperature.text = String.format("%.1f °c", temperature)
        
        // 计算充电速度或放电速度及续航时间
        val speedText = if (isCharging) {
            // 基于电流计算充电速度 (mAh/min)
            val chargingSpeed = if (current > 0) {
                // 电流 (mA) 转换为 mAh/min
                val speed = (current / 60.0).toFloat()
                String.format("%.1f mAh/min", speed)
            } else {
                // 如果电流为0，使用百分比变化率计算
                val currentTime = System.currentTimeMillis()
                val speed = if (lastBatteryPercentage != -1 && lastUpdateTime != 0L) {
                    val timeDiffMinutes = (currentTime - lastUpdateTime) / (1000 * 60.0)
                    if (timeDiffMinutes > 0) {
                        val percentageDiff = actualPercentage - lastBatteryPercentage
                        val speedValue = (percentageDiff / timeDiffMinutes).toFloat()
                        String.format("%.1f %%/min", speedValue)
                    } else {
                        getString(R.string.charging_speed)
                    }
                } else {
                    getString(R.string.charging_speed)
                }
                
                // 更新最后一次的电池百分比和时间
                lastBatteryPercentage = actualPercentage
                lastUpdateTime = currentTime
                
                speed
            }
            chargingSpeed
        } else {
            // 放电速度及续航时间
            if (current != -500.0) { // 检查是否使用了默认值
                // 计算放电速度 (mAh/min)
                val speed = (-current / 60.0).toFloat() // 负号表示放电
                val speedStr = String.format("%.1f mAh/min", speed)
                
                // 计算剩余续航时间
                val remainingCapacity = estimatedCapacity * (actualPercentage.toDouble() / 100.0) // 剩余电量 (mAh)
                val dischargeCurrent = Math.abs(current) // 放电电流 (mA)
                
                if (dischargeCurrent > 0) {
                    val remainingTimeHours = remainingCapacity / dischargeCurrent // 剩余时间 (小时)
                    
                    // 转换为小时、分钟、秒
                    val hours = remainingTimeHours.toInt()
                    val minutes = ((remainingTimeHours - hours) * 60).toInt()
                    val seconds = (((remainingTimeHours - hours) * 60 - minutes) * 60).toInt()
                    
                    // 格式化续航时间
                    val remainingTimeStr = String.format("预估续航：%d时%d分%d秒", hours, minutes, seconds)
                    
                    // 组合放电速度和续航时间
                    "$speedStr \n ($remainingTimeStr)"
                } else {
                    speedStr
                }
            } else {
                // 使用默认放电速度计算
                val defaultDischargeCurrent = 500.0 // mA
                val remainingCapacity = estimatedCapacity * (actualPercentage.toDouble() / 100.0) // 剩余电量 (mAh)
                val remainingTimeHours = remainingCapacity / defaultDischargeCurrent // 剩余时间 (小时)
                
                // 转换为小时、分钟、秒
                val hours = remainingTimeHours.toInt()
                val minutes = ((remainingTimeHours - hours) * 60).toInt()
                val seconds = (((remainingTimeHours - hours) * 60 - minutes) * 60).toInt()
                
                // 格式化续航时间
                val remainingTimeStr = String.format("预估续航：%d时%d分%d秒", hours, minutes, seconds)
                
                "默认值 \n ($remainingTimeStr)"
            }
        }

        Log.d("BatteryInfo", "speedText: $speedText")
        tvSpeedValue.text = speedText
        
        // 更新充电状态卡片中的功率
        tvCardPower.text = String.format("%.2f w", displayPower)
        
        // 更新估计容量，改进显示逻辑，即使在充电时也显示估计值
        val estimatedCapacityText = if (isCharging) {
            // 即使在充电时也显示估计值
            "$estimatedCapacity mAh (充电中)"
        } else {
            "$estimatedCapacity mAh"
        }
        tvEstimatedCapacityValue.text = estimatedCapacityText
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 注销广播接收器
        requireContext().unregisterReceiver(batteryReceiver)
    }
}
