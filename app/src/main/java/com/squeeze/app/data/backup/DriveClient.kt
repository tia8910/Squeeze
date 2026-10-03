package com.squeeze.app.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The backup file found in Drive, with when it was last written. */
data class RemoteBackup(val id: String, val modifiedMs: Long?)

/** HTTP 401: the access token expired or was revoked. */
class DriveUnauthorized : IOException("Google sign-in expired")

/**
 * The three Drive calls backup needs — find, write, read — against the app-data folder.
 *
 * **The app-data folder** is a hidden, per-app space in the user's own Drive: the user can see
 * how much it holds and delete it from Drive's settings, but no other app can read it, and the
 * `drive.appdata` scope that reaches it grants nothing else in their Drive.
 *
 * Plain HTTPS rather than the Google API client library, which would add several megabytes
 * and a dependency tree for three requests.
 */
@Singleton
class DriveClient @Inject constructor() {

    suspend fun find(token: String): RemoteBackup? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val body = request(
            "GET",
            "$API/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)&orderBy=modifiedTime%20desc",
            token,
        )
        val files = JSONObject(body).optJSONArray("files") ?: return@withContext null
        if (files.length() == 0) return@withContext null
        val f = files.getJSONObject(0)
        RemoteBackup(
            id = f.getString("id"),
            modifiedMs = f.optString("modifiedTime").takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        )
    }

    suspend fun download(token: String, id: String): String = withContext(Dispatchers.IO) {
        request("GET", "$API/files/$id?alt=media", token)
    }

    /** Overwrites the existing backup, or creates the first one. */
    suspend fun upload(token: String, json: String, existingId: String?) = withContext(Dispatchers.IO) {
        if (existingId != null) {
            // HttpURLConnection has no PATCH; Google's APIs accept the override header.
            request("POST", "$UPLOAD/files/$existingId?uploadType=media", token, json.toByteArray(), "application/json", patch = true)
        } else {
            val boundary = "squeeze${System.nanoTime()}"
            val metadata = JSONObject().put("name", FILE_NAME).put("parents", org.json.JSONArray().put("appDataFolder"))
            val multipart = buildString {
                append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadata.toString()).append("\r\n")
                append("--$boundary\r\nContent-Type: application/json\r\n\r\n")
                append(json).append("\r\n")
                append("--$boundary--")
            }
            request("POST", "$UPLOAD/files?uploadType=multipart", token, multipart.toByteArray(), "multipart/related; boundary=$boundary")
        }
        Unit
    }

    private fun request(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null,
        patch: Boolean = false,
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (patch) conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw DriveUnauthorized()
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Google Drive returned $code ${error.take(200)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_NAME = "squeeze-backup.json"
        const val TIMEOUT_MS = 30_000
    }
}
