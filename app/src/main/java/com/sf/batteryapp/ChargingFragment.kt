package com.sf.batteryapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.concurrent.TimeUnit

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
    private lateinit var tvEstimatedChargingTime: TextView

    companion object {
        // 日志标签
        private const val TAG = "BatteryInfo"
        
        // 典型的电池容量范围（mAh）
        private const val MIN_BATTERY_CAPACITY_MAH = 1000  // 最小电池容量 1000mAh
        private const val MAX_BATTERY_CAPACITY_MAH = 10000  // 最大电池容量 10000mAh

        // 合理的电池电压范围（毫伏）
        private const val MIN_VALID_VOLTAGE_MV = 3000  // 3.0V（极低电量）
        private const val MAX_VALID_VOLTAGE_MV = 10000  // 10.0V（满电）
    }    

    // 备用的电压键（某些厂商可能使用不同的键）
    private val VOLTAGE_KEYS = listOf(
        "voltage",                // 标准键
        "battery_voltage",        // 部分厂商
        "batt_vol",               // 部分厂商
        "batteryVoltage",         // 部分厂商
        BatteryManager.EXTRA_VOLTAGE // 官方键
    )
    
    // 性能模式相关
    // private lateinit var switchPerformanceMode: SwitchMaterial
    // private lateinit var tvPerformanceModeTip: TextView
    
    // 省电模式相关
    // private lateinit var switchPowerSavingMode: SwitchMaterial
    private lateinit var tvPowerSavingModeTip: TextView
    
    // 今日亮屏时长
    private lateinit var tvScreenOnTime: TextView
    
    // 耗电排行
    private lateinit var rvBatteryUsageRank: RecyclerView
    
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
        // 电池电量百分比文本
        tvBatteryPercentage = view.findViewById(R.id.tv_battery_percentage)
        // 电池状态文本（充电/放电）
        tvBatteryState = view.findViewById(R.id.tv_battery_state)
        // 实时功率值文本
        tvPowerValue = view.findViewById(R.id.tv_power_value)
        // 充电/放电电流文本
        tvChargingCurrent = view.findViewById(R.id.tv_charging_current)
        // 电池电压文本
        tvVoltage = view.findViewById(R.id.tv_voltage)
        // 电池温度文本
        tvBatteryTemperature = view.findViewById(R.id.tv_battery_temperature)
        // 充电速度或放电速度文本
        tvSpeedValue = view.findViewById(R.id.tv_speed_value)
        // 功率标签文本
        tvPower = view.findViewById(R.id.tv_power)
        // 估计电池容量文本
        tvEstimatedCapacityValue = view.findViewById(R.id.tv_estimated_capacity_value)
        // 卡片功率值文本
        tvCardPower = view.findViewById(R.id.tv_card_power)
        // 预计充满所需时间文本
        tvEstimatedChargingTime = view.findViewById(R.id.tv_estimated_charging_time)
        
        // 性能模式相关
        // switchPerformanceMode = view.findViewById(R.id.switch_performance_mode)
        // 性能模式提示文本
        // tvPerformanceModeTip = view.findViewById(R.id.tv_performance_mode_tip)
        
        // 省电模式相关
        // switchPowerSavingMode = view.findViewById(R.id.switch_power_saving_mode)
        // 省电模式提示文本
        tvPowerSavingModeTip = view.findViewById(R.id.tv_power_saving_mode_tip)
        
        // 今日亮屏时长
        tvScreenOnTime = view.findViewById(R.id.tv_screen_on_time)
        
        // 耗电排行
        // rvBatteryUsageRank = view.findViewById(R.id.rv_battery_usage_rank)
        // 设置适配器
        // setupBatteryUsageRecyclerView()
    }
    
    /**
     * 设置耗电排行RecyclerView
     */
    /*
    private fun setupBatteryUsageRecyclerView() {
        rvBatteryUsageRank.layoutManager = LinearLayoutManager(requireContext())
        // 暂时隐藏耗电排行，因为AppBatteryUsageAdapter需要导入和处理
        rvBatteryUsageRank.visibility = View.GONE
    }
     */
    
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

    /**
     * 智能标准化：检测并转换为 µAh
     */
    private fun normalizeChargeCounter(rawValue: Long): Long {
        if (rawValue <= 0) return rawValue
        
        // 检测当前值的可能单位
        return when (detectChargeCounterUnit(rawValue)) {
            ChargeCounterUnit.NANO_AMP_HOURS -> {
                Log.d(TAG, "Detected nAh, converting to µAh: ${rawValue / 1000L}")
                rawValue / 1000L  // nAh → µAh
            }
            ChargeCounterUnit.MILLI_AMP_HOURS -> {
                Log.d(TAG, "Detected mAh, converting to µAh: ${rawValue * 1000L}")
                rawValue * 1000L  // mAh → µAh
            }
            ChargeCounterUnit.MICRO_AMP_HOURS -> {
                Log.d(TAG, "Detected µAh, using as-is: $rawValue")
                rawValue  // 已经是 µAh
            }
            ChargeCounterUnit.UNKNOWN -> {
                // 启发式猜测：基于常见的电池容量范围
                guessAndConvert(rawValue)
            }
        }
    }

    /**
     * 单位枚举
     */
    private enum class ChargeCounterUnit {
        NANO_AMP_HOURS,   // nAh
        MICRO_AMP_HOURS,  // µAh
        MILLI_AMP_HOURS,  // mAh
        UNKNOWN
    }
    
    /**
     * 检测原始值的单位
     */
    private fun detectChargeCounterUnit(rawValue: Long): ChargeCounterUnit {
        // 将原始值转换为 mAh 范围进行判断
        val valueInMah = rawValue.toDouble() / 1000.0  // 假设是 µAh
        
        when {
            // 如果是 nAh 单位：值会很小（如 1650 nAh = 1.65 µAh = 0.00165 mAh）
            rawValue < 1000 -> {
                return ChargeCounterUnit.NANO_AMP_HOURS
            }
            
            // 如果是 µAh 单位：应该在合理电池容量范围内
            valueInMah in MIN_BATTERY_CAPACITY_MAH.toDouble()..MAX_BATTERY_CAPACITY_MAH.toDouble() -> {
                return ChargeCounterUnit.MICRO_AMP_HOURS
            }
            
            // 如果是 mAh 单位：值会很大（如 4861440 mAh 不合理，但可能是 µAh）
            rawValue > MAX_BATTERY_CAPACITY_MAH * 1000 -> {
                // 如果以 mAh 计算的值远大于最大电池容量，说明它实际上可能是 µAh
                // 例如：4861440 > 6000*1000，所以它不是 mAh
                return if (rawValue > MAX_BATTERY_CAPACITY_MAH * 1000L) {
                    ChargeCounterUnit.MICRO_AMP_HOURS
                } else {
                    ChargeCounterUnit.MILLI_AMP_HOURS
                }
            }
            
            // 其他情况：可能是 mAh
            else -> {
                return ChargeCounterUnit.MILLI_AMP_HOURS
            }
        }
    }
    
    /**
     * 启发式猜测与转换
     */
    private fun guessAndConvert(rawValue: Long): Long {
        // 基于 Android 版本的经验规则
        return when (Build.VERSION.SDK_INT) {
            in Build.VERSION_CODES.M..Build.VERSION_CODES.P -> {
                // Android 6.0-9.0：通常是 µAh
                Log.d(TAG, "API ${Build.VERSION.SDK_INT}: Assuming µAh")
                rawValue
            }
            Build.VERSION_CODES.Q -> {
                // Android 10：存在不一致，但大多数是 µAh
                Log.d(TAG, "API 29: Most devices use µAh")
                rawValue
            }
            Build.VERSION_CODES.R -> {
                // Android 11：有些设备可能改为 mAh
                Log.d(TAG, "API 30: Checking if mAh...")
                if (rawValue in 1000L..6000L) rawValue * 1000L else rawValue
            }
            Build.VERSION_CODES.S -> {
                // Android 12 (API 31)：你的设备返回 1650，很可能是 nAh
                Log.d(TAG, "API 31: Suspected nAh, converting to µAh")
                rawValue / 1000L
            }
            Build.VERSION_CODES.S_V2, Build.VERSION_CODES.TIRAMISU -> {
                // Android 12L/13：可能已修复，假设 µAh
                Log.d(TAG, "API ${Build.VERSION.SDK_INT}: Assuming µAh")
                rawValue
            }
            else -> {
                // 默认假设为 µAh
                Log.d(TAG, "Unknown API ${Build.VERSION.SDK_INT}: Assuming µAh")
                rawValue
            }
        }
    }
    /**
     * 验证电压值是否合理
     */
    private fun isValidVoltage(voltageMv: Int): Boolean {
        return voltageMv in MIN_VALID_VOLTAGE_MV..MAX_VALID_VOLTAGE_MV
    }

    /**
     * 方法3：直接从系统文件读取（需要权限）
     */
    private fun readVoltageFromSysFs(): Int {
        val sysfsPaths = listOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/battery/batt_vol",
            "/sys/class/power_supply/bms/voltage_now",
            "/sys/class/power_supply/usb/voltage_now",
            "/proc/battery/status"
        )
        
        for (path in sysfsPaths) {
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    val content = file.readText().trim()
                    val voltage = content.toIntOrNull() ?: continue
                    
                    // 系统文件可能是微伏(µV)而不是毫伏(mV)
                    val voltageMv = when {
                        voltage > 1000000 -> voltage / 1000  // µV → mV
                        voltage > 1000 && voltage < 10000 -> voltage / 1  // 已经是 mV
                        voltage < 100 -> voltage * 1000     // V → mV
                        else -> voltage
                    }
                    
                    if (isValidVoltage(voltageMv)) {
                        Log.d(TAG, "Read voltage from $path: $voltage -> $voltageMv mV")
                        return voltageMv
                    }
                }
            } catch (e: Exception) {
                // 继续尝试下一个路径
            }
        }
        
        return -1
    }

    /**
     * 基于 Android 版本返回估计值
     */
    private fun estimateVoltageByApiVersion(): Int {
        return when (Build.VERSION.SDK_INT) {
            in Build.VERSION_CODES.LOLLIPOP..Build.VERSION_CODES.Q -> {
                // Android 5.0-10：通常返回正确的 mV
                4200  // 典型值
            }
            Build.VERSION_CODES.R, Build.VERSION_CODES.S -> {
                // Android 11-12：可能有问题
                4150
            }
            Build.VERSION_CODES.TIRAMISU -> {
                // Android 13 (API 33)：你的设备有问题
                Log.e(TAG, "API 33 has known voltage bug, using fallback")
                4100
            }
            else -> {
                // 更新的版本
                4200
            }
        }
    }
    
    /**
     * 基于 API 版本的默认值
     */
    private fun getDefaultVoltageForApi(): Int {
        return if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
            4100  // API 33 的特定默认值
        } else {
            4200  // 其他版本的默认值
        }
    }

    /**
     * 获取可靠的电池电压（毫伏）
     */
    fun getReliableBatteryVoltage(context: Context): Int {
        return try {
            // 方法1：使用 BatteryManager API（Android 5.0+）
            val voltageFromManager = getVoltageFromBatteryManager(context)
            if (isValidVoltage(voltageFromManager)) {
                Log.d(TAG, "Using BatteryManager voltage: ${voltageFromManager}mV")
                return voltageFromManager
            }
            
            // 方法2：使用 Intent 数据（多重键尝试）
            val voltageFromIntent = getVoltageFromIntent(context)
            if (isValidVoltage(voltageFromIntent)) {
                Log.d(TAG, "Using Intent voltage: ${voltageFromIntent}mV")
                return voltageFromIntent
            }
            
            // 方法3：使用系统文件读取
            val voltageFromSysfs = readVoltageFromSysFs()
            if (isValidVoltage(voltageFromSysfs)) {
                Log.d(TAG, "Using sysfs voltage: ${voltageFromSysfs}mV")
                return voltageFromSysfs
            }
            
            // 方法4：基于 Android 版本的经验值
            val estimatedVoltage = estimateVoltageByApiVersion()
            Log.w(TAG, "Using estimated voltage for API ${Build.VERSION.SDK_INT}: ${estimatedVoltage}mV")
            estimatedVoltage
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery voltage", e)
            // 返回安全的默认值
            getDefaultVoltageForApi()
        }
    }
    
    /**
     * 方法1：使用 BatteryManager（最官方的方法）
     */
    private fun getVoltageFromBatteryManager(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                
                // 尝试不同的 BatteryManager 属性
                val properties = listOf(
                    "voltage",              // 某些设备可能使用这个
                    "batteryVoltage"        // 某些设备可能使用这个
                )
                
                // 使用反射尝试所有可能的属性（如果标准方法失败）
                for (prop in properties) {
                    try {
                        val field = BatteryManager::class.java.getDeclaredField(prop)
                        field.isAccessible = true
                        val value = field.get(batteryManager)
                        if (value is Int && isValidVoltage(value)) {
                            return value
                        }
                    } catch (e: Exception) {
                        // 继续尝试下一个属性
                    }
                }
                
                // 标准方法：通过 Intent 获取
                val intent = context.registerReceiver(null, 
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                return intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
                
            } catch (e: Exception) {
                Log.w(TAG, "BatteryManager voltage failed", e)
            }
        }
        return -1
    }

    /**
     * 特别纠正 API 33 的电压值
     */
    private fun correctVoltageForApi33(rawVoltage: Int): Int {
        // 如果 API 33 且值异常小（< 100），假设它是伏特而不是毫伏
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU && rawVoltage in 1..100) {
            Log.w(TAG, "API 33 voltage correction: $rawVoltage -> ${rawVoltage * 1000} mV")
            return rawVoltage * 1000  // 假设是伏特，转换为毫伏
        }
        
        // 如果值在 3000-4500 之间，直接返回
        if (rawVoltage in MIN_VALID_VOLTAGE_MV..MAX_VALID_VOLTAGE_MV) {
            return rawVoltage
        }
        
        // 如果值在 3-4.5 之间，假设是伏特
        if (rawVoltage in 3..5) {
            return rawVoltage * 1000
        }
        
        // 如果值在 300-500 之间，可能是 0.1mV 单位？
        if (rawVoltage in 300..500) {
            return rawVoltage * 10
        }
        
        return rawVoltage
    }
    
    /**
     * 方法2：从 Intent 中尝试多个可能的键
     */
    private fun getVoltageFromIntent(context: Context): Int {
        val intent = context.registerReceiver(null, 
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        
        // 尝试所有已知的键
        for (key in VOLTAGE_KEYS) {
            try {
                if (intent.hasExtra(key)) {
                    val value = intent.extras?.get(key)
                    val voltage = when (value) {
                        is Int -> value
                        is Long -> value.toInt()
                        is String -> value.toIntOrNull() ?: -1
                        else -> -1
                    }
                    
                    // 特别处理 API 33 的小值问题：可能是单位问题
                    val correctedVoltage = correctVoltageForApi33(voltage)
                    
                    if (isValidVoltage(correctedVoltage)) {
                        Log.d(TAG, "Found voltage with key '$key': $voltage -> $correctedVoltage mV")
                        return correctedVoltage
                    }
                }
            } catch (e: Exception) {
                // 继续尝试下一个键
            }
        }
        
        return -1
    }

    private fun updateBatteryStatus(intent: Intent) {     
        // 使用BatteryManager API估计电池容量
        val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        // 获取当前剩余电荷（微安时 µAh）
        val chargeCounter = when {
            // 方案1：优先使用 BatteryManager API（Android 6.0+）
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && batteryManager != null -> {
                val value = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                Log.d("BatteryInfo", "Using BatteryManager API ${android.os.Build.VERSION.SDK_INT}, charge_counter: $value")
                value
            }
            
            // 方案2：使用 Intent 附加数据（全版本支持）
            intent != null && intent.hasExtra("charge_counter") -> {
                // 注意：有些设备使用 Integer，有些使用 Long
                val value = when {
                    intent.hasExtra("charge_counter") -> {
                        // 尝试获取 Long 类型
                        intent.getLongExtra("charge_counter", -1L)
                    }
                    intent.hasExtra("charge_counter") -> {
                        // 如果失败，尝试获取 Int 类型并转换
                        intent.getIntExtra("charge_counter", -1).toLong()
                    }
                    else -> -1L
                }
                Log.d("BatteryInfo", "Using Intent API, charge_counter: $value")
                value
            }
            
            // 方案3：所有方法都失败
            else -> {
                Log.w("BatteryInfo", "All charge counter retrieval methods failed")
                -1L
            }
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

        val normalizedChargeCounter = normalizeChargeCounter(chargeCounter)
        
        // 基于电量百分比估算总容量：估算总容量 = (当前电荷 / 当前百分比) * 100% 
        estimatedCapacity = if (normalizedChargeCounter > 0 && percentage > 0) {
            // 转换为mAh：μAh / 1000 = mAh
            val chargeInmAh = normalizedChargeCounter / 1000.0
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
        // 使用已经通过BatteryManager normalizeChargeCounter
        // 注意：上面已经定义了normalizeChargeCounter变量
        
        Log.d("BatteryInfo", "current_now: $currentNow μA")
        Log.d("BatteryInfo", "current: $currentRaw mA")
        Log.d("BatteryInfo", "charge_counter: $normalizedChargeCounter μA")
        Log.d("BatteryInfo", "isCharging: $isCharging")

        // 12-02 09:50:58.151 27293 27293 D BatteryInfo: Intent extras: Bundle[mParcelledData.dataSize=1120]
        // 12-02 09:50:58.152 27293 27293 D BatteryInfo: current_now: -1 μA
        // 12-02 09:50:58.152 27293 27293 D BatteryInfo: current: -1 mA
        // 12-02 09:50:58.152 27293 27293 D BatteryInfo: charge_counter: 2586400 μA
        // 12-02 09:50:58.153 27293 27293 D BatteryInfo: isCharging: true
        // 12-02 09:50:58.153 27293 27293 D BatteryInfo: Final current: 2586.4 mA
        
        // 获取电压（mV），确保电压值有效
        // val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3800) // 默认3800mV
        // val voltage = if (voltageRaw > 0) voltageRaw / 1000.0 else 3.8 // 默认3.8V
        val voltageRaw = getReliableBatteryVoltage(requireContext())
        val voltage = if (voltageRaw > 0) voltageRaw / 1000.0 else 3.8 // 将mV转换为V

        // Log.d("BatteryInfo", "voltageRaw: $voltageRaw mV")
        Log.d("BatteryInfo", "Final voltage: $voltage V")

        // 获取温度（°C）   
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        
        // 电流（mA），如果获取不到实际值则使用备选方案或默认值（TODO 这里面进行了多个值的选择，其实很容易导致后续使用哪一个值才是对的情况的问题，或者说会导致后续有些情况适用currentRaw有些使用currentNow，有些时候使用current，导致上下逻辑不一致的混乱）
        val current = when {
            currentNow != -1 -> currentNow.toDouble() / 1000.0
            normalizedChargeCounter != -1L -> normalizedChargeCounter.toDouble() / 1000.0
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
            // 判断充电模式
            val chargingMode = com.sf.batteryapp.data.BatteryRepository(requireContext()).determineChargingMode(displayPower.toFloat(), current.toFloat())
            val modeText = when (chargingMode.mode) {
                com.sf.batteryapp.model.ChargingMode.Mode.TRICKLE -> "涓流充电"
                com.sf.batteryapp.model.ChargingMode.Mode.SLOW -> "慢充"
                com.sf.batteryapp.model.ChargingMode.Mode.NORMAL -> "普通充电"
                com.sf.batteryapp.model.ChargingMode.Mode.FAST -> "快充"
                com.sf.batteryapp.model.ChargingMode.Mode.SUPER_FAST -> "超级快充"
                com.sf.batteryapp.model.ChargingMode.Mode.ULTRA_FAST -> "极速快充"
                else -> getString(R.string.charging_state)
            }
            "${getString(R.string.charging_state)} (${modeText})"
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
                    "$speedStr \n $remainingTimeStr"
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
        
        // 计算并更新预估充电时间
        updateEstimatedChargingTime(isCharging, actualPercentage, current)

        // 调用系统API获取今日亮屏时长
        val usageStatsManager = requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - TimeUnit.DAYS.toMillis(1)
        val usageStatsList = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            beginTime,
            endTime
        )
        var totalScreenOnTime = 0L
        for (usageStats in usageStatsList) {
            totalScreenOnTime += usageStats.totalTimeInForeground
        }
        val hours = TimeUnit.MILLISECONDS.toHours(totalScreenOnTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalScreenOnTime) % 60
        tvScreenOnTime.text = "${hours}小时${minutes}分钟"
    }
    
    /**
     * 更新预估充电时间
     */
    private fun updateEstimatedChargingTime(isCharging: Boolean, currentPercentage: Int, current: Double) {
        if (isCharging && current > 0 && currentPercentage < 100) {
            // 计算剩余电量百分比
            val remainingPercentage = 100 - currentPercentage
            
            // 计算剩余容量（mAh）
            val remainingCapacity = (estimatedCapacity * remainingPercentage) / 100.0
            
            // 计算预估充电时间（分钟）：剩余容量（mAh） / 充电电流（mA） * 60（分钟）
            val estimatedMinutes = (remainingCapacity / current) * 60.0
            
            // 格式化时间为xx小时:xx分钟格式
            val totalMinutes = estimatedMinutes.toInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            
            // 更新UI，固定显示为小时:分钟格式
            tvEstimatedChargingTime.text = String.format("%d小时:%d分钟", hours, minutes)
        } else if (currentPercentage >= 100) {
            // 已经充满
            tvEstimatedChargingTime.text = "已充满"
        } else {
            // 未充电或充电电流为0
            tvEstimatedChargingTime.text = "--:--"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 注销广播接收器
        requireContext().unregisterReceiver(batteryReceiver)
    }
}
