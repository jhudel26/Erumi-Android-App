package com.erumi.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.erumi.nativeapp.core.download.ErumiDownloadUtil
import com.erumi.nativeapp.core.image.CoilConfig
import com.erumi.nativeapp.core.network.ApiClient

class ErumiApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onTerminate() {
        super.onTerminate()
        // Release SimpleCache and cancel coroutine pools on process shutdown (A5)
        ErumiDownloadUtil.release()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }

    override fun newImageLoader(): ImageLoader {
        return CoilConfig.getImageLoader(this, ApiClient.okHttpClient)
    }
}
