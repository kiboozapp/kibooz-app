package com.nthb.kibooz.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityOnboardingBinding
import com.nthb.kibooz.ui.auth.RoleSelectionActivity
import com.nthb.kibooz.util.InsetsUtil

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pastikan navigasi bawah tidak tertutup oleh navigasi sistem bawaan HP
        InsetsUtil.applyBottomNavigationInsets(binding.clBottomBar, root = binding.root)

        setupViewPager()
        setupListeners()
    }

    private fun setupViewPager() {
        adapter = OnboardingAdapter(this)
        binding.vpOnboarding.adapter = adapter

        binding.vpOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
            }
        })
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            val currentItem = binding.vpOnboarding.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.vpOnboarding.setCurrentItem(currentItem + 1, true)
            } else {
                navigateToRoleSelection()
            }
        }

        binding.btnBack.setOnClickListener {
            val currentItem = binding.vpOnboarding.currentItem
            if (currentItem > 0) {
                binding.vpOnboarding.setCurrentItem(currentItem - 1, true)
            }
        }
    }

    private fun updateIndicators(position: Int) {
        val activeBg = R.drawable.bg_dot_active
        val inactiveBg = R.drawable.bg_dot_inactive

        binding.dot1.setBackgroundResource(if (position == 0) activeBg else inactiveBg)
        binding.dot2.setBackgroundResource(if (position == 1) activeBg else inactiveBg)
        binding.dot3.setBackgroundResource(if (position == 2) activeBg else inactiveBg)

        // Show/hide back button
        binding.btnBack.visibility = if (position > 0) View.VISIBLE else View.GONE
    }

    private fun navigateToRoleSelection() {
        val intent = Intent(this, RoleSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
}
