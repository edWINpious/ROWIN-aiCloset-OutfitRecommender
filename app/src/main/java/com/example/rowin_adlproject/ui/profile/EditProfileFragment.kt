package com.example.rowin_adlproject.ui.profile

import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.UserEntity
import com.example.rowin_adlproject.databinding.ScreenEditProfileBinding
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.onTextChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Feature #16 - Edit Profile: avatar with edit badge, Full Name / Email /
 * Phone / Location fields, Save (header action + button) and Cancel.
 */
class EditProfileFragment : HostedFragment(R.layout.screen_edit_profile) {

    private var _b: ScreenEditProfileBinding? = null
    private val b get() = _b!!

    private var currentUser: UserEntity? = null
    private var newAvatar: String? = null

    private val avatarPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let { importAvatar(it) } }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenEditProfileBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        val user = host.rowin.user.value
        if (user == null) {
            host.back()
            return
        }
        currentUser = user
        b.editProfileName.setText(user.name)
        b.editProfileEmail.setText(user.email)
        b.editProfilePhone.setText(user.phone.orEmpty())
        b.editProfileLocation.setText(user.location.orEmpty())
        renderAvatar(user, null)

        b.editProfileBack.setOnClickListener { host.back() }
        b.editProfileCancel.setOnClickListener { host.back() }
        b.editProfileSave.setOnClickListener { attemptSave() }
        b.editProfileSaveButton.setOnClickListener { attemptSave() }
        b.editProfileAvatarBadge.setOnClickListener { launchPicker() }
        b.editProfileAvatarRoot.setOnClickListener { launchPicker() }

        listOf(b.editProfileName, b.editProfileEmail, b.editProfilePhone, b.editProfileLocation)
            .forEach { field -> field.onTextChanged { b.editProfileError.visibility = View.GONE } }
    }

    private fun launchPicker() {
        avatarPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    private fun importAvatar(uri: Uri) {
        val container = (requireActivity().application as com.example.rowin_adlproject.RowinApplication).container
        viewLifecycleOwner.lifecycleScope.launch {
            val fileName = withContext(Dispatchers.IO) {
                runCatching { container.repository.imageStore.importAvatarFromUri(uri) }.getOrNull()
            }
            if (fileName != null) {
                currentUser?.avatarFileName?.let(container.repository.imageStore::deleteAvatar)
                newAvatar = fileName
                currentUser?.let { renderAvatar(it, fileName) }
            }
        }
    }

    private fun renderAvatar(user: UserEntity, pendingAvatar: String?) {
        val container = (requireActivity().application as com.example.rowin_adlproject.RowinApplication).container
        val avatarFile = (pendingAvatar ?: user.avatarFileName)
            ?.let { container.repository.imageStore.avatarFileFor(it) }
        val bitmap = Views.decodeSampled(avatarFile, 256)
        if (bitmap != null) {
            b.editProfileAvatarImage.setImageBitmap(bitmap)
            b.editProfileAvatarInitial.visibility = View.GONE
        } else {
            b.editProfileAvatarImage.setImageDrawable(null)
            b.editProfileAvatarInitial.text = user.name.firstOrNull()?.uppercase() ?: "R"
            b.editProfileAvatarInitial.visibility = View.VISIBLE
        }
    }

    private fun attemptSave() {
        val user = currentUser ?: return
        val name = b.editProfileName.text?.toString()?.trim().orEmpty()
        val email = b.editProfileEmail.text?.toString()?.trim().orEmpty()
        val error = when {
            name.isBlank() -> getString(R.string.error_enter_name)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.error_valid_email)
            else -> null
        }
        if (error != null) {
            b.editProfileError.text = error
            Views.show(b.editProfileError)
            return
        }
        host.rowin.updateProfile(
            user.copy(
                name = name,
                email = email,
                phone = b.editProfilePhone.text?.toString()?.trim().orEmpty().ifEmpty { null },
                location = b.editProfileLocation.text?.toString()?.trim().orEmpty().ifEmpty { null },
                avatarFileName = newAvatar ?: user.avatarFileName,
            ),
        )
        host.back()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
