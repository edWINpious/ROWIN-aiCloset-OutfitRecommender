package com.example.rowin_adlproject.ui.view

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.databinding.ComponentRowinTopBarBinding

/**
 * Programmatic chrome for the shared top bar (per the Stitch shell):
 * 36dp leading initials avatar, centered ROWIN wordmark, trailing
 * notification bell with an 8dp error badge when there are unread alerts.
 */
object ScreenChrome {

    fun bindTopBar(
        binding: ComponentRowinTopBarBinding,
        userName: String?,
        hasUnread: Boolean,
        onBell: () -> Unit,
    ) {
        val context = binding.root.context
        val slot = (36 * context.resources.displayMetrics.density).toInt()

        // Leading: initials avatar (36dp circle, surfaceContainerHighest, primary initial).
        val leading = binding.topBarLeading
        leading.removeAllViews()
        val initial = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(slot, slot)
            background = ContextCompat.getDrawable(context, R.drawable.bg_circle_container)
            gravity = Gravity.CENTER
            text = (userName?.firstOrNull()?.uppercase() ?: "R")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, R.color.rowin_primary))
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.manrope_semibold)
            contentDescription = userName ?: context.getString(R.string.nav_profile)
        }
        leading.addView(initial)

        // Trailing: bell (24dp, primary) + 8dp unread badge at the top end.
        val trailing = binding.topBarTrailing
        trailing.removeAllViews()
        val bell = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(slot, slot)
            addView(
                ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        dp(context, 24), dp(context, 24), Gravity.CENTER,
                    )
                    setImageResource(R.drawable.ic_notifications)
                    setColorFilter(ContextCompat.getColor(context, R.color.rowin_primary))
                    contentDescription = context.getString(R.string.ai_alerts_title)
                },
            )
            if (hasUnread) {
                addView(
                    View(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp(context, 8), dp(context, 8)).apply {
                            gravity = Gravity.TOP or Gravity.END
                        }
                        background = ContextCompat.getDrawable(context, R.drawable.bg_circle_error)
                    },
                )
            }
            setOnClickListener { onBell() }
        }
        trailing.addView(bell)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Tints an ImageView from a color resource. */
    fun tint(image: ImageView, colorRes: Int) {
        image.setColorFilter(ContextCompat.getColor(image.context, colorRes))
    }
}
