package com.batteryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.batteryapp.model.RunningAppInfo

/**
 * 运行中的APP列表适配器
 */
class RunningAppAdapter(private var runningApps: List<RunningAppInfo>) : RecyclerView.Adapter<RunningAppAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tv_rank)
        val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
        val tvStatus: TextView = view.findViewById(R.id.tv_status)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_running_app, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = runningApps[position]
        holder.tvRank.text = (position + 1).toString()
        holder.tvAppName.text = app.appName
        holder.tvStatus.text = if (app.isForeground) "前台" else "后台"
    }
    
    override fun getItemCount() = runningApps.size
    
    fun updateData(newData: List<RunningAppInfo>) {
        runningApps = newData
        notifyDataSetChanged()
    }
}
