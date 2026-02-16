package com.scramblr.rftoolkit

import android.app.Application
import com.scramblr.rftoolkit.data.db.AppDatabase

class RFToolkitApp : Application() {
    
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: RFToolkitApp
            private set
    }
}
