package com.batteryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.batteryapp.data.BatteryRepository
import com.batteryapp.model.AppBatteryUsage

class AppBatteryUsageAdapter(
    private val appList: List<AppBatteryUsage>,
    private val totalUsage: Double = 0.0, // 总使用量，用于计算百分比
    private val rankType: BatteryRepository.BatteryUsageRankType = BatteryRepository.BatteryUsageRankType.TOTAL_USAGE
) : RecyclerView.Adapter<AppBatteryUsageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
        val tvUsageValue: TextView = view.findViewById(R.id.tv_usage_value)
        val tvUsageUnit: TextView = view.findViewById(R.id.tv_usage_unit)
        val tvRank: TextView = view.findViewById(R.id.tv_rank)
        val tvUsagePercentage: TextView = view.findViewById(R.id.tv_usage_percentage) // 新增：显示百分比
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_battery_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appUsage = appList[position]
        holder.tvAppName.text = appUsage.appName
        
        // 根据不同的排序类型显示不同的单位和数值
        when (rankType) {
            BatteryRepository.BatteryUsageRankType.TOTAL_USAGE -> {
                // 总使用时间（秒）
                holder.tvUsageValue.text = String.format("%.2f", appUsage.backgroundUsage + appUsage.screenOnUsage)
                holder.tvUsageUnit.text = "秒"
            }
            BatteryRepository.BatteryUsageRankType.BACKGROUND_USAGE -> {
                // 后台使用时间（秒）
                holder.tvUsageValue.text = String.format("%.2f", appUsage.backgroundUsage)
                holder.tvUsageUnit.text = "秒"
            }
            BatteryRepository.BatteryUsageRankType.WAKELOCK_TIME -> {
                // 唤醒锁时间（毫秒）
                holder.tvUsageValue.text = String.format("%d", appUsage.wakelockTime)
                holder.tvUsageUnit.text = "ms"
            }
            BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION -> {
                // 预估耗电量（mAh）
                holder.tvUsageValue.text = String.format("%.2f", appUsage.totalUsage)
                holder.tvUsageUnit.text = "mAh"
            }
        }
        
        // 计算并显示百分比
        val percentage = if (totalUsage > 0.0) {
            (appUsage.totalUsage / totalUsage) * 100
        } else {
            0.0
        }
        holder.tvUsagePercentage.text = String.format("%.1f%%", percentage)
        
        holder.tvRank.text = (position + 1).toString()
    }

    override fun getItemCount() = appList.size
}