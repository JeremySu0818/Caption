package com.jeremysu0818.caption

import android.app.Application

class CaptionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CaptionGraph.init(this)
    }
}
