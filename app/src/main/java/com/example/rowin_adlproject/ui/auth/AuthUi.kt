package com.example.rowin_adlproject.ui.auth

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.rowin_adlproject.R

/** Shared auth-screen helpers (login + register). */
object AuthUi {

    /**
     * "prefix + linkLabel" footer: prefix in Secondary, link in
     * PrimaryContainer (medium weight) and clickable.
     */
    fun applyFooter(footer: TextView, prefix: String, linkLabel: String, onLink: () -> Unit) {
        val context = footer.context
        val spannable = SpannableStringBuilder()
        spannable.append(prefix)
        val start = spannable.length
        spannable.append(linkLabel)
        val end = spannable.length
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onLink()
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = ContextCompat.getColor(context, R.color.rowin_primary_container)
                    ds.isUnderlineText = false
                }
            },
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        spannable.setSpan(StyleSpan(android.graphics.Typeface.NORMAL), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        footer.text = spannable
        footer.movementMethod = LinkMovementMethod.getInstance()
        footer.highlightColor = android.graphics.Color.TRANSPARENT
    }

    /** Swaps the show/hide password icon and transformation. */
    fun syncPasswordToggle(field: EditText, toggle: ImageButton, visible: Boolean) {
        field.transformationMethod = if (visible)
            android.text.method.HideReturnsTransformationMethod.getInstance()
            else android.text.method.PasswordTransformationMethod.getInstance()
        toggle.setImageResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
        toggle.contentDescription =
            toggle.context.getString(if (visible) R.string.hide_password else R.string.show_password)
        field.setSelection(field.text?.length ?: 0)
    }

    /** Disabled button look (50% alpha, not clickable) while loading. */
    fun setLoading(button: android.widget.Button, loading: Boolean) {
        button.isEnabled = !loading
        button.alpha = if (loading) 0.5f else 1f
    }
}
