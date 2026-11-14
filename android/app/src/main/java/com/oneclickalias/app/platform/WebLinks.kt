package com.oneclickalias.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import okhttp3.HttpUrl

/** Opens website pages in the user's browser; the app has no WebView. */
fun interface WebLinks {
    /** @return false when no app can open the link (no browser installed). */
    fun open(url: HttpUrl): Boolean
}

class BrowserLinks(context: Context) : WebLinks {
    private val appContext = context.applicationContext

    override fun open(url: HttpUrl): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toString().toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
