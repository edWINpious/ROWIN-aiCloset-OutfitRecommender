package com.example.rowin_adlproject.ui.view

import android.view.View
import androidx.core.content.ContextCompat
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.databinding.ComponentRowinBottomBarBinding

/**
 * Binds the bottom navigation (feature #17): active tab gets the filled
 * icon + brand-green icon/label on a soft PrimaryFixed pill; inactive tabs
 * get the outlined icon in 60%-opacity on-surface-variant.
 */
object ScreenRows {

    fun bindBottomBar(
        binding: ComponentRowinBottomBarBinding,
        current: Screen,
        onSelect: (Screen) -> Unit,
    ) {
        val context = binding.root.context
        val active = ContextCompat.getColor(context, R.color.rowin_primary)
        val inactive = ContextCompat.getColor(context, R.color.rowin_nav_inactive)
        val pill = ContextCompat.getDrawable(context, R.drawable.bg_nav_pill)
        val transparent = context.getDrawable(android.R.color.transparent)

        fun apply(
            row: View,
            icon: android.widget.ImageView,
            label: android.widget.TextView,
            screen: Screen,
            filledRes: Int,
            outlinedRes: Int,
        ) {
            val selected = screen == current
            icon.setImageResource(if (selected) filledRes else outlinedRes)
            icon.setColorFilter(if (selected) active else inactive)
            label.setTextColor(if (selected) active else inactive)
            row.background = if (selected) pill?.constantState?.newDrawable()?.mutate() else transparent
            row.contentDescription = label.text
            row.setOnClickListener { onSelect(screen) }
        }

        apply(binding.navHome, binding.iconHome, binding.labelHome, Screen.HOME,
            R.drawable.ic_home_filled, R.drawable.ic_home_outlined)
        apply(binding.navCloset, binding.iconCloset, binding.labelCloset, Screen.CLOSET,
            R.drawable.ic_closet_filled, R.drawable.ic_closet_outlined)
        apply(binding.navOutfits, binding.iconOutfits, binding.labelOutfits, Screen.OUTFITS,
            R.drawable.ic_style_filled, R.drawable.ic_style_outlined)
        apply(binding.navProfile, binding.iconProfile, binding.labelProfile, Screen.PROFILE,
            R.drawable.ic_person_filled, R.drawable.ic_person_outlined)
    }
}
