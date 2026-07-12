package com.brbrs.vinci.network

import android.util.Base64
import com.brbrs.vinci.auth.AuthRepository
import com.brbrs.vinci.auth.VinciSession
import com.brbrs.vinci.ui.components.SOCIAL_PLATFORMS
import com.brbrs.vinci.ui.components.platformForUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes contact data directly from/to Nextcloud via CardDAV.
 * Used specifically for fields DAVx5 does not sync into Android ContactsContract,
 * such as X-SOCIALPROFILE.
 */
@Singleton
class CardDavRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val httpClient: OkHttpClient,
) {
    private fun authedClient(session: VinciSession) = httpClient.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", session.basicAuthHeader())
                    .build()
            )
        }
        .build()

    // -- Address book discovery -----------------------------------------------

    /**
     * Discovers the primary address book URL for the user via PROPFIND.
     * Returns something like:
     *   https://server/remote.php/dav/addressbooks/users/username/contacts-1/
     * Cached in AuthRepository DataStore after first discovery.
     */
    suspend fun discoverAddressBookUrl(): String? = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: run {
            return@withContext null
        }

        session.addressBookUrl.let {
            if (it.isNotBlank() && !it.endsWith(".vcf")) {
                return@withContext it
            }
            if (it.endsWith(".vcf")) {
                kotlinx.coroutines.runBlocking { authRepository.saveAddressBookUrl("") }
            }
        }

        val client = authedClient(session)
        val base   = "${session.serverUrl.trimEnd('/')}/remote.php/dav/addressbooks/users/${session.username}/"

        val propfind = """<?xml version="1.0" encoding="utf-8"?>
<propfind xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
  <prop><displayname/><resourcetype/></prop>
</propfind>"""

        return@withContext runCatching {
            val resp = client.newCall(
                Request.Builder()
                    .url(base)
                    .method("PROPFIND", propfind.toRequestBody("application/xml".toMediaType()))
                    .header("Depth", "1")
                    .build()
            ).execute()

            val body = resp.body?.string() ?: run {
                resp.close()
                return@runCatching null
            }
            resp.close()

            val hrefRegex = Regex(
                """<[^>]*:?href[^>]*>([^<]+/)</[^>]*:?href>""",
                RegexOption.IGNORE_CASE
            )
            val basePath = "/remote.php/dav/addressbooks/users/${session.username}/"
            val allHrefs = hrefRegex.findAll(body).map { it.groupValues[1] }.toList()

            val path = allHrefs.firstOrNull { it != basePath && it.startsWith(basePath) }
                ?: run {
                    // Fallback: try well-known Nextcloud address book paths directly
                    val fallbacks = listOf("contacts", "contacts-1", "default", "addressbook")
                    fallbacks.map { "$basePath$it/" }
                        .firstOrNull { fallbackPath ->
                            val testUrl = "${session.serverUrl.trimEnd('/')}$fallbackPath"
                            val testResp = runCatching {
                                client.newCall(Request.Builder().url(testUrl)
                                    .method("PROPFIND", propfind.toRequestBody("application/xml".toMediaType()))
                                    .header("Depth", "0").build()).execute()
                            }.getOrNull()
                            val ok = testResp?.isSuccessful == true
                            testResp?.close()
                            ok
                        }
                        ?: run {
                            return@runCatching null
                        }
                }

            val url = if (path.startsWith("http")) path
                      else "${session.serverUrl.trimEnd('/')}$path"
            authRepository.saveAddressBookUrl(url)
            url
        }.getOrNull()
    }

    // -- Address book UID listing ------------------------------------------

    /**
     * PROPFINDs the address book to get the list of all VCF filenames (UIDs).
     * Returns a Set of UIDs like "21f30e04-ba51-4302-a293-d439a21fd5dd".
     * One request instead of N requests -- used to filter before fetching.
     */
    suspend fun listAddressBookUids(): Set<String> = withContext(Dispatchers.IO) {
        val session     = authRepository.session.first() ?: return@withContext emptySet()
        val addressBook = discoverAddressBookUrl() ?: return@withContext emptySet()
        val client      = authedClient(session)

        val propfind = """<?xml version="1.0" encoding="utf-8"?>
<propfind xmlns="DAV:"><prop><getetag/></prop></propfind>"""

        return@withContext runCatching {
            val resp = client.newCall(
                Request.Builder()
                    .url(addressBook)
                    .method("PROPFIND", propfind.toRequestBody("application/xml".toMediaType()))
                    .header("Depth", "1")
                    .build()
            ).execute()
            val body = resp.body?.string() ?: return@runCatching emptySet()
            resp.close()

            // Extract hrefs that end with .vcf and strip to UID
            val hrefRegex = Regex(
                """<[^>]*:href[^>]*>([^<]+\.vcf)</[^>]*:href>""",
                RegexOption.IGNORE_CASE
            )
            hrefRegex.findAll(body)
                .map { it.groupValues[1].trimEnd('/').substringAfterLast('/').removeSuffix(".vcf") }
                .filter { it.isNotBlank() }
                .toSet()
        }.getOrDefault(emptySet()).also {
        }
    }

    // -- vCard fetch ----------------------------------------------------------

    /**
     * Fetches the raw vCard for a contact by UID.
     * Returns the vCard text or null if not found.
     */
    suspend fun fetchVCard(uid: String): String? = withContext(Dispatchers.IO) {
        val session      = authRepository.session.first() ?: return@withContext null
        val addressBook  = discoverAddressBookUrl()
        if (addressBook == null) {
            return@withContext null
        }
        val client = authedClient(session)
        val url    = "$addressBook$uid.vcf"

        return@withContext runCatching {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!resp.isSuccessful) { resp.close(); return@runCatching null }
            val body = resp.body?.string()
            resp.close()
            body
        }.getOrNull()
    }

    suspend fun fetchSocialLinks(uid: String): String? = withContext(Dispatchers.IO) {
        val vcard = fetchVCard(uid) ?: return@withContext null
        val result = parseSocialLinks(vcard)
        result
    }

    // -- vCard write ----------------------------------------------------------

    /**
     * Writes updated social links back to a contact's vCard on Nextcloud.
     * Fetches the current vCard, replaces X-SOCIALPROFILE and URL lines,
     * and PUTs it back with the ETag for conflict detection.
     */
    suspend fun writeSocialLinks(uid: String, socialLinksJson: String): Boolean = withContext(Dispatchers.IO) {
        val session     = authRepository.session.first() ?: return@withContext false
        val addressBook = discoverAddressBookUrl() ?: return@withContext false
        val client      = authedClient(session)
        val url         = "$addressBook$uid.vcf"

        return@withContext runCatching {
            // 1. Fetch current vCard and ETag
            val getResp = client.newCall(
                Request.Builder().url(url).get()
                    .header("Accept", "text/vcard; version=3.0")
                    .build()
            ).execute()
            if (!getResp.isSuccessful) return@runCatching false
            val etag  = getResp.header("ETag") ?: ""
            val vcard = getResp.body?.string() ?: return@runCatching false
            getResp.close()

            // 2. Strip existing X-SOCIALPROFILE and social URL lines
            val cleaned = vcard.lines().filter { line ->
                !line.startsWith("X-SOCIALPROFILE", ignoreCase = true) &&
                !line.startsWith("IMPP", ignoreCase = true)
            }.joinToString("\n")

            // 3. Build new X-SOCIALPROFILE lines from JSON
            val newLines = buildSocialLines(socialLinksJson)

            // 4. Insert before END:VCARD
            val updated = cleaned.replace(
                "END:VCARD",
                "${newLines}END:VCARD"
            )

            // 5. PUT back with ETag
            val putReq = Request.Builder()
                .url(url)
                .put(updated.toRequestBody("text/vcard; charset=utf-8".toMediaType()))
                .apply { if (etag.isNotBlank()) header("If-Match", etag) }
                .build()
            val putResp = client.newCall(putReq).execute()
            putResp.isSuccessful.also { putResp.close() }
        }.getOrDefault(false)
    }

    // -- vCard parsers --------------------------------------------------------

    /**
     * Parses X-SOCIALPROFILE;TYPE=LINKEDIN:https://...
     * and URL;TYPE=HOME;VALUE=URI:https://...
     * from a raw vCard string.
     */
    fun parseSocialLinks(vcard: String): String {
        val links = JSONArray()
        val seen  = mutableSetOf<String>()

        // Unfold vCard lines (lines ending with \r\n + whitespace are continuations)
        val unfolded = vcard.replace(Regex("\r\n[ \t]"), "").replace(Regex("\n[ \t]"), "")

        unfolded.lines().forEach { line ->
            val upper = line.uppercase().trimStart()
            when {
                // X-SOCIALPROFILE;TYPE=LINKEDIN:https://...
                upper.startsWith("X-SOCIALPROFILE") -> {
                    // Find the URL — it starts after the last colon that precedes "http"
                    // e.g. X-SOCIALPROFILE;TYPE=LINKEDIN:https://linkedin.com/in/user
                    val colonIdx = line.indexOf(":http", ignoreCase = true)
                        .takeIf { it >= 0 } ?: line.lastIndexOf(":")
                    val url = if (colonIdx >= 0) line.substring(colonIdx + 1).trim() else ""
                    if (url.isBlank() || url in seen) return@forEach
                    seen.add(url)
                    // Extract TYPE value
                    val typeParam = Regex("TYPE=([^;:]+)", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.get(1)?.trim() ?: ""
                    val platform = SOCIAL_PLATFORMS
                        .firstOrNull { it.key.equals(typeParam, ignoreCase = true) || it.label.equals(typeParam, ignoreCase = true) }
                        ?: platformForUrl(url)
                    links.put(JSONObject().apply {
                        put("platform", platform.key)
                        put("url", url)
                    })
                }

                // IMPP:xmpp:user@mastodon.social etc.
                upper.startsWith("IMPP") -> {
                    val colonIdx = line.indexOf(":http", ignoreCase = true)
                        .takeIf { it >= 0 } ?: line.lastIndexOf(":")
                    val value = if (colonIdx >= 0) line.substring(colonIdx + 1).trim() else ""
                    if (value.isBlank() || value in seen) return@forEach
                    // Only include http/https URIs
                    if (!value.startsWith("http", ignoreCase = true)) return@forEach
                    seen.add(value)
                    val platform = platformForUrl(value)
                    links.put(JSONObject().apply {
                        put("platform", platform.key)
                        put("url", value)
                    })
                }

                // URL;TYPE=HOME;VALUE=URI:https://barburas.com
                upper.startsWith("URL") -> {
                    // URL;TYPE=HOME;VALUE=URI:https://barburas.com
                    val colonIdx = line.indexOf(":http", ignoreCase = true)
                        .takeIf { it >= 0 } ?: line.lastIndexOf(":")
                    val url = if (colonIdx >= 0) line.substring(colonIdx + 1).trim() else ""
                    if (url.isBlank() || url in seen) return@forEach
                    if (!url.startsWith("http", ignoreCase = true)) return@forEach
                    seen.add(url)
                    val typeParam = Regex("TYPE=([^;:]+)", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.get(1)?.trim() ?: ""
                    // Try to match a known platform first
                    val knownPlatform = SOCIAL_PLATFORMS
                        .firstOrNull { it.key.equals(typeParam, ignoreCase = true) }
                        ?: platformForUrl(url)
                    // For plain website URLs (home, work, etc.), store a custom label
                    val platformKey   = knownPlatform.key
                    val displayLabel  = if (platformKey == "other" && typeParam.isNotBlank())
                        typeParam.replaceFirstChar { it.uppercase() }  // "home" -> "Home"
                    else null
                    links.put(JSONObject().apply {
                        put("platform", platformKey)
                        put("url", url)
                        if (displayLabel != null) put("label", displayLabel)
                    })
                }
            }
        }

        val result = links.toString()
        return result
    }

    private fun buildSocialLines(socialLinksJson: String): String {
        if (socialLinksJson.isBlank() || socialLinksJson == "[]") return ""
        return try {
            val arr = JSONArray(socialLinksJson)
            buildString {
                for (i in 0 until arr.length()) {
                    val obj      = arr.getJSONObject(i)
                    val url      = obj.optString("url", "").trim()
                    val key      = obj.optString("platform", "other")
                    val platform = SOCIAL_PLATFORMS.firstOrNull { it.key == key }
                    if (url.isBlank()) continue

                    if (platform != null && platform.key != "other") {
                        appendLine("X-SOCIALPROFILE;TYPE=${platform.label.uppercase()}:$url")
                    } else {
                        appendLine("URL;TYPE=HOME;VALUE=URI:$url")
                    }
                }
            }
        } catch (e: Exception) { "" }
    }
}
