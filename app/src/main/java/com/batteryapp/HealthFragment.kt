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
        avgChargingTimeTextView.text = "$avgMinutes${getString(R.string.health_minutes)}"
        
        avgStartLevelTextView.text = "${analysis.avgStartLevel.toInt()}%"
        avgEndLevelTextView.text = "${analysis.avgEndLevel.toInt()}%"
        peakHourTextView.text = "${analysis.peakChargingHour}${getString(R.string.health_hour_unit)}"
        overnightPercentageTextView.text = "${analysis.overnightChargePercentage.toInt()}%"
        
        // 更新充电建议
        if (analysis.recommendations.isEmpty()) {
            chargingRecommendationsTextView.text = getString(R.string.health_no_charging_data)
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
        explanationBuilder.append(getString(R.string.health_calc_process)).append("\n\n")
        
        // 1. 基础得分
        explanationBuilder.append(String.format(getString(R.string.health_calc_base_score), baseScore.toInt())).append("\n")
        explanationBuilder.append("   - ").append(getString(R.string.health_calc_based_on)).append("\n")
        explanationBuilder.append("   - ").append(getString(R.string.health_calc_current_status))
        explanationBuilder.append(when (healthData.batteryHealth) {
            2 -> getString(R.string.health_status_good) + "\n"
            3 -> getString(R.string.health_status_overheat) + "\n"
            4 -> getString(R.string.health_status_dead) + "\n"
            5 -> getString(R.string.health_status_over_voltage) + "\n"
            6 -> getString(R.string.health_status_unspecified_failure) + "\n"
            7 -> getString(R.string.health_status_cold) + "\n"
            else -> getString(R.string.health_status_unknown) + "\n"
        })
        
        // 2. 充电循环次数影响
        explanationBuilder.append("\n").append(String.format(getString(R.string.health_calc_cycle_impact), (cycleFactor * 100).toInt())).append("\n")
        explanationBuilder.append("   - ").append(String.format(getString(R.string.health_calc_current_cycles), healthData.cycleCount)).append("\n")
        explanationBuilder.append("   - ").append(getString(R.string.health_calc_impact_level))
        explanationBuilder.append(when {
            healthData.cycleCount <= 100 -> getString(R.string.health_cycle_impact_none) + "\n"
            healthData.cycleCount <= 300 -> getString(R.string.health_cycle_impact_minor) + "\n"
            healthData.cycleCount <= 500 -> getString(R.string.health_cycle_impact_moderate) + "\n"
            else -> getString(R.string.health_cycle_impact_significant) + "\n"
        })
        
        // 3. 实际容量影响
        explanationBuilder.append("\n").append(String.format(getString(R.string.health_calc_capacity_impact), (capacityFactor * 100).toInt())).append("\n")
        explanationBuilder.append("   - ").append(String.format(getString(R.string.health_calc_current_capacity), healthData.actualCapacity)).append("\n")
        explanationBuilder.append("   - ").append(String.format(getString(R.string.health_calc_design_capacity), designCapacity.toInt())).append("\n")
        explanationBuilder.append("   - ").append(String.format(getString(R.string.health_calc_capacity_retention), (capacityFactor * 100).toInt())).append("\n")
        
        // 4. 温度影响
        explanationBuilder.append("\n").append(String.format(getString(R.string.health_calc_temperature_impact), (temperatureFactor * 100).toInt())).append("\n")
        explanationBuilder.append("   - ").append(String.format(getString(R.string.health_calc_current_temp), healthData.temperature)).append("\n")
        explanationBuilder.append("   - ").append(getString(R.string.health_calc_impact_level))
        explanationBuilder.append(when {
            healthData.temperature < 0 || healthData.temperature > 45 -> getString(R.string.health_temperature_impact_severe) + "\n"
            healthData.temperature < 10 || healthData.temperature > 40 -> getString(R.string.health_temperature_impact_moderate) + "\n"
            healthData.temperature < 20 || healthData.temperature > 30 -> getString(R.string.health_temperature_impact_minor) + "\n"
            else -> getString(R.string.health_temperature_impact_none) + "\n"
        })
        
        // 5. 充电习惯影响
        explanationBuilder.append("\n").append(String.format(getString(R.string.health_calc_charge_habit_impact), healthData.chargeHabitScore)).append("\n")
        explanationBuilder.append("   - ").append(getString(R.string.health_calc_assessment_based)).append("\n")
        explanationBuilder.append("   - ").append(getString(R.string.health_calc_assessment_dimensions)).append("\n")
        
        // 6. 综合计算
        explanationBuilder.append("\n").append(getString(R.string.health_calc_comprehensive)).append("\n")
        explanationBuilder.append("   - ").append(getString(R.string.health_calc_formula)).append("\n")
        explanationBuilder.append("   - ").append(String.format(getString(R.string.health_calc_calculation), baseScore.toInt(), cycleFactor, capacityFactor, temperatureFactor, chargeHabitFactor)).append("\n")
        explanationBuilder.append("   - = ${String.format("%.2f", baseScore * cycleFactor * capacityFactor * temperatureFactor * chargeHabitFactor)}\n")
        explanationBuilder.append("   - ").append(String.format(getString(R.string.health_calc_final_score), finalScore)).append("\n")
        
        return explanationBuilder.toString()
    }
    
    private fun getChargingTypeExplanation(): String {
        val explanationBuilder = StringBuilder()
        
        explanationBuilder.append("1. ").append(getString(R.string.health_charging_type_slow)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_slow_analogy)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_slow_tech)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_slow_advantages)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_slow_disadvantages)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_slow_when)).append("\n\n")
        
        explanationBuilder.append("2. ").append(getString(R.string.health_charging_type_fast)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_fast_analogy)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_fast_tech)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_fast_stage1)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_fast_stage2)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_fast_stage3)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_fast_advantages)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_fast_disadvantages)).append("\n\n")
        
        explanationBuilder.append("3. ").append(getString(R.string.health_charging_type_trickle)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_trickle_analogy)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_trickle_tech)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_trickle_purpose)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_trickle_purpose_protect)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_trickle_purpose_calibrate)).append("\n")
        explanationBuilder.append("   ").append(getString(R.string.health_charging_type_trickle_smart)).append("\n\n")
        
        explanationBuilder.append(getString(R.string.health_charging_type_conclusion)).append("\n")
        explanationBuilder.append(getString(R.string.health_charging_type_conclusion_1)).append("\n")
        explanationBuilder.append(getString(R.string.health_charging_type_conclusion_2)).append("\n")
        explanationBuilder.append(getString(R.string.health_charging_type_conclusion_3)).append("\n")
        explanationBuilder.append(getString(R.string.health_charging_type_conclusion_4))
        
        return explanationBuilder.toString()
    }
    
    private fun getHealthAdvice(healthScore: Int, healthData: BatteryHealthData): String {
        val adviceBuilder = StringBuilder()
        
        // 基本健康状况建议
        when {
            healthScore >= 90 -> adviceBuilder.append(getString(R.string.health_advice_excellent))
            healthScore >= 70 -> adviceBuilder.append(getString(R.string.health_advice_good_text))
            healthScore >= 50 -> adviceBuilder.append(getString(R.string.health_advice_fair))
            else -> adviceBuilder.append(getString(R.string.health_advice_poor))
        }
        
        // 基于温度的建议
        when {
            healthData.temperature < 0 || healthData.temperature > 45 -> 
                adviceBuilder.append(getString(R.string.health_advice_temperature_extreme))
            healthData.temperature < 10 || healthData.temperature > 40 -> 
                adviceBuilder.append(getString(R.string.health_advice_temperature_moderate))
            else -> adviceBuilder.append(getString(R.string.health_advice_temperature_ideal))
        }
        
        adviceBuilder.append("\n\n")
        
        // 基于循环次数的建议
        when {
            healthData.cycleCount > 500 -> 
                adviceBuilder.append(getString(R.string.health_advice_cycle_over_500))
            healthData.cycleCount > 300 -> 
                adviceBuilder.append(getString(R.string.health_advice_cycle_over_300))
            healthData.cycleCount > 100 -> 
                adviceBuilder.append(getString(R.string.health_advice_cycle_normal))
            else -> 
                adviceBuilder.append(getString(R.string.health_advice_cycle_few))
        }
        
        adviceBuilder.append("\n\n")
        
        // 基于实际容量的建议
        val designCapacity = viewModel.getBatteryDesignCapacity()
        val capacityPercentage = (healthData.actualCapacity / designCapacity * 100).toInt()
        
        when {
            capacityPercentage < 70 -> 
                adviceBuilder.append(String.format(getString(R.string.health_advice_capacity_below_70), capacityPercentage))
            capacityPercentage < 85 -> 
                adviceBuilder.append(String.format(getString(R.string.health_advice_capacity_below_85), capacityPercentage))
            else -> 
                adviceBuilder.append(getString(R.string.health_advice_capacity_good))
        }
        
        adviceBuilder.append("\n\n")
        
        // 基于充电习惯的建议
        when {
            healthData.chargeHabitScore < 60 -> 
                adviceBuilder.append(getString(R.string.health_advice_habit_poor))
            healthData.chargeHabitScore < 80 -> 
                adviceBuilder.append(getString(R.string.health_advice_habit_fair))
            else -> 
                adviceBuilder.append(getString(R.string.health_advice_habit_good))
        }
        
        // 通用建议
        adviceBuilder.append("\n\n")
        adviceBuilder.append(getString(R.string.health_advice_general))
        adviceBuilder.append("\n").append(getString(R.string.health_advice_general_1))
        adviceBuilder.append("\n").append(getString(R.string.health_advice_general_2))
        adviceBuilder.append("\n").append(getString(R.string.health_advice_general_3))
        adviceBuilder.append("\n").append(getString(R.string.health_advice_general_4))
        adviceBuilder.append("\n").append(getString(R.string.health_advice_general_5))
        
        return adviceBuilder.toString()
    }
}
