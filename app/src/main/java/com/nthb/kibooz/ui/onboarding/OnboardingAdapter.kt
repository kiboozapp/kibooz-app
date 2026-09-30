package com.nthb.kibooz.ui.onboarding

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ItemOnboardingSlideBinding

data class OnboardingSlide(
    val imageRes: Int,
    val title: String,
    val description: String
)

class OnboardingAdapter(private val context: Context) :
    RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {

    private val slides = listOf(
        OnboardingSlide(
            imageRes = R.drawable.img_onboarding_1,
            title = "Pantau Perasaan Si Kecil Setiap Hari",
            description = "Kibooz membantu Anda memahami emosi anak melalui cerita interaktif dan laporan harian yang simpel."
        ),
        OnboardingSlide(
            imageRes = R.drawable.img_onboarding_2,
            title = "Ajarkan Anak Mengenal Perasaannya",
            description = "Dengan permainan dan cerita, anak belajar mengekspresikan emosi tanpa rasa tertekan."
        ),
        OnboardingSlide(
            imageRes = R.drawable.img_onboarding_3,
            title = "Dapatkan Insight Harian dalam 1 Klik",
            description = "Lihat perkembangan emosi anak melalui grafik yang mudah dibaca, kapan saja dan di mana saja."
        )
    )

    inner class SlideViewHolder(private val binding: ItemOnboardingSlideBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(slide: OnboardingSlide) {
            binding.ivSlideGraphic.setImageResource(slide.imageRes)
            binding.tvSlideTitle.text = slide.title
            binding.tvSlideDesc.text = slide.description
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = ItemOnboardingSlideBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount(): Int = slides.size
}
