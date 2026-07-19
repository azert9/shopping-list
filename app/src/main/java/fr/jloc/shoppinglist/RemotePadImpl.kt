@file:OptIn(ExperimentalSerializationApi::class)

package fr.jloc.shoppinglist

import fr.jloc.shoppinglist.business.PadItemContent
import fr.jloc.shoppinglist.business.sync.RemotePad
import fr.jloc.shoppinglist.business.sync.PadKey
import fr.jloc.shoppinglist.business.sync.SyncError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.io.use

// TODO: compress before encrypting ?

class RemotePadImpl(
    private val url: String,
    private val key: PadKey,
) : RemotePad {

    override suspend fun get(cachedTag: String?): RemotePad.ItemsAndTag? {

        val fetched = withContext(Dispatchers.IO) {
            val conn = makeHttpConnection(url, key)
            // TODO: If-Not-Match?
            try {
                if (conn.responseCode == 404 && cachedTag == null) {
                    return@withContext null
                }
                if (conn.responseCode != 200) {
                    throw exceptionFromResponse(conn)
                }
                if (conn.contentType.lowercase() != "application/octet-stream") {
                    throw SyncError.newServerError("Server responded with an unexpected Content-Type: ${conn.contentType} (status ${conn.responseCode})")
                }
                Pair(
                    conn.getInputStream().use { it.readBytes() },
                    extractETagFromResponse(conn),
                )
            } catch (e: Exception) {
                throw makeSyncError(e)
            } finally {
                conn.disconnect()
            }
        }

        if (fetched == null) {
            return null // expected 404
        }
        val (fetchedBytes, fetchedETag) = fetched

        if (fetchedETag == cachedTag) {
            return null
        }

        val decrypted = key.decrypt(fetchedBytes)

        val decoded = Json.decodeFromStream<Map<String, SerializablePadItemContent>>(
            ByteArrayInputStream(decrypted)
        ).mapValues { PadItemContent(checked = it.value.checked, note = it.value.note) }

        return RemotePad.ItemsAndTag(
            items = decoded,
            tag = fetchedETag,
        )
    }

    override suspend fun put(
        items: Map<String, PadItemContent>,
        baseTag: String?,
    ): String {

        val serializedItems = Json.encodeToString(items.mapValues {
            SerializablePadItemContent(
                checked = it.value.checked,
                note = it.value.note,
            )
        }).encodeToByteArray()

        val encryptedItems = key.encrypt(serializedItems)

        return withContext(Dispatchers.IO) {
            val conn = makeHttpConnection(url, key)
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            if (baseTag != null) {
                conn.setRequestProperty("If-Match", "\"$baseTag\"")
            }
            conn.getOutputStream().use { it.write(encryptedItems) }
            try {
                if (conn.responseCode != 201 && conn.responseCode != 204) {
                    throw exceptionFromResponse(conn)
                }
                extractETagFromResponse(conn)
            } catch (e: Exception) {
                throw makeSyncError(e)
            } finally {
                conn.disconnect()
            }
        }
    }

    override suspend fun delete() {
        withContext(Dispatchers.IO) {
            val conn = makeHttpConnection(url, key)
            try {
                conn.requestMethod = "DELETE"
                conn.getOutputStream().use { }
                if (conn.responseCode != 404 && conn.responseCode != 204) {
                    throw exceptionFromResponse(conn)
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}

private fun makeHttpConnection(baseURL: String, key: PadKey): HttpURLConnection {

    val parsedBaseURL = URL(baseURL)

    val conn = URL(
        parsedBaseURL.protocol,
        parsedBaseURL.host,
        parsedBaseURL.port,
        parsedBaseURL.file + "/v1/store/" + key.deriveRemoteID(),
    ).openConnection()

    conn.connectTimeout = 10000
    conn.readTimeout = 10000

    return conn as HttpURLConnection
}

private fun makeSyncError(exception: Exception): SyncError {
    return when (exception) {
        is SyncError -> exception
        is IOException -> SyncError.newNetworkError("Request failed.", exception)
        is Exception -> SyncError.newUnexpectedError("Request failed.", exception)
    }
}

private val ETAG_HEADER_REGEX = Regex("^\\s*\"([^\"]*)\"\\s*$")

private fun extractETagFromResponse(resp: HttpURLConnection): String {

    val etagValues = resp.headerFields["ETag"] ?: listOf()

    if (etagValues.isEmpty()) {
        throw SyncError.newServerError("Response is missing an ETag (status ${resp.responseCode}).")
    }

    if (etagValues.size > 1) {
        throw SyncError.newServerError("Response contains multiple ETags.")
    }

    val match = ETAG_HEADER_REGEX.matchEntire(etagValues[0])
        ?: throw SyncError.newServerError("Invalid ETag header in response.")

    return match.groups[1]!!.value
}

private fun exceptionFromResponse(resp: HttpURLConnection): SyncError =
    SyncError.newServerError("Bad server response with status ${resp.responseCode}.")

@Serializable
private data class SerializablePadItemContent(
    val checked: Boolean,
    val note: String,
)
