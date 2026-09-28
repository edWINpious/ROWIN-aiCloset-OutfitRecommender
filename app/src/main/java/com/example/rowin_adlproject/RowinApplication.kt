package com.example.rowin_adlproject

import android.app.Application
import com.example.rowin_adlproject.data.AppContainer

/**
 * Application entry: owns the singleton [AppContainer].
 *
 * TODO (Firebase, practical #4): call FirebaseApp.initializeApp(this) here after
 *  adding google-services.json, and pass the FirebaseAuth/Firestore instances
 *  into AppContainer for cloud sync.
 */
class RowinApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
