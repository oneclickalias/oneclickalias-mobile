package com.oneclickalias.app.data.session

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * JVM stand-in for the Android Keystore (Robolectric has none): a plain AES key
 * in memory. [AesGcmTokenCipher] behaves the same with it; the Keystore source
 * itself is covered by the instrumented test on a device.
 */
class SoftwareKeySource : SecretKeySource {
    var key: SecretKey? = null
    var deletions = 0

    override fun getOrCreate(): SecretKey =
        key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }

    override fun delete() {
        key = null
        deletions++
    }
}
