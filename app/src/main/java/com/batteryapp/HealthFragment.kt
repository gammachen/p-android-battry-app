package com.batteryapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.batteryapp.viewmodel.BatteryViewModel

/**
 * 电池健康评估Fragment，显示电池健康度评估与预测
 */
class HealthFragment : Fragment() {
    
    private lateinit var viewModel: BatteryViewModel
    // private lateinit var healthScoreProgressBar: ProgressBar
    // private lateinit var healthScoreTextView: TextView
    private lateinit var cycleCountTextView: TextView
    private lateinit var actualCapacityTextView: TextView
    private lateinit var chargeHabitScoreTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var healthAdviceTextView: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_health, container, false)
        
        // 初始化UI组件
        // healthScoreProgressBar = view.findViewById(R.id.health_score_progress)
        // healthScoreTextView = view.findViewById(R.id.health_score_text)
        cycleCountTextView = view.findViewById(R.id.cycle_count_text)
        actualCapacityTextView = view.findViewById(R.id.actual_capacity_text)
        chargeHabitScoreTextView = view.findViewById(R.id.charge_habit_score_text)
        temperatureTextView = view.findViewById(R.id.temperature_text)
        healthAdviceTextView = view.findViewById(R.id.health_advice_text)
        
        // 初始化ViewModel
        viewModel = ViewModelProvider(requireActivity()).get(BatteryViewModel::class.java)
        
        // 观察ViewModel数据变化
        observeViewModel()
        
        // 加载电池健康度数据
        viewModel.loadBatteryHealthData()
        
        return view
    }
    
    private fun observeViewModel() {
        viewModel.batteryHealthData.observe(viewLifecycleOwner) {
            it?.let { healthData ->
                // 将电池健康状态映射为百分比显示
                val healthPercentage = when (healthData.batteryHealth) {
                    2 -> 95 // BATTERY_HEALTH_GOOD
                    3 -> 60 // BATTERY_HEALTH_OVERHEAT
                    4 -> 20 // BATTERY_HEALTH_DEAD
                    5 -> 50 // BATTERY_HEALTH_OVER_VOLTAGE
                    6 -> 40 // BATTERY_HEALTH_UNSPECIFIED_FAILURE
                    7 -> 80 // BATTERY_HEALTH_COLD
                    else -> 70 // 默认值
                }
                
                // 更新UI组件
                // healthScoreProgressBar.progress = healthPercentage
                // healthScoreTextView.text = "${healthPercentage}%"
                cycleCountTextView.text = "${healthData.cycleCount}"
                actualCapacityTextView.text = "${healthData.actualCapacity} mAh"
                chargeHabitScoreTextView.text = "${healthData.chargeHabitScore}"
                temperatureTextView.text = "${healthData.temperature}°C"
                
                // 根据健康度提供建议
                val advice = getHealthAdvice(healthPercentage)
                healthAdviceTextView.text = advice
            }
        }
    }
    
    private fun getHealthAdvice(healthScore: Int): String {
        return when {
            healthScore >= 90 -> "电池健康状况良好，请继续保持良好的充电习惯。"
            healthScore >= 70 -> "电池健康状况一般，建议减少快充次数，避免过度充电。"
            healthScore >= 50 -> "电池健康状况较差，建议避免高温环境，及时更换电池。"
            else -> "电池健康状况严重不良，建议尽快更换电池。"
        }
    }
}
