package com.example.rowin_adlproject.ui.profile

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.FirebaseSyncManager
import com.example.rowin_adlproject.databinding.ScreenProfileBinding
import com.example.rowin_adlproject.databinding.SheetSettingsBinding
import com.example.rowin_adlproject.databinding.SheetSignOutBinding
import com.example.rowin_adlproject.ui.home.HomeViewModel
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.SheetDialogFragment
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.collectFlowOnStarted
import com.example.rowin_adlproject.ui.view.collectOnStarted

/**
 * Feature #15 - User Profile: big avatar (editable), identity, My Activity
 * stats, Account rows and the Sign Out / App Settings bottom sheets (#18).
 */
class ProfileFragment : HostedFragment(R.layout.screen_profile) {

    private var _b: ScreenProfileBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenProfileBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        val container = (requireActivity().application as com.example.rowin_adlproject.RowinApplication).container

        b.profileAvatarRoot.setOnClickListener { host.open(Screen.EDIT_PROFILE) }
        b.profileRowEdit.setOnClickListener { host.open(Screen.EDIT_PROFILE) }
        b.profileRowSaved.setOnClickListener { host.open(Screen.SAVED_OUTFITS) }
        b.profileRowAssistant.setOnClickListener { host.open(Screen.CHAT) }
        b.profileRowAlerts.setOnClickListener { host.open(Screen.ALERTS) }
        b.profileRowSettings.setOnClickListener {
            SettingsSheet().showOnce(childFragmentManager, "settings_sheet")
        }
        b.profileSignOut.setOnClickListener {
            SignOutSheet().showOnce(childFragmentManager, "sign_out_sheet")
        }

        // Identity (live profile)
        host.rowin.user.collectWithViewLifecycle { user ->
            if (user == null) return@collectWithViewLifecycle
            b.profileName.text = user.name.ifBlank { "ROWIN User" }
            b.profileEmail.text = user.email
            val avatarFile = user.avatarFileName?.let { container.repository.imageStore.avatarFileFor(it) }
            val bitmap = Views.decodeSampled(avatarFile, 192)
            if (bitmap != null) {
                b.profileAvatarImage.setImageBitmap(bitmap)
                b.profileAvatarInitial.visibility = View.GONE
            } else {
                b.profileAvatarImage.setImageDrawable(null)
                b.profileAvatarInitial.text =
                    user.name.firstOrNull()?.uppercase() ?: "R"
                b.profileAvatarInitial.visibility = View.VISIBLE
            }
        }

        // Dynamic stats (live counts from the database)
        val statsVm = ViewModelProvider(
            this, HomeViewModel.Factory(requireContext(), host.userId),
        )[HomeViewModel::class.java]
        statsVm.clothingCount.collectWithViewLifecycle { b.profileStatItems.text = "$it" }
        statsVm.outfitsCount.collectWithViewLifecycle { b.profileStatOutfits.text = "$it" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    // ------------------------------------------------------------ sheets

    /** Sign out confirmation (feature #18, per the Stitch design). */
    class SignOutSheet : SheetDialogFragment(R.layout.sheet_sign_out) {

        override fun onSheetReady(view: View) {
            val binding = SheetSignOutBinding.bind(view)
            val host = requireActivity() as com.example.rowin_adlproject.ui.view.ScreenHost
            binding.sheetSignOutConfirm.setOnClickListener {
                host.rowin.signOut()
                dismiss()
            }
            binding.sheetSignOutCancel.setOnClickListener { dismiss() }
        }
    }

    /**
     * App Settings (practicals #4/#5/#10): Firebase connectivity + network
     * state + AI-alert permission, all bound live.
     */
    class SettingsSheet : SheetDialogFragment(R.layout.sheet_settings) {

        override fun onSheetReady(view: View) {
            val binding = SheetSettingsBinding.bind(view)
            val container =
                (requireActivity().application as com.example.rowin_adlproject.RowinApplication).container

            container.firebaseSync.state.collectOnStarted(this) { sync ->
                val (iconRes, tintRes) = when (sync.status) {
                    FirebaseSyncManager.Status.CONNECTED -> R.drawable.ic_cloud_done to R.color.rowin_primary
                    FirebaseSyncManager.Status.SYNCING -> R.drawable.ic_sync to R.color.rowin_primary
                    FirebaseSyncManager.Status.LOCAL_ONLY -> R.drawable.ic_cloud_off to R.color.rowin_on_surface_variant
                    else -> R.drawable.ic_cloud_off to R.color.rowin_error
                }
                binding.settingsIconFirebase.setImageResource(iconRes)
                binding.settingsIconFirebase.setColorFilter(ContextCompat.getColor(requireContext(), tintRes))
                binding.settingsTitleFirebase.text =
                    "Firebase " + sync.status.name.lowercase().replace('_', ' ')
                        .replaceFirstChar { it.uppercase() }
                binding.settingsDetailFirebase.text = sync.detail
            }

            container.networkMonitor.isOnline.collectFlowOnStarted(this) { online ->
                binding.settingsIconNetwork.setImageResource(
                    if (online) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                )
                binding.settingsIconNetwork.setColorFilter(
                    ContextCompat.getColor(
                        requireContext(),
                        if (online) R.color.rowin_primary else R.color.rowin_on_surface_variant,
                    ),
                )
                binding.settingsTitleNetwork.text =
                    getString(if (online) R.string.network_online else R.string.network_offline)
            }

            val alertsOn = container.alertsManager.notificationsEnabled()
            binding.settingsTitleAlerts.text =
                getString(if (alertsOn) R.string.alerts_enabled else R.string.alerts_blocked)
            binding.settingsDetailAlerts.text =
                getString(if (alertsOn) R.string.alerts_enabled_detail else R.string.alerts_blocked_detail)
            binding.settingsIconAlerts.setColorFilter(
                ContextCompat.getColor(
                    requireContext(),
                    if (alertsOn) R.color.rowin_primary else R.color.rowin_on_surface_variant,
                ),
            )

            binding.sheetSettingsClose.setOnClickListener { dismiss() }
        }
    }
}
