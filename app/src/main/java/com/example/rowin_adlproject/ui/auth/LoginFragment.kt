package com.example.rowin_adlproject.ui.auth

import android.os.Bundle
import android.view.View
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.databinding.ScreenLoginBinding
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.onTextChanged

/** Feature #1 - Login: email + password, show/hide toggle, inline error. */
class LoginFragment : HostedFragment(R.layout.screen_login) {

    private var _b: ScreenLoginBinding? = null
    private val b get() = _b!!

    private var showPassword = false
    private var loading = false

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenLoginBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        AuthUi.applyFooter(
            b.loginFooter,
            prefix = getString(R.string.footer_no_account),
            linkLabel = getString(R.string.create_account_link),
            onLink = { host.open(Screen.REGISTER) },
        )

        b.loginTogglePassword.setOnClickListener {
            showPassword = !showPassword
            AuthUi.syncPasswordToggle(b.loginPassword, b.loginTogglePassword, showPassword)
        }

        b.loginForgot.setOnClickListener { b.loginResetHint.visibility = toggleOf(b.loginResetHint) }

        b.loginEmail.onTextChanged { b.loginError.visibility = View.GONE }
        b.loginPassword.onTextChanged { b.loginError.visibility = View.GONE }

        b.loginButton.setOnClickListener { attemptLogin() }
    }

    private fun toggleOf(view: View): Int =
        if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE

    private fun attemptLogin() {
        if (loading) return
        loading = true
        AuthUi.setLoading(b.loginButton, true)
        host.rowin.login(
            b.loginEmail.text?.toString()?.trim().orEmpty(),
            b.loginPassword.text?.toString().orEmpty(),
        ) { result ->
            loading = false
            AuthUi.setLoading(b.loginButton, false)
            result.exceptionOrNull()?.message?.let { message ->
                b.loginError.text = message
                Views.show(b.loginError)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
