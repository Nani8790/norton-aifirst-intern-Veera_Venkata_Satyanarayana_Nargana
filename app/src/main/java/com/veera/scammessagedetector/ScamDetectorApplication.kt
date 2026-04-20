package com.veera.scammessagedetector

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point required by Hilt.
 *
 * Annotating with [@HiltAndroidApp] triggers Hilt's code generation and creates
 * the application-level dependency container. Every other [@AndroidEntryPoint]
 * component in the app can trace its component hierarchy back to this class.
 *
 * Must be declared in `AndroidManifest.xml` via `android:name=".ScamDetectorApplication"`.
 */
@HiltAndroidApp
class ScamDetectorApplication : Application()
