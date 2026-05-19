package radio.ks3ckc.ft8us.qrz

import com.bg7yoz.ft8cn.GeneralVariables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Lightweight client for the QRZ XML callsign API (xmldata.qrz.com).
 * - Holds an in-memory session key; re-authenticates on demand.
 * - Caches per-callsign lookups in-memory so repeated UI recompositions
 *   don't refetch.
 * - Never throws to callers: any failure (missing creds, no XML
 *   subscription, network down, callsign not found) resolves as null.
 */
object QrzXmlClient {
    private const val BASE_URL = "https://xmldata.qrz.com/xml/current/"
    private const val CACHE_MAX = 200

    private val sessionMutex = Mutex()
    @Volatile private var sessionKey: String? = null

    private val cacheMutex = Mutex()
    private val cache = LinkedHashMap<String, QrzLookup>(16, 0.75f, true)

    data class QrzLookup(val imageUrl: String?)

    suspend fun lookup(callsign: String): QrzLookup? = withContext(Dispatchers.IO) {
        val key = callsign.trim().uppercase()
        if (key.isEmpty()) return@withContext null

        cacheMutex.withLock { cache[key] }?.let { return@withContext it }

        val user = GeneralVariables.qrzXmlUsername.orEmpty()
        val pass = GeneralVariables.qrzXmlPassword.orEmpty()
        if (user.isEmpty() || pass.isEmpty()) return@withContext null

        var session = ensureSession(user, pass) ?: return@withContext null
        var xml = fetch("s=$session&callsign=${urlEncode(key)}") ?: return@withContext null

        // QRZ returns an Error message with "Invalid session key" or similar when
        // the session expires; reauth once and retry before giving up.
        if (xml.contains("Invalid session", ignoreCase = true) ||
            xml.contains("Session Timeout", ignoreCase = true)
        ) {
            sessionMutex.withLock { sessionKey = null }
            session = ensureSession(user, pass) ?: return@withContext null
            xml = fetch("s=$session&callsign=${urlEncode(key)}") ?: return@withContext null
        }

        val image = parseTag(xml, "image")
        val result = QrzLookup(imageUrl = image)
        cacheMutex.withLock {
            cache[key] = result
            while (cache.size > CACHE_MAX) {
                val oldest = cache.entries.iterator().next()
                cache.remove(oldest.key)
            }
        }
        result
    }

    private suspend fun ensureSession(user: String, pass: String): String? {
        sessionMutex.withLock {
            sessionKey?.let { return it }
        }
        val body =
            "username=${urlEncode(user)};password=${urlEncode(pass)};agent=ft8us-1.0"
        val xml = fetchRaw(body) ?: return null
        val key = parseTag(xml, "Key") ?: return null
        sessionMutex.withLock { sessionKey = key }
        return key
    }

    private fun fetch(body: String): String? = fetchRaw(body)

    private fun fetchRaw(body: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded;charset=UTF-8",
                )
            }
            val out: OutputStream = conn.outputStream
            out.write(body.toByteArray(StandardCharsets.UTF_8))
            out.close()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTag(xml: String, tagName: String): String? {
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(xml.reader())
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals(tagName, ignoreCase = true)) {
                    val text = parser.nextText()
                    if (!text.isNullOrEmpty()) return text
                }
                event = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun urlEncode(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}
