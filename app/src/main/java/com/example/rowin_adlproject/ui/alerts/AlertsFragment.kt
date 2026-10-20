package com.example.rowin_adlproject.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.AlertsEngine
import com.example.rowin_adlproject.databinding.ScreenAlertsBinding
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.Views

/**
 * Practical #10 - AI alerts & notifications, in-app feed. Lists every active
 * suggestion with a deep-link action into the matching screen.
 */
class AlertsFragment : HostedFragment(R.layout.screen_alerts) {

    private var _b: ScreenAlertsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenAlertsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        val container =
            (requireActivity().application as com.example.rowin_adlproject.RowinApplication).container

        b.alertsBack.setOnClickListener { host.back() }

        container.alertsManager.alerts.collectWithViewLifecycle { alerts ->
            renderAlerts(alerts)
        }
    }

    private fun renderAlerts(alerts: List<AlertsEngine.Alert>) {
        b.alertsList.removeAllViews()
        if (alerts.isEmpty()) {
            Views.show(b.alertsEmpty)
            return
        }
        Views.hide(b.alertsEmpty)
        alerts.forEach { alert ->
            val card = layoutInflater.inflate(R.layout.item_alert_card, b.alertsList, false) as LinearLayout
            card.findViewById<TextView>(R.id.alert_title).text = alert.title
            card.findViewById<TextView>(R.id.alert_message).text = alert.message
            card.setOnClickListener { openRoute(alert.actionRoute) }
            b.alertsList.addView(card)
        }
    }

    private fun openRoute(route: String) {
        when (route) {
            "closet" -> host.open(Screen.CLOSET)
            "add_clothing" -> host.open(Screen.ADD_CLOTHING)
            "planner" -> host.open(Screen.OUTFITS)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
