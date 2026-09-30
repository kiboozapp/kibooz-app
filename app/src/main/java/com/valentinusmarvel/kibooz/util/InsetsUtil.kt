package com.valentinusmarvel.kibooz.util

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

object InsetsUtil {

    val SYSTEM_NAV_BAR_GRAY = Color.parseColor("#E2E2E8")

    /**
     * Memastikan bilah navigasi bawaan HP (3-button atau gesture bar) memiliki latar belakang abu-abu
     * dan ikon navigasi berwarna gelap sehingga terlihat jelas dan kontras tinggi.
     */
    fun ensureNavigationBarColor(
        view: View,
        color: Int = SYSTEM_NAV_BAR_GRAY,
        isLightBackground: Boolean = true
    ) {
        val activity = findActivity(view) ?: return
        val window = activity.window ?: return
        window.navigationBarColor = color
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightNavigationBars = isLightBackground
    }

    private fun findActivity(view: View): Activity? {
        var context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    /**
     * Menerapkan insets bilah status (status bar / notch) ke header atas aplikasi
     * sehingga teks judul, tombol kembali, dan konten tidak tertutup/terpotong oleh
     * jam sistem, persentase baterai, dan kamera punch-hole HP.
     */
    fun applyHeaderStatusBarInsets(headerView: View, root: View? = null) {
        val target = root ?: headerView
        ensureNavigationBarColor(target)
        val basePaddingTop = headerView.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(target) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            headerView.updatePadding(top = basePaddingTop + statusBars.top)
            insets
        }
        target.requestApplyInsets()
    }

    /**
     * Mengatur inset navigasi sistem (3-button atau gesture pill bar) pada Bottom Navigation Bar
     * dan Floating Action Button (FAB) agar tidak tertutupi oleh navigasi bawaan HP.
     */
    fun applyBottomNavigationInsets(bottomBar: View, fab: View? = null, root: View? = null) {
        val target = root ?: bottomBar
        ensureNavigationBarColor(target)
        val baseHeight = bottomBar.layoutParams?.height ?: -1
        val basePaddingBottom = bottomBar.paddingBottom
        val baseFabMarginBottom = (fab?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(target) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(navBars.bottom, imeInsets.bottom)

            // Perpanjang tinggi bottom bar agar ruang konten tidak tertekan
            if (baseHeight > 0) {
                bottomBar.updateLayoutParams {
                    height = baseHeight + bottomInset
                }
            }
            bottomBar.updatePadding(bottom = basePaddingBottom + bottomInset)

            // Angkat FAB agar selalu mengambang di atas bar navigasi sistem
            fab?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseFabMarginBottom + bottomInset
            }

            insets
        }
        target.requestApplyInsets()
    }

    /**
     * Menerapkan insets navigasi bawah pada root view untuk layar Onboarding, Login, dan Detail
     */
    fun applyRootBottomInsets(rootView: View) {
        ensureNavigationBarColor(rootView)
        val basePaddingBottom = rootView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(navBars.bottom, imeInsets.bottom)

            v.updatePadding(bottom = basePaddingBottom + bottomInset)
            insets
        }
        rootView.requestApplyInsets()
    }

    /**
     * Menerapkan insets lengkap:
     * - Status Bar (top) pada headerView agar bebas tumpukan jam/kamera
     * - Navigation Bar (bottom) pada bottomBar & FAB (atau root) agar tidak tertutup tombol sistem HP
     */
    fun applySystemBarInsets(
        root: View,
        headerView: View? = null,
        bottomBar: View? = null,
        fab: View? = null
    ) {
        ensureNavigationBarColor(root)
        val baseHeaderHeight = headerView?.layoutParams?.height ?: -1
        val baseHeaderPaddingTop = headerView?.paddingTop ?: 0
        val baseBottomBarHeight = bottomBar?.layoutParams?.height ?: -1
        val baseBottomPadding = bottomBar?.paddingBottom ?: root.paddingBottom
        val baseFabMarginBottom = (fab?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(navBars.bottom, imeInsets.bottom)

            // 1. Dorong konten header ke bawah batas status bar dan perluas tingginya jika berukuran tetap
            headerView?.let {
                if (baseHeaderHeight > 0) {
                    it.updateLayoutParams {
                        height = baseHeaderHeight + statusBars.top
                    }
                }
                it.updatePadding(top = baseHeaderPaddingTop + statusBars.top)
            }

            // 2. Perpanjang dan beri bantalan bottom bar navigasi
            bottomBar?.let {
                if (baseBottomBarHeight > 0) {
                    it.updateLayoutParams {
                        height = baseBottomBarHeight + bottomInset
                    }
                }
                it.updatePadding(bottom = baseBottomPadding + bottomInset)
            } ?: run {
                root.updatePadding(bottom = baseBottomPadding + bottomInset)
            }

            // 3. Elevasi FAB
            fab?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseFabMarginBottom + bottomInset
            }

            insets
        }
        root.requestApplyInsets()
    }
}
