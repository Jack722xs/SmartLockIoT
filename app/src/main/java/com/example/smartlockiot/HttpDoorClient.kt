package com.example.smartlockiot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object HttpDoorClient {
    /** POST HTTPS {"action":"unlock|lock"}. Rechaza cualquier URL no HTTPS. */
    suspend fun send(url: String, token: String, action: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(url.startsWith("https://")) { "Solo se permite HTTPS" }
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.connectTimeout = 5000
                    c.readTimeout = 5000
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
                    c.outputStream.use {
                        it.write("""{"action":"$action","source":"android_admin"}""".toByteArray())
                    }
                    val code = c.responseCode
                    if (code !in 200..299) error("HTTP $code")
                    code
                } finally {
                    c.disconnect()
                }
            }
        }
}
