package com.example.rowin_adlproject.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.ui.components.RowinButton
import com.example.rowin_adlproject.ui.components.RowinTextField
import com.example.rowin_adlproject.ui.theme.Primary
import com.example.rowin_adlproject.ui.theme.PrimaryContainer

/**
 * Feature #1 - Login. Email + password with show/hide toggle, inline error,
 * forgot-password link and Create Account link - 1:1 with the Stitch login
 * design (wordmark, display heading in brand green, secondary subtitle).
 */
@Composable
fun LoginScreen(
    onLogin: (email: String, password: String, onResult: (Result<Long>) -> Unit) -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showResetHint by remember { mutableStateOf(false) }

    AuthScaffold(wordmarkHeight = 32.dp) {
        Text(
            text = "Welcome back",
            style = MaterialTheme.typography.headlineLarge,
            color = Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Your wardrobe, beautifully organized.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Spacer(Modifier.height(32.dp))

        RowinTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = "Email",
            placeholder = "Enter your email",
        )
        Spacer(Modifier.height(16.dp))
        RowinTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password",
            placeholder = "Enter your password",
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                AuthVisibilityToggle(showPassword) { showPassword = it }
            },
        )

        // Forgot password link, right-aligned per the design.
        Text(
            text = "Forgot password?",
            style = MaterialTheme.typography.labelMedium,
            color = PrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showResetHint = !showResetHint },
            textAlign = TextAlign.End,
        )
        if (showResetHint) {
            Text(
                text = "Password reset isn't available in this build - create a new account if you can't sign in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        RowinButton(
            text = "Log In",
            onClick = {
                loading = true
                onLogin(email, password) { result ->
                    loading = false
                    error = result.exceptionOrNull()?.message
                }
            },
            loading = loading,
            enabled = email.isNotBlank() && password.isNotBlank(),
        )

        Spacer(Modifier.height(32.dp))
        AuthFooter(
            prefix = "Don't have an account? ",
            linkLabel = "Create Account",
            onLinkClick = onNavigateToRegister,
        )
    }
}

/**
 * Feature #1 - Registration: name, email, password (+confirm) with
 * validation, exactly per the Stitch register design (no age field - the
 * profile age defaults to 0 and can be set later in Edit Profile).
 */
@Composable
fun RegisterScreen(
    onRegister: (name: String, email: String, password: String, onResult: (Result<Long>) -> Unit) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun validate(): String? = when {
        name.isBlank() -> "Please enter your name."
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address."
        password.length < 6 -> "Password must be at least 6 characters."
        password != confirm -> "Passwords do not match."
        else -> null
    }

    AuthScaffold(wordmarkHeight = 40.dp) {
        Text(
            text = "Create your account",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Start building your digital wardrobe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Spacer(Modifier.height(28.dp))

        RowinTextField(value = name, onValueChange = { name = it; error = null }, label = "Name", placeholder = "Enter your name")
        Spacer(Modifier.height(16.dp))
        RowinTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = "Email",
            placeholder = "Enter your email",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(16.dp))
        RowinTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password",
            placeholder = "Create a password",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { AuthVisibilityToggle(showPassword) { showPassword = it } },
        )
        Spacer(Modifier.height(16.dp))
        RowinTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = "Confirm Password",
            placeholder = "Confirm your password",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        )

        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        RowinButton(
            text = "Create Account",
            onClick = {
                val validation = validate()
                if (validation != null) {
                    error = validation
                    return@RowinButton
                }
                loading = true
                onRegister(name, email, password) { result ->
                    loading = false
                    error = result.exceptionOrNull()?.message
                }
            },
            loading = loading,
        )

        Spacer(Modifier.height(32.dp))
        AuthFooter(
            prefix = "Already have an account? ",
            linkLabel = "Sign In",
            onLinkClick = onNavigateToLogin,
        )
    }
}

@Composable
private fun AuthVisibilityToggle(visible: Boolean, onToggle: (Boolean) -> Unit) {
    androidx.compose.material3.IconButton(onClick = { onToggle(!visible) }) {
        androidx.compose.material3.Icon(
            imageVector = if (visible) com.example.rowin_adlproject.ui.components.RowinIcons.VisibilityOff
            else com.example.rowin_adlproject.ui.components.RowinIcons.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Shared auth chrome: centered wordmark, scrollable body, footer link. */
@Composable
private fun AuthScaffold(wordmarkHeight: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, top = 72.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ROWIN logo above the heading. Padding must come BEFORE the fixed
        // height - padding after height clamps the image to zero.
        Image(
            painter = painterResource(R.drawable.wordmark_rowin),
            contentDescription = "ROWIN",
            modifier = Modifier
                .padding(bottom = 48.dp)
                .height(wordmarkHeight),
        )
        content()
    }
}

@Composable
private fun AuthFooter(prefix: String, linkLabel: String, onLinkClick: () -> Unit) {
    val annotated = buildAnnotatedString {
        append(prefix)
        pushStringAnnotation(tag = "link", annotation = "link")
        withStyle(
            SpanStyle(
                color = PrimaryContainer,
                fontWeight = FontWeight.Medium,
            ),
        ) { append(linkLabel) }
        pop()
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("link", offset, offset)
                .firstOrNull()?.let { onLinkClick() }
        },
    )
}
