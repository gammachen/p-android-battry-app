package com.batteryapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.batteryapp.viewmodel.BatteryViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

/**
 * 电池历史趋势Fragment，显示电池使用历史趋势图表
 */
class HistoryFragment : Fragment() {
    
    private lateinit var viewModel: BatteryViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: HistoryPagerAdapter
    private val tabTitles = arrayOf("电量消耗", "使用时长", "充电次数", "充电速度", "电池温度")
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        
        // 初始化UI组件
        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)
        progressBar = view.findViewById(R.id.progressBar)
        
        // 初始化ViewModel
        viewModel = ViewModelProvider(requireActivity()).get(BatteryViewModel::class.java)
        
        // 初始化ViewPager2适配器
        adapter = HistoryPagerAdapter(this)
        viewPager.adapter = adapter
        
        // 关联TabLayout和ViewPager2
        TabLayoutMediator(tabLayout, viewPager) {
                tab, position -> tab.text = tabTitles[position]
        }.attach()
        
        // 观察ViewModel数据变化
        observeViewModel()
        
        // 加载历史数据
        viewModel.loadRecentBatteryHistory()
        
        return view
    }
    
    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) {
            // 显示或隐藏加载指示器
            progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }
    }
    
    // ViewPager2适配器
    private inner class HistoryPagerAdapter(
        fragment: Fragment
    ) : FragmentStateAdapter(fragment) {
        
        override fun getItemCount(): Int = tabTitles.size
        
        override fun createFragment(position: Int): Fragment {
            // 为每个标签页创建对应的图表Fragment
            return HistoryChartFragment.newInstance(position)
        }
    }
    
    companion object {
        /**
         * 历史图表类型枚举
         */
        enum class ChartType {
            BATTERY_LEVEL, // 电量消耗
            USAGE_DURATION, // 使用时长
            CHARGE_COUNT, // 充电次数
            CHARGE_SPEED, // 充电速度
            TEMPERATURE // 电池温度
        }
    }
}