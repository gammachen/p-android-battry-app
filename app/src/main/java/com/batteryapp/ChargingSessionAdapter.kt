package com.batteryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.batteryapp.model.ChargingSession
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 充电会话列表适配器
 */
class ChargingSessionAdapter(
    private val chargingSessions: List<ChargingSession>
) : RecyclerView.Adapter<ChargingSessionAdapter.ChargingSessionViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 视图持有者，用于缓存列表项的视图组件
     */
    inner class ChargingSessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chargingTimeRange: TextView = itemView.findViewById(R.id.charging_time_range)
        val chargerType: TextView = itemView.findViewById(R.id.charger_type)
        val chargingMode: TextView = itemView.findViewById(R.id.charging_mode)
        val startLevel: TextView = itemView.findViewById(R.id.start_level)
        val endLevel: TextView = itemView.findViewById(R.id.end_level)
        val chargingDuration: TextView = itemView.findViewById(R.id.charging_duration)
        val maxTemperature: TextView = itemView.findViewById(R.id.max_temperature)
        val chargingSpeed: TextView = itemView.findViewById(R.id.charging_speed)
    }

    /**
     * 创建视图持有者
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChargingSessionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_charging_session, parent, false)
        return ChargingSessionViewHolder(itemView)
    }

    /**
     * 绑定数据到视图持有者
     */
    override fun onBindViewHolder(holder: ChargingSessionViewHolder, position: Int) {
        val session = chargingSessions[position]
        
        // 格式化充电时间范围
        val startTimeStr = dateFormat.format(session.startTime)
        val endTimeStr = dateFormat.format(session.endTime)
        holder.chargingTimeRange.text = "$startTimeStr - $endTimeStr"
        
        // 显示充电器类型
        holder.chargerType.text = session.chargerType
        
        // 显示充电模式
        val modeText = when (session.chargingMode) {
            com.batteryapp.model.ChargingMode.Mode.TRICKLE.name -> holder.itemView.context.getString(R.string.charging_mode_trickle)
            com.batteryapp.model.ChargingMode.Mode.SLOW.name -> holder.itemView.context.getString(R.string.charging_mode_slow)
            com.batteryapp.model.ChargingMode.Mode.NORMAL.name -> holder.itemView.context.getString(R.string.charging_mode_normal)
            com.batteryapp.model.ChargingMode.Mode.FAST.name -> holder.itemView.context.getString(R.string.charging_mode_fast)
            com.batteryapp.model.ChargingMode.Mode.SUPER_FAST.name -> holder.itemView.context.getString(R.string.charging_mode_super_fast)
            com.batteryapp.model.ChargingMode.Mode.ULTRA_FAST.name -> holder.itemView.context.getString(R.string.charging_mode_ultra_fast)
            else -> holder.itemView.context.getString(R.string.unknown)
        }
        holder.chargingMode.text = modeText
        
        // 显示充电开始和结束电量
        holder.startLevel.text = "${session.startLevel}%"
        holder.endLevel.text = "${session.endLevel}%"
        
        // 显示充电时长
        val durationMinutes = (session.duration / (60 * 1000)).toInt()
        val durationStr = when {
            durationMinutes < 60 -> "${durationMinutes}分钟"
            else -> "${durationMinutes / 60}小时${durationMinutes % 60}分钟"
        }
        holder.chargingDuration.text = durationStr
        
        // 显示最高温度
        holder.maxTemperature.text = "${session.maxTemperature}°C"
        
        // 计算并显示充电速度
        val chargeAmount = session.endLevel - session.startLevel
        val chargingSpeed = if (durationMinutes > 0) {
            (chargeAmount.toDouble() / durationMinutes).toInt()
        } else {
            0
        }
        holder.chargingSpeed.text = "${chargingSpeed}%/分钟"
    }

    /**
     * 获取列表项数量
     */
    override fun getItemCount(): Int {
        return chargingSessions.size
    }
}
