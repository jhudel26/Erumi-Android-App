package com.erumi.nativeapp

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.erumi.nativeapp.core.di.AppContainer
import com.erumi.nativeapp.core.image.CoilConfig
import com.erumi.nativeapp.core.network.ApiClient

class ErumiApplication : Application(), ImageLoaderFactory {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return CoilConfig.getImageLoader(this, ApiClient.okHttpClient)
    }

    companion object {
        fun container(context: Context): AppContainer {
            val app = context.applicationContext as? ErumiApplication
            return app?.container ?: AppContainer(context.applicationContext)
        }
    }
}
