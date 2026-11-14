package com.oneclickalias.app.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Copies an alias address. Aliases are not secret: plain clip, no sensitive
 * flag, no automatic clearing (unlike [SensitiveClipboard]).
 */
fun interface AliasClipboard {
    /** @return true when the system shows its own copy confirmation (Android 13+). */
    fun copy(alias: String): Boolean
}

class SystemAliasClipboard(context: Context) : AliasClipboard {
    private val manager = context.applicationContext.getSystemService(ClipboardManager::class.java)

    override fun copy(alias: String): Boolean {
        manager.setPrimaryClip(ClipData.newPlainText(LABEL, alias))
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private companion object {
        const val LABEL = "Alias"
    }
}
