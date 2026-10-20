package com.example.rowin_adlproject.ui.view

import android.view.View
import android.widget.PopupMenu

/** Exposed dropdown used for Category / Season / Occasion pickers. */
object RowinDropdowns {

    fun show(anchor: View, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val popup = PopupMenu(anchor.context, anchor)
        options.forEachIndexed { index, label -> popup.menu.add(0, index, index, label) }
        popup.menu.setGroupCheckable(0, true, true)
        if (selected in options.indices) popup.menu.getItem(selected).isChecked = true
        popup.setOnMenuItemClickListener { item ->
            onPick(item.itemId)
            true
        }
        popup.show()
    }
}
