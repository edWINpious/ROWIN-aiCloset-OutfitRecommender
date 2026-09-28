package com.example.rowin_adlproject.ai

import com.example.rowin_adlproject.BuildConfig

/**
 * ============================================================
 *  API KEYS & CREDENTIALS - READ THIS BEFORE RUNNING THE APP
 * ============================================================
 *
 *  1) GROQ (AI analysis + outfit recommendations - features #6 & #21):
 *     - Create a free key at https://console.groq.com/keys
 *     - Open local.properties (project root, NOT committed to git) and add:
 *
 *           GROQ_API_KEY=gsk_your_key_here
 *
 *     - Rebuild. The key is injected into BuildConfig at compile time.
 *     - Alternatively paste it into the FALLBACK_GROQ_KEY constant below
 *       (fine for the college demo, not for production).
 *
 *  2) FIREBASE (optional cloud sync - practical #4):
 *     - Firebase Console -> Add project -> Register this app with package
 *       name com.example.rowin_adlproject
 *     - Download google-services.json into app/
 *     - Add the Google Services Gradle plugin (see app/build.gradle.kts note)
 *     - Auth & Firestore wiring points are marked TODO in:
 *         WardrobeRepository (sync writes), AppContainer (init),
 *         SessionManager (token/session mirror).
 *
 *  Keep every key out of source control. local.properties is gitignored.
 */
object AiKeys {

    /** Injected from local.properties via app/build.gradle.kts. */
    val groqApiKey: String
        get() = BuildConfig.GROQ_API_KEY.ifBlank { FALLBACK_GROQ_KEY }

    /** Fallback slot if you prefer hardcoding for the demo. Leave blank normally. */
    // TODO: optionally paste your Groq key here for the demo build:
    private const val FALLBACK_GROQ_KEY = ""

    val isGroqConfigured: Boolean get() = groqApiKey.isNotBlank()
}
