package com.example.smartlockiot

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Almacenamiento cifrado (AES256) de endpoint y token - ISO/IEC 27400: proteccion de datos sensibles. */
class SecureStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_cfg",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var endpoint: String
        get() = prefs.getString("endpoint", "") ?: ""
        set(v) = prefs.edit().putString("endpoint", v).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v).apply()
}
