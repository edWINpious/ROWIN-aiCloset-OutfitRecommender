package com.example.rowin_adlproject.ui.view

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.StateFlow

/**
 * Base for all screen fragments: they all live inside the single-activity
 * host and talk to it through [ScreenHost].
 */
abstract class HostedFragment(@LayoutRes layout: Int) : Fragment(layout) {

    protected val host: ScreenHost
        get() = requireActivity() as ScreenHost

    /** Collect a StateFlow only while the view is STARTED (mirrors Compose collectAsState). */
    protected fun <T> StateFlow<T>.collectWithViewLifecycle(block: (T) -> Unit) {
        collectOnStarted(this@HostedFragment, block)
    }

    /** Collect a cold Flow only while the view is STARTED. */
    protected fun <T> kotlinx.coroutines.flow.Flow<T>.collectFlowWithViewLifecycle(block: (T) -> Unit) {
        collectFlowOnStarted(this@HostedFragment, block)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(view)
    }

    /** Fragment hook instead of onViewCreated override (binding is ready). */
    protected abstract fun onViewReady(view: View)
}
