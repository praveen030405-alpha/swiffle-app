package com.swiffle.app

import android.app.Application
import com.swiffle.app.ui.components.CachedMaroonClothShaderBrush
import com.swiffle.app.ui.components.OnboardingImageMemoryManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SwiffleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm PDFBox and the #440E03 cloth shader off the main thread so startup is instantaneous
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CachedMaroonClothShaderBrush
                PDFBoxResourceLoader.init(applicationContext)
            } catch (_: Throwable) {}
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        OnboardingImageMemoryManager.onTrimMemory(applicationContext, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        OnboardingImageMemoryManager.clearAllOnboardingBitmapCaches(applicationContext)
    }
}
