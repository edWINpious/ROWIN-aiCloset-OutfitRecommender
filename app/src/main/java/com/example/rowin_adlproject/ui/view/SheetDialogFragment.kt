package com.example.rowin_adlproject.ui.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.annotation.LayoutRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Collect a StateFlow while the fragment view is STARTED (Compose collectAsState). */
fun <T> StateFlow<T>.collectOnStarted(owner: Fragment, block: (T) -> Unit) {
    owner.viewLifecycleOwner.lifecycleScope.launch {
        owner.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect { block(it) }
        }
    }
}

/** Collect a cold Flow while the fragment view is STARTED (latest value wins). */
fun <T> kotlinx.coroutines.flow.Flow<T>.collectFlowOnStarted(owner: Fragment, block: (T) -> Unit) {
    owner.viewLifecycleOwner.lifecycleScope.launch {
        owner.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect { block(it) }
        }
    }
}

/**
 * Base for the ROWIN confirmation sheets (feature #18, per the Stitch
 * designs): surface-colored rounded sheet + navigation-bar padding applied
 * to the content (the Compose sheets used navigationBarsPadding).
 */
abstract class SheetDialogFragment(@LayoutRes private val layoutRes: Int) :
    BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.example.rowin_adlproject.R.style.RowinBottomSheetDialogStyle)
    }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(layoutRes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.isFitToContents = true
        // Compose navigationBarsPadding equivalent.
        view.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + bars.bottom)
            WindowInsets.CONSUMED
        }
        onSheetReady(view)
    }

    protected abstract fun onSheetReady(view: View)

    fun showOnce(manager: FragmentManager, tag: String) {
        if (manager.findFragmentByTag(tag) == null) show(manager, tag)
    }
}
