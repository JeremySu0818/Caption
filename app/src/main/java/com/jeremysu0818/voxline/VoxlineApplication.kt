package com.jeremysu0818.voxline

import android.app.Application

class VoxlineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VoxlineGraph.init(this)
    }
}
