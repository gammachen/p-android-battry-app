package com.batteryapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.batteryapp.model.BatteryHealthData
import com.batteryapp.viewmodel.BatteryViewModel


/**
 * 电池健康评估Fragment，显示电池健康度评估与预测
 */
class HealthFragment : Fragment() {
    
    private lateinit var viewModel: BatteryViewModel
    private lateinit var healthScoreProgressBar: ProgressBar
    private lateinit var healthScoreTextView: TextView
    private lateinit var cycleCountTextView: TextView
    private lateinit var actualCapacityTextView: TextView
    private lateinit var chargeHabitScoreTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var healthAdviceTextView: TextView
    private lateinit var healthCalculationTextView: TextView
    private lateinit var chargingTypeExplanationTextView: TextView
    
    // 充电习惯分析相关组件
    private lateinit var totalSessionsTextView: TextView
    private lateinit var avgChargingTimeTextView: TextView
    private lateinit var avgStartLevelTextView: TextView
    private lateinit var avgEndLevelTextView: TextView
    private lateinit var peakHourTextView: TextView
    private lateinit var overnightPercentageTextView: TextView
    private lateinit var chargingRecommendationsTextView: TextView
    
    // 充电记录列表相关组件
    private lateinit var chargingSessionsRecyclerView: androidx.recyclerview.widget.RecyclerView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_health, container, false)
        
        // 初始化UI组件
        healthScoreProgressBar = view.findViewById(R.id.health_score_progress)
        healthScoreTextView = view.findViewById(R.id.health_score_text)
        cycleCountTextView = view.findViewById(R.id.cycle_count_text)
        // TODO 暂时去掉这个内容的定义与透出
        // actualCapacityTextView = view.findViewById(R.id.actual_capacity_text)
        chargeHabitScoreTextView = view.findViewById(R.id.charge_habit_score_text)
        temperatureTextView = view.findViewById(R.id.temperature_text)
        healthAdviceTextView = view.findViewById(R.id.health_advice_text)
        healthCalculationTextView = view.findViewById(R.id.health_calculation_text)
        chargingTypeExplanationTextView = view.findViewById(R.id.charging_type_explanation)
        
        // 初始化充电习惯分析相关组件
        totalSessionsTextView = view.findViewById(R.id.total_sessions_text)
        avgChargingTimeTextView = view.findViewById(R.id.avg_charging_time_text)
        avgStartLevelTextView = view.findViewById(R.id.avg_start_level_text)
        avgEndLevelTextView = view.findViewById(R.id.avg_end_level_text)
        peakHourTextView = view.findViewById(R.id.peak_hour_text)
        overnightPercentageTextView = view.findViewById(R.id.overnight_percentage_text)
        chargingRecommendationsTextView = view.findViewById(R.id.charging_recommendations_text)
        
        // 初始化充电记录列表组件
        chargingSessionsRecyclerView = view.findViewById(R.id.charging_sessions_recycler_view)
        chargingSessionsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        
        // 初始化ViewModel
        viewModel = ViewModelProvider(requireActivity()).get(BatteryViewModel::class.java)
        
        // 观察ViewModel数据变化
        observeViewModel()
        
        // 加载电池健康度数据
        viewModel.loadBatteryHealthData()
        
        // 加载充电习惯分析数据
        viewModel.loadRecentChargingSessions()
        viewModel.analyzeChargingHabits()
        
        // 设置充电类型说明
        chargingTypeExplanationTextView.text = getChargingTypeExplanation()
        
        return view
    }
    
    private fun observeViewModel() {
        viewModel.batteryHealthData.observe(viewLifecycleOwner) {
            it?.let { healthData ->
                // 综合计算电池健康度百分比
                val healthPercentage = calculateHealthPercentage(healthData)
                
                // 更新UI组件
                healthScoreProgressBar.progress = healthPercentage
                healthScoreTextView.text = "${healthPercentage}%"
                cycleCountTextView.text = "${healthData.cycleCount}"
                // actualCapacityTextView.text = "${healthData.actualCapacity} mAh"
                chargeHabitScoreTextView.text = "${healthData.chargeHabitScore}"
                temperatureTextView.text = "${healthData.temperature}°C"
                
                // 根据健康度提供建议
                val advice = getHealthAdvice(healthPercentage, healthData)
                healthAdviceTextView.text = advice
                
                // 显示健康度计算说明
                val calculationExplanation = getHealthCalculationExplanation(healthData, healthPercentage)
                healthCalculationTextView.text = calculationExplanation
            }
        }
        
        // 观察充电习惯分析数据
        viewModel.chargingHabitsAnalysis.observe(viewLifecycleOwner) {
            updateChargingHabitsAnalysisUI(it)
        }
        
        // 观察充电会话数据
        viewModel.chargingSessions.observe(viewLifecycleOwner) {
            updateChargingSessionsUI(it)
        }
    }
    
    /**
     * 更新充电习惯分析UI
     */
    private fun updateChargingHabitsAnalysisUI(analysis: com.batteryapp.model.ChargingHabitsAnalysis) {
        // 更新充电习惯统计数据
        totalSessionsTextView.text = analysis.totalSessions.toString()
        
        // 格式化充电时间（毫秒转分钟）
        val avgMinutes = (analysis.avgChargingTime / (60 * 1000)).toInt()
        avgChargingTimeTextView.text = "${avgMinutes}分钟"
        
        avgStartLevelTextView.text = "${analysis.avgStartLevel.toInt()}%"
        avgEndLevelTextView.text = "${analysis.avgEndLevel.toInt()}%"
        peakHourTextView.text = "${analysis.peakChargingHour}点"
        overnightPercentageTextView.text = "${analysis.overnightChargePercentage.toInt()}%"
        
        // 更新充电建议
        if (analysis.recommendations.isEmpty()) {
            chargingRecommendationsTextView.text = "暂无充电记录，无法提供建议"
        } else {
            val recommendationsText = analysis.recommendations.joinToString("\n• ", prefix = "• ")
            chargingRecommendationsTextView.text = recommendationsText
        }
    }
    
    /**
     * 更新充电记录列表UI
     */
    private fun updateChargingSessionsUI(sessions: List<com.batteryapp.model.ChargingSession>) {
        // 创建并设置适配器
        val adapter = ChargingSessionAdapter(sessions)
        chargingSessionsRecyclerView.adapter = adapter
    }
    
    /**
     * 综合计算电池健康度百分比
     */
    private fun calculateHealthPercentage(healthData: BatteryHealthData): Int {
        // 基于电池健康状态的基础得分
        val baseScore = when (healthData.batteryHealth) {
            2 -> 95.0 // BATTERY_HEALTH_GOOD
            3 -> 60.0 // BATTERY_HEALTH_OVERHEAT
            4 -> 20.0 // BATTERY_HEALTH_DEAD
            5 -> 50.0 // BATTERY_HEALTH_OVER_VOLTAGE
            6 -> 40.0 // BATTERY_HEALTH_UNSPECIFIED_FAILURE
            7 -> 80.0 // BATTERY_HEALTH_COLD
            else -> 70.0 // 默认值
        }
        
        // 充电循环次数影响 (假设设计寿命为500次循环)
        val cycleFactor = when {
            healthData.cycleCount <= 100 -> 1.0
            healthData.cycleCount <= 300 -> 0.95
            healthData.cycleCount <= 500 -> 0.85
            else -> 0.7
        }
        
        // 实际容量影响 (从设备获取设计容量)
        val designCapacity = viewModel.getBatteryDesignCapacity()
        val capacityFactor = minOf(healthData.actualCapacity.toDouble() / designCapacity, 1.0)
        
        // 温度影响 (理想温度范围: 20-30°C)
        val temperatureFactor = when {
            healthData.temperature < 0 || healthData.temperature > 45 -> 0.7
            healthData.temperature < 10 || healthData.temperature > 40 -> 0.85
            healthData.temperature < 20 || healthData.temperature > 30 -> 0.95
            else -> 1.0
        }
        
        // 充电习惯影响
        val chargeHabitFactor = healthData.chargeHabitScore / 100.0
        
        // 综合计算最终得分
        var finalScore = baseScore * cycleFactor * capacityFactor * temperatureFactor * chargeHabitFactor
        
        // 确保得分在0-100之间
        finalScore = Math.max(0.0, Math.min(100.0, finalScore))
        
        return finalScore.toInt()
    }
    
    /**
     * 生成电池健康度计算过程的详细说明
     */
    private fun getHealthCalculationExplanation(healthData: BatteryHealthData, finalScore: Int): String {
        val explanationBuilder = StringBuilder()
        
        // 计算各个因素的具体值
        val baseScore = when (healthData.batteryHealth) {
            2 -> 95.0 // BATTERY_HEALTH_GOOD
            3 -> 60.0 // BATTERY_HEALTH_OVERHEAT
            4 -> 20.0 // BATTERY_HEALTH_DEAD
            5 -> 50.0 // BATTERY_HEALTH_OVER_VOLTAGE
            6 -> 40.0 // BATTERY_HEALTH_UNSPECIFIED_FAILURE
            7 -> 80.0 // BATTERY_HEALTH_COLD
            else -> 70.0 // 默认值
        }
        
        val cycleFactor = when {
            healthData.cycleCount <= 100 -> 1.0
            healthData.cycleCount <= 300 -> 0.95
            healthData.cycleCount <= 500 -> 0.85
            else -> 0.7
        }
        
        val designCapacity = viewModel.getBatteryDesignCapacity()
        val capacityFactor = Math.min(healthData.actualCapacity.toDouble() / designCapacity, 1.0)
        
        val temperatureFactor = when {
            healthData.temperature < 0 || healthData.temperature > 45 -> 0.7
            healthData.temperature < 10 || healthData.temperature > 40 -> 0.85
            healthData.temperature < 20 || healthData.temperature > 30 -> 0.95
            else -> 1.0
        }
        
        val chargeHabitFactor = healthData.chargeHabitScore / 100.0
        
        // 构建说明文本
        explanationBuilder.append("电池健康度计算过程：\n\n")
        
        // 1. 基础得分
        explanationBuilder.append("1. 基础得分：${baseScore.toInt()}分\n")
        explanationBuilder.append("   - 基于电池健康状态评估\n")
        explanationBuilder.append("   - 当前状态：")
        explanationBuilder.append(when (healthData.batteryHealth) {
            2 -> "健康（BATTERY_HEALTH_GOOD）\n"
            3 -> "过热（BATTERY_HEALTH_OVERHEAT）\n"
            4 -> "损坏（BATTERY_HEALTH_DEAD）\n"
            5 -> "过电压（BATTERY_HEALTH_OVER_VOLTAGE）\n"
            6 -> "不明故障（BATTERY_HEALTH_UNSPECIFIED_FAILURE）\n"
            7 -> "低温（BATTERY_HEALTH_COLD）\n"
            else -> "未知状态\n"
        })
        
        // 2. 充电循环次数影响
        explanationBuilder.append("\n2. 充电循环次数影响：${(cycleFactor * 100).toInt()}%\n")
        explanationBuilder.append("   - 当前循环次数：${healthData.cycleCount}次\n")
        explanationBuilder.append("   - 影响程度：")
        explanationBuilder.append(when {
            healthData.cycleCount <= 100 -> "无影响（≤100次）\n"
            healthData.cycleCount <= 300 -> "轻微影响（101-300次）\n"
            healthData.cycleCount <= 500 -> "中等影响（301-500次）\n"
            else -> "显著影响（>500次）\n"
        })
        
        // 3. 实际容量影响
        explanationBuilder.append("\n3. 实际容量影响：${(capacityFactor * 100).toInt()}%\n")
        explanationBuilder.append("   - 当前实际容量：${healthData.actualCapacity} mAh\n")
        explanationBuilder.append("   - 设计容量：${designCapacity.toInt()} mAh\n")
        explanationBuilder.append("   - 容量保持率：${(capacityFactor * 100).toInt()}%\n")
        
        // 4. 温度影响
        explanationBuilder.append("\n4. 温度影响：${(temperatureFactor * 100).toInt()}%\n")
        explanationBuilder.append("   - 当前温度：${healthData.temperature}°C\n")
        explanationBuilder.append("   - 影响程度：")
        explanationBuilder.append(when {
            healthData.temperature < 0 || healthData.temperature > 45 -> "严重影响（<0°C或>45°C）\n"
            healthData.temperature < 10 || healthData.temperature > 40 -> "中等影响（<10°C或>40°C）\n"
            healthData.temperature < 20 || healthData.temperature > 30 -> "轻微影响（<20°C或>30°C）\n"
            else -> "无影响（20-30°C，理想范围）\n"
        })
        
        // 5. 充电习惯影响
        explanationBuilder.append("\n5. 充电习惯影响：${healthData.chargeHabitScore}%\n")
        explanationBuilder.append("   - 基于您的充电行为模式评估\n")
        explanationBuilder.append("   - 评估维度：充电频率、充电时间、充电速率等\n")
        
        // 6. 综合计算
        explanationBuilder.append("\n6. 综合计算：\n")
        explanationBuilder.append("   - 基础得分 × 循环次数系数 × 容量系数 × 温度系数 × 充电习惯系数\n")
        explanationBuilder.append("   - ${baseScore.toInt()} × ${String.format("%.2f", cycleFactor)} × ${String.format("%.2f", capacityFactor)} × ${String.format("%.2f", temperatureFactor)} × ${String.format("%.2f", chargeHabitFactor)}\n")
        explanationBuilder.append("   - = ${String.format("%.2f", baseScore * cycleFactor * capacityFactor * temperatureFactor * chargeHabitFactor)}\n")
        explanationBuilder.append("   - 最终得分：${finalScore}分\n")
        
        return explanationBuilder.toString()
    }
    
    private fun getChargingTypeExplanation(): String {
        val explanationBuilder = StringBuilder()
        
        explanationBuilder.append("1. 慢充 (Trickle Charge / Standard Charge)\n")
        explanationBuilder.append("   类比：用一根小水管，以恒定、平缓的水流注水。\n")
        explanationBuilder.append("   技术解释：通常指功率在10W-15W以下的充电。使用标准的5V电压和较低的电流，整个过程电压电流基本不变，直到快满时才略微调整。\n")
        explanationBuilder.append("   优点：发热小，对电池的化学压力最小，有利于长期电池健康。\n")
        explanationBuilder.append("   缺点：速度慢。\n")
        explanationBuilder.append("   何时发生：使用不支持快充的旧充电器；或手机/充电器有一方不支持对方的快充协议时，会自动回落到5V慢充。\n\n")
        
        explanationBuilder.append("2. 快充 (Quick Charge / Fast Charge)\n")
        explanationBuilder.append("   类比：前期用消防水管猛灌，当水位快满时，自动换回小水管。\n")
        explanationBuilder.append("   技术解释：指通过提升电压和/或电流，将充电功率提升到18W以上的技术。它不是从头到尾全功率，而是一个智能的、多阶段的过程：\n")
        explanationBuilder.append("   - 阶段1：恒流预充：如果电池电量极低，会以小电流先激活。\n")
        explanationBuilder.append("   - 阶段2：恒流快充 (核心阶段)：充电器和手机协商出一个最高的安全功率（如27W、65W），在此阶段以最大功率充电，电量从0%迅速冲到50%-70%。\n")
        explanationBuilder.append("   - 阶段3：恒压减流：当电池电压达到上限（约4.2V-4.4V），充电器保持电压不变，电流开始逐渐减小。功率也随之下降。此阶段电量从70%充到90%以上。\n")
        explanationBuilder.append("   优点：极大缩短充电时间，解决续航焦虑。\n")
        explanationBuilder.append("   缺点：相对慢充会产生更多热量，对电池的长期健康有轻微影响（但现代手机的管理系统已将其控制在安全范围内）。\n\n")
        
        explanationBuilder.append("3. 涓流充电 (Trickle Charge / Maintenance Charge)\n")
        explanationBuilder.append("   类比：水池即将灌满，改用滴管一滴一滴地加，直到完全精确满盈。\n")
        explanationBuilder.append("   技术解释：这是充电的最后阶段（通常是95%或98%以后）。此时充电功率极低（可能只有1-2W），以非常微小的电流慢慢将电池充至100%。\n")
        explanationBuilder.append("   目的：\n")
        explanationBuilder.append("   - 保护电池：避免在电池已接近满电时继续大电流冲击，减少电池压力和发热。\n")
        explanationBuilder.append("   - 校准电量计：让手机更精确地判断\"100%\"这个点。\n")
        explanationBuilder.append("   智能运用：手机的\"优化电池充电\"功能，就是利用了涓流充电的原理。它先快速充到80%，然后暂停，在你起床前再用涓流慢慢充满最后20%，从而缩短电池处于100%高压状态的时间。\n\n")
        
        explanationBuilder.append("核心结论与建议：\n")
        explanationBuilder.append("1. 协议匹配是关键：想要实现快充，必须确保手机支持、充电器支持、数据线也支持同一快充协议。否则会自动降级为慢充。\n")
        explanationBuilder.append("2. 看功率(W)比单独看V或A更有意义：功率直接反映了充电速度。一个30W的充电器通常比18W的快。\n")
        explanationBuilder.append("3. 通用性选择：目前USB PD (含PPS) 协议是最通用、前景最广的快充标准，被苹果、谷歌、三星及众多笔记本厂商支持。\n")
        explanationBuilder.append("4. 不必恐惧快充伤电池：在手机厂商严格的温控和智能管理下，快充对电池寿命的额外损耗非常有限。")
        
        return explanationBuilder.toString()
    }
    
    private fun getHealthAdvice(healthScore: Int, healthData: BatteryHealthData): String {
        val adviceBuilder = StringBuilder()
        
        // 基本健康状况建议
        when {
            healthScore >= 90 -> adviceBuilder.append("电池健康状况良好，")
            healthScore >= 70 -> adviceBuilder.append("电池健康状况一般，")
            healthScore >= 50 -> adviceBuilder.append("电池健康状况较差，")
            else -> adviceBuilder.append("电池健康状况严重不良，")
        }
        
        // 基于温度的建议
        when {
            healthData.temperature < 0 || healthData.temperature > 45 -> 
                adviceBuilder.append("当前温度过高/过低，建议在20-30°C的环境中使用手机。")
            healthData.temperature < 10 || healthData.temperature > 40 -> 
                adviceBuilder.append("当前温度偏离理想范围，尽量避免在极端温度下使用手机。")
            else -> adviceBuilder.append("当前温度处于理想范围。")
        }
        
        adviceBuilder.append("\n\n")
        
        // 基于循环次数的建议
        when {
            healthData.cycleCount > 500 -> 
                adviceBuilder.append("充电循环次数已超过500次，电池性能可能明显下降，建议考虑更换电池。")
            healthData.cycleCount > 300 -> 
                adviceBuilder.append("充电循环次数已超过300次，建议减少电池深度放电，尽量保持20%-80%电量。")
            healthData.cycleCount > 100 -> 
                adviceBuilder.append("充电循环次数正常，继续保持良好的充电习惯。")
            else -> 
                adviceBuilder.append("充电循环次数较少，电池处于最佳状态。")
        }
        
        adviceBuilder.append("\n\n")
        
        // 基于实际容量的建议
        val designCapacity = viewModel.getBatteryDesignCapacity()
        val capacityPercentage = (healthData.actualCapacity / designCapacity * 100).toInt()
        
        when {
            capacityPercentage < 70 -> 
                adviceBuilder.append("电池实际容量已降至设计容量的${capacityPercentage}%，建议更换电池以获得更好的续航体验。")
            capacityPercentage < 85 -> 
                adviceBuilder.append("电池实际容量为设计容量的${capacityPercentage}%，建议减少后台应用运行，优化电池使用。")
            else -> 
                adviceBuilder.append("电池实际容量保持良好，继续保持现有的使用习惯。")
        }
        
        adviceBuilder.append("\n\n")
        
        // 基于充电习惯的建议
        when {
            healthData.chargeHabitScore < 60 -> 
                adviceBuilder.append("充电习惯评分较低，建议避免整夜充电、过度放电和频繁快充。")
            healthData.chargeHabitScore < 80 -> 
                adviceBuilder.append("充电习惯评分一般，建议尽量使用原装充电器，避免在充电时玩大型游戏。")
            else -> 
                adviceBuilder.append("充电习惯良好，请继续保持。")
        }
        
        // 通用建议
        adviceBuilder.append("\n\n")
        adviceBuilder.append("通用建议：")
        adviceBuilder.append("\n1. 避免将手机暴露在高温或低温环境中")
        adviceBuilder.append("\n2. 尽量使用原装充电器和数据线")
        adviceBuilder.append("\n3. 避免长时间深度放电（电量低于20%）")
        adviceBuilder.append("\n4. 减少快充次数，优先使用标准充电")
        adviceBuilder.append("\n5. 充电时尽量避免使用手机")
        
        return adviceBuilder.toString()
    }
}
