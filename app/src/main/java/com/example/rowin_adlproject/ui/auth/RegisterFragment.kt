package com.example.rowin_adlproject.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.databinding.ScreenRegisterBinding
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.onTextChanged

/**
 * Feature #1 - Registration: name, email, password (+confirm) with
 * validation, exactly per the Stitch register design.
 */
class RegisterFragment : HostedFragment(R.layout.screen_register) {

    private var _b: ScreenRegisterBinding? = null
    private val b get() = _b!!

    private var showPassword = false
    private var loading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenRegisterBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        AuthUi.applyFooter(
            b.registerFooter,
            prefix = getString(R.string.footer_have_account),
            linkLabel = getString(R.string.sign_in_link),
            onLink = { host.back() },
        )

        b.registerTogglePassword.setOnClickListener {
            showPassword = !showPassword
            AuthUi.syncPasswordToggle(b.registerPassword, b.registerTogglePassword, showPassword)
        }

        listOf(b.registerName, b.registerEmail, b.registerPassword, b.registerConfirm)
            .forEach { field -> field.onTextChanged { b.registerError.visibility = View.GONE } }

        b.registerButton.setOnClickListener { attemptRegister() }
    }

    private fun validate(): String? = when {
        b.registerName.text.isNullOrBlank() -> getString(R.string.error_enter_name)
        !Patterns.EMAIL_ADDRESS.matcher(b.registerEmail.text?.toString()?.trim().orEmpty()).matches() ->
            getString(R.string.error_valid_email)
        (b.registerPassword.text?.length ?: 0) < 6 -> getString(R.string.error_password_length)
        b.registerPassword.text?.toString() != b.registerConfirm.text?.toString() ->
            getString(R.string.error_password_match)
        else -> null
    }

    private fun attemptRegister() {
        if (loading) return
        validate()?.let { message ->
            b.registerError.text = message
            Views.show(b.registerError)
            return
        }
        loading = true
        AuthUi.setLoading(b.registerButton, true)
        host.rowin.register(
            b.registerName.text?.toString()?.trim().orEmpty(),
            b.registerEmail.text?.toString()?.trim().orEmpty(),
            b.registerPassword.text?.toString().orEmpty(),
        ) { result ->
            loading = false
            AuthUi.setLoading(b.registerButton, false)
            result.exceptionOrNull()?.message?.let { message ->
                b.registerError.text = message
                Views.show(b.registerError)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
