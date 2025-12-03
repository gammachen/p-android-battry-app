package com.batteryapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.batteryapp.model.AppBatteryUsage
import com.batteryapp.data.BatteryRepository
import com.batteryapp.viewmodel.BatteryViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 电池耗电排行榜Fragment，显示应用耗电情况
 */
class BatteryUsageFragment : Fragment() {
    
    private lateinit var viewModel: BatteryViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: AppUsagePagerAdapter
    // 只保留"总耗电量"标签页
    private val tabTitles = arrayOf("总耗电量")
    
    private var appUsageLists = mapOf(
        BatteryRepository.BatteryUsageRankType.TOTAL_USAGE to emptyList<AppBatteryUsage>()
        // 注释掉后台运行时长和唤醒锁时间
        // BatteryRepository.BatteryUsageRankType.BACKGROUND_USAGE to emptyList<AppBatteryUsage>(),
        // BatteryRepository.BatteryUsageRankType.WAKELOCK_TIME to emptyList<AppBatteryUsage>()
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_battery_usage, container, false)
        
        // 初始化UI组件
        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)
        progressBar = view.findViewById(R.id.progressBar)
        
        // 初始化ViewModel
        viewModel = ViewModelProvider(requireActivity()).get(BatteryViewModel::class.java)
        
        // 初始化ViewPager2适配器
        adapter = AppUsagePagerAdapter(requireActivity(), appUsageLists)
        viewPager.adapter = adapter
        
        // 关联TabLayout和ViewPager2
        TabLayoutMediator(tabLayout, viewPager) {
                tab, position -> tab.text = tabTitles[position]
        }.attach()
        
        // 观察ViewModel数据变化
        observeViewModel()
        
        // 初始加载所有数据
        loadAllAppUsageData()
        
        return view
    }
    
    private fun observeViewModel() {
        viewModel.appBatteryUsage.observe(viewLifecycleOwner) {
            // 计算总使用时间
            val totalUsageTime = it.sumOf { appUsage -> appUsage.totalUsage }
            Log.d("BatteryUsageFragment", "总使用时间: $totalUsageTime")
            
            // 只更新总耗电量标签页的数据
            val newMap = appUsageLists.toMutableMap()
            newMap[BatteryRepository.BatteryUsageRankType.TOTAL_USAGE] = it
            appUsageLists = newMap
            adapter.updateData(appUsageLists)
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) {
            // 显示或隐藏加载指示器
            progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }
    }
    
    private fun loadAllAppUsageData() {
        // 初始加载当前选中标签页的数据
        val currentPosition = viewPager.currentItem
        loadAppUsageDataByPosition(currentPosition)
        
        // 设置ViewPager2页面切换监听器
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 切换标签页时加载对应的数据
                loadAppUsageDataByPosition(position)
            }
        })
    }
    
    private fun loadAppUsageDataByPosition(position: Int) {
        // 只处理总耗电量标签页（position 0）
        val rankType = BatteryRepository.BatteryUsageRankType.TOTAL_USAGE
        viewModel.loadAppBatteryUsageRanking(rankType)
    }
    
    // ViewPager2适配器
    private inner class AppUsagePagerAdapter(
        fragmentActivity: FragmentActivity,
        private var data: Map<BatteryRepository.BatteryUsageRankType, List<AppBatteryUsage>>
    ) : FragmentStateAdapter(fragmentActivity) {
        
        // 保存已创建的Fragment实例
        private val fragmentMap = mutableMapOf<Int, AppUsageListFragment>()
        
        override fun getItemCount(): Int = data.size
        
        override fun createFragment(position: Int): Fragment {
            // 只处理总耗电量标签页（position 0）
            val rankType = BatteryRepository.BatteryUsageRankType.TOTAL_USAGE
            val fragment = AppUsageListFragment.newInstance(data[rankType] ?: emptyList())
            fragmentMap[position] = fragment
            return fragment
        }
        
        fun updateData(newData: Map<BatteryRepository.BatteryUsageRankType, List<AppBatteryUsage>>) {
            data = newData
            
            // 更新已创建的Fragment实例的数据
            fragmentMap.forEach { (position, fragment) ->
                // 只处理总耗电量标签页（position 0）
                val rankType = BatteryRepository.BatteryUsageRankType.TOTAL_USAGE
                val updatedData = data[rankType] ?: emptyList()
                fragment.updateData(updatedData)
            }
            
            notifyDataSetChanged()
        }
    }
}
