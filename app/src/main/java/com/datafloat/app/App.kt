package com.datafloat.app

import android.app.Application

class App : Application() {
    val settingsRepo by lazy { SettingsRepo(this) }
    val db by lazy { AppDb.build(this) }
    val usageSource by lazy { UsageSource(this) }
    val engine by lazy { TrackerEngine(this) }

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)
    }
}
