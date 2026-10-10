package com.example.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

class AuraCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName("com.example.MainActivity")
            .build()
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName("com.example.MainActivity")
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId("CC1AD845") // Default Media Receiver
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
