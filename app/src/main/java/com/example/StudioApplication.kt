package com.example

import android.app.Application
import android.util.Log
import com.example.domain.StudioAccountManager
import com.example.engine.NativeEngineLoader

class StudioApplication : Application() {
  companion object {
    lateinit var instance: StudioApplication
      private set
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    
    // Global safety uncaught exception handler
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("StudioApplication", "Uncaught exception on thread: ${thread.name}", throwable)
      defaultHandler?.uncaughtException(thread, throwable)
    }

    // 1. Safe Native Library Loader
    try {
      NativeEngineLoader.loadLibrary()
    } catch (t: Throwable) {
      Log.w("StudioApplication", "Native library loader skipped/failed", t)
    }

    // 2. Safe Studio Account & Firebase Initialization
    try {
      StudioAccountManager.init(this)
    } catch (t: Throwable) {
      Log.w("StudioApplication", "StudioAccountManager init skipped/failed", t)
    }
  }
}
