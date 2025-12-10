package com.sf.batteryapp

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
import com.sf.batteryapp.model.AppBatteryUsage
import com.sf.batteryapp.data.BatteryRepository
import com.sf.batteryapp.viewmodel.BatteryViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.sf.batteryapp.RunningAppFragment

/**
 * 电池耗电排行榜Fragment，显示应用耗电情况
 */
class BatteryUsageFragment : Fragment() {
    
    private lateinit var viewModel: BatteryViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: AppUsagePagerAdapter
    // 添加"按预估电量"标签页和"运行中的APP"标签页
    private lateinit var tabTitles: Array<String>
    
    private var appUsageLists = mapOf(
        BatteryRepository.BatteryUsageRankType.TIME_USAGE to emptyList<AppBatteryUsage>(),
        BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION to emptyList<AppBatteryUsage>()
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
        
        // 初始化标签页标题
        tabTitles = arrayOf(
            getString(R.string.tab_title_total_usage),
            getString(R.string.tab_title_estimated_consumption),
            getString(R.string.tab_title_running_apps)
        )
        
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
            
            // 更新总耗电量标签页的数据
            val newMap = appUsageLists.toMutableMap()
            newMap[BatteryRepository.BatteryUsageRankType.TIME_USAGE] = it
            
            // 简单估算应用耗电量：基于使用时长和唤醒锁时间
            // 计算方法：(totalUsage * 0.7) + (wakelockTime / 1000.0 * 0.3)
            val estimatedConsumptionList = it.sortedByDescending { appUsage ->
                (appUsage.totalUsage * 0.7) + (appUsage.wakelockTime / 1000.0 * 0.3)
            }
            newMap[BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION] = estimatedConsumptionList
            
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
        // 只在第一和第二个Tab加载电池使用数据，第三个Tab（运行中的APP）不需要
        if (position <= 1) {
            // 根据位置选择对应的排名类型
            val rankType = when (position) {
                0 -> BatteryRepository.BatteryUsageRankType.TIME_USAGE
                1 -> BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION
                else -> BatteryRepository.BatteryUsageRankType.TIME_USAGE
            }
            viewModel.loadAppBatteryUsageRanking(rankType)
        }
    }
    
    // ViewPager2适配器
    private inner class AppUsagePagerAdapter(
        fragmentActivity: FragmentActivity,
        private var data: Map<BatteryRepository.BatteryUsageRankType, List<AppBatteryUsage>>
    ) : FragmentStateAdapter(fragmentActivity) {
        
        // 保存已创建的Fragment实例
        private val fragmentMap = mutableMapOf<Int, Fragment>()
        
        override fun getItemCount(): Int = tabTitles.size
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0, 1 -> {
                    // 根据位置选择对应的排名类型
                    val rankType = when (position) {
                        0 -> BatteryRepository.BatteryUsageRankType.TIME_USAGE
                        1 -> BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION
                        else -> BatteryRepository.BatteryUsageRankType.TIME_USAGE
                    }
                    val fragment = AppUsageListFragment.newInstance(data[rankType] ?: emptyList(), rankType)
                    fragmentMap[position] = fragment
                    fragment
                }
                2 -> {
                    // 运行中的APP Fragment
                    val fragment = RunningAppFragment()
                    fragmentMap[position] = fragment
                    fragment
                }
                else -> {
                    // 默认返回第一个标签页的Fragment
                    val fragment = AppUsageListFragment.newInstance(data[BatteryRepository.BatteryUsageRankType.TIME_USAGE] ?: emptyList(), BatteryRepository.BatteryUsageRankType.TIME_USAGE)
                    fragmentMap[position] = fragment
                    fragment
                }
            }
        }
        
        fun updateData(newData: Map<BatteryRepository.BatteryUsageRankType, List<AppBatteryUsage>>) {
            data = newData
            
            // 更新已创建的Fragment实例的数据
            fragmentMap.forEach { (position, fragment) ->
                if (position <= 1 && fragment is AppUsageListFragment) {
                    // 根据位置选择对应的排名类型
                    val rankType = when (position) {
                        0 -> BatteryRepository.BatteryUsageRankType.TIME_USAGE
                        1 -> BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION
                        else -> BatteryRepository.BatteryUsageRankType.TIME_USAGE
                    }
                    val updatedData = data[rankType] ?: emptyList()
                    fragment.updateData(updatedData, rankType)
                }
                // 第三个标签页是RunningAppFragment，不需要更新数据，它会自己刷新
            }
            
            notifyDataSetChanged()
        }
    }
}
