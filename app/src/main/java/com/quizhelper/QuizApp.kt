package com.quizhelper

import android.app.Application
import com.quizhelper.core.Prefs
import com.quizhelper.overlay.OverlayController

class QuizApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        OverlayController.init(this)
    }
}
