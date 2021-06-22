package com.example.puppi

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_history.*

class HistoryActivity : AppCompatActivity() {
    private lateinit var collectionAdapter: CollectionAdapter
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        setSupportActionBar(findViewById(R.id.toolbar))

        viewPager = findViewById(R.id.viewPager)

        setPagerAdapter()

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            if(position == 0){
                tab.text = "By day"
            } else if(position == 1){
                tab.text = "By event"
            }
        }.attach()
    }

    private fun setPagerAdapter(){
        collectionAdapter = CollectionAdapter(this, 2)
        viewPager.adapter = collectionAdapter
    }
}



class CollectionAdapter(activity: AppCompatActivity, private val itemsCount: Int) : FragmentStateAdapter(
    activity
) {
    override fun getItemCount(): Int {
        return itemsCount
    }

    override fun createFragment(position: Int) : Fragment {
        return when (position) {
            0 -> {
                BroadHistoryFragment()
            }
            1 -> {
                DetailedHistoryFragment()
            }
            else -> {
                DetailedHistoryFragment()
            }
        }
    }
}