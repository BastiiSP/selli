package com.prehmus.selli.data.places

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Paketname und SHA-1-Fingerabdruck der Signatur, mit der diese APK signiert wurde. */
data class AndroidAppIdentity(
    val packageName: String,
    val certSha1: String,
)

/**
 * Liest die eigene App-Identität zur Laufzeit — genau die beiden Werte, mit denen ein auf
 * Android beschränkter Google-API-Schlüssel geprüft wird.
 *
 * Bewusst ausgelesen statt hartkodiert: so passt der Fingerabdruck automatisch, egal ob
 * die APK mit dem Debug- oder einem späteren Release-Keystore signiert wurde. Lässt sich
 * die Signatur nicht ermitteln, kommt null zurück und der Aufrufer verzichtet auf die
 * Identitäts-Header (die Anfrage scheitert dann sichtbar, statt still falsch zu laufen).
 */
fun readAndroidAppIdentity(context: Context): AndroidAppIdentity? = try {
    val packageName = context.packageName
    val signatures = context.packageManager.signatureBytes(packageName)
    val firstSignature = signatures.firstOrNull()

    if (firstSignature == null) {
        null
    } else {
        AndroidAppIdentity(
            packageName = packageName,
            certSha1 = MessageDigest.getInstance("SHA-1")
                .digest(firstSignature)
                .joinToString(separator = "") { byte -> "%02X".format(byte) },
        )
    }
} catch (_: Exception) {
    // Signatur nicht lesbar — dann eben keine Identitäts-Header.
    null
}

private fun PackageManager.signatureBytes(packageName: String): List<ByteArray> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        val packageInfo = getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        packageInfo.signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            signature.toByteArray()
        }
    } else {
        @Suppress("DEPRECATION")
        val packageInfo = getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        packageInfo.signatures.orEmpty().map { signature -> signature.toByteArray() }
    }
