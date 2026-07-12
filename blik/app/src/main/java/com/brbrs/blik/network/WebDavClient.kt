/*
 * Blik — a Nextcloud screenshot manager for Android
 * Copyright (C) 2026 andrei BARBURAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See <https://www.gnu.org/licenses/>.
 */

package com.brbrs.blik.network

import com.brbrs.blik.auth.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private fun davBase(creds: AuthCredentials) =
        "${creds.serverUrl}/remote.php/dav/files/${creds.username}"

    private fun authHeader(creds: AuthCredentials) =
        Credentials.basic(creds.username, creds.appPassword)

    /** Ensure every component of the remote path exists (MKCOL). */
    suspend fun ensureFolder(creds: AuthCredentials, remoteFolderPath: String) =
        withContext(Dispatchers.IO) {
            val parts = remoteFolderPath.trimStart('/').split("/")
            var built = ""
            for (part in parts) {
                built += "/$part"
                val url = "${davBase(creds)}$built"
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", authHeader(creds))
                    .method("MKCOL", null)
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                // 201 = created, 405 = already exists — both are fine
                if (!resp.isSuccessful && resp.code != 405) {
                    throw Exception("MKCOL $url → HTTP ${resp.code}")
                }
            }
        }

    /** Upload a file via PUT. Returns the remote path. */
    suspend fun upload(
        creds: AuthCredentials,
        file: File,
        remoteFolderPath: String,
        remoteFileName: String,
    ): String = withContext(Dispatchers.IO) {
        val remotePath = "${remoteFolderPath.trimEnd('/')}/$remoteFileName"
        val url = "${davBase(creds)}$remotePath"
        val body = file.asRequestBody("image/png".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(creds))
            .put(body)
            .build()
        val resp = okHttpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("Upload failed: HTTP ${resp.code}")
        remotePath
    }

    /** Upload a markdown sidecar file. */
    suspend fun uploadMarkdown(
        creds: AuthCredentials,
        content: String,
        remoteFolderPath: String,
        remoteFileName: String,
    ) = withContext(Dispatchers.IO) {
        val remotePath = "${remoteFolderPath.trimEnd('/')}/$remoteFileName"
        val url = "${davBase(creds)}$remotePath"
        val body = content.toRequestBody("text/markdown".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(creds))
            .put(body)
            .build()
        val resp = okHttpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("Markdown upload failed: HTTP ${resp.code}")
    }

    /** Delete a remote file via DELETE. */
    suspend fun deleteFile(creds: AuthCredentials, remotePath: String) =
        withContext(Dispatchers.IO) {
            val url = "${davBase(creds)}$remotePath"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(creds))
                .delete()
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful && resp.code != 404) {
                throw Exception("DELETE $remotePath → HTTP ${resp.code}")
            }
        }

    /** Check if a remote file already exists. */
    suspend fun exists(creds: AuthCredentials, remotePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = "${davBase(creds)}$remotePath"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(creds))
                .method("HEAD", null)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            resp.isSuccessful
        }

    /** List files in a remote folder. Returns list of filenames. */
    suspend fun listFolder(creds: AuthCredentials, remoteFolderPath: String): List<String> =
        withContext(Dispatchers.IO) {
            val url = "${davBase(creds)}${remoteFolderPath.trimEnd('/')}"
            val propfindBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop><d:getlastmodified/><d:displayname/></d:prop>
                </d:propfind>
            """.trimIndent().toRequestBody("application/xml".toMediaType())
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(creds))
                .header("Depth", "1")
                .method("PROPFIND", propfindBody)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val regex = Regex("<[^:]*:displayname>([^<]+)</[^:]*:displayname>")
            regex.findAll(body).map { it.groupValues[1] }.toList()
        }

    /** List only files (non-collections) inside [remoteFolderPath]. Returns file names. */
    suspend fun listFiles(creds: AuthCredentials, remoteFolderPath: String): List<String> =
        withContext(Dispatchers.IO) {
            val basePath = remoteFolderPath.trimEnd('/')
            val url      = "${davBase(creds)}$basePath"
            val propfindBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop><d:displayname/><d:resourcetype/></d:prop>
                </d:propfind>
            """.trimIndent().toRequestBody("application/xml".toMediaType())
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(creds))
                .header("Depth", "1")
                .method("PROPFIND", propfindBody)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val bodyBytes = resp.body?.bytes() ?: return@withContext emptyList()
            parseFileNames(bodyBytes, basePath, davBase(creds))
        }

    private fun parseFileNames(xml: ByteArray, basePath: String, davBaseUrl: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val parser = android.util.Xml.newPullParser()
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
            parser.setInput(xml.inputStream(), "UTF-8")
            var inResponse = false; var href = ""; var displayName = ""; var isCollection = false
            var captureText = false; var currentTag = ""
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val local = parser.name.substringAfterLast(':').lowercase()
                        when {
                            local == "response"    -> { inResponse = true; href = ""; displayName = ""; isCollection = false }
                            inResponse && local == "href"        -> { captureText = true; currentTag = "href" }
                            inResponse && local == "displayname" -> { captureText = true; currentTag = "displayname" }
                            inResponse && local == "collection"  -> { isCollection = true }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (captureText) when (currentTag) {
                            "href"        -> href        += parser.text ?: ""
                            "displayname" -> displayName += parser.text ?: ""
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val local = parser.name.substringAfterLast(':').lowercase()
                        when {
                            local == "href" || local == "displayname" -> { captureText = false; currentTag = "" }
                            local == "response" && inResponse -> {
                                inResponse = false
                                if (!isCollection) {
                                    val decodedHref = java.net.URLDecoder.decode(href.trimEnd('/'), "UTF-8")
                                    val hrefPath = decodedHref.removePrefix(davBaseUrl).trimEnd('/')
                                    if (hrefPath != basePath) {
                                        val name = displayName.trim().ifBlank {
                                            java.net.URLDecoder.decode(href.trimEnd('/').substringAfterLast('/'), "UTF-8")
                                        }
                                        if (name.isNotBlank()) result += name
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavClient", "parseFileNames error: ${e.message}")
        }
        return result
    }

    /**
     * List only sub-folders (collections) inside [remoteFolderPath].
     * Returns folder names (not full paths) suitable for a folder picker UI.
     *
     * Uses Android's XmlPullParser so it handles any namespace prefix
     * (d:, D:, DAV:, etc.) and any whitespace Nextcloud throws at us.
     */
    suspend fun listFolders(creds: AuthCredentials, remoteFolderPath: String): List<String> =
        withContext(Dispatchers.IO) {
            val basePath = remoteFolderPath.trimEnd('/')
            val url      = "${davBase(creds)}$basePath"

            val propfindBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop>
                        <d:displayname/>
                        <d:resourcetype/>
                    </d:prop>
                </d:propfind>
            """.trimIndent().toRequestBody("application/xml".toMediaType())

            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(creds))
                .header("Depth", "1")
                .method("PROPFIND", propfindBody)
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("PROPFIND failed: HTTP ${resp.code}")
            val bodyBytes = resp.body?.bytes() ?: return@withContext emptyList()

            parsePropfindFolders(bodyBytes, basePath, davBase(creds))
        }

    /**
     * Parse a PROPFIND multistatus response and return folder display names.
     * Robust against any XML namespace prefix Nextcloud may use.
     */
    private fun parsePropfindFolders(
        xml: ByteArray,
        basePath: String,
        davBaseUrl: String,
    ): List<String> {
        val result = mutableListOf<String>()
        try {
            val parser = android.util.Xml.newPullParser()
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
            parser.setInput(xml.inputStream(), "UTF-8")

            // Per-response state
            var inResponse   = false
            var href         = ""
            var displayName  = ""
            var isCollection = false
            var captureText  = false
            var currentTag   = ""

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val local = parser.name.substringAfterLast(':').lowercase()
                        when {
                            local == "response" -> {
                                inResponse   = true
                                href         = ""
                                displayName  = ""
                                isCollection = false
                            }
                            inResponse && local == "href" -> {
                                captureText = true; currentTag = "href"
                            }
                            inResponse && local == "displayname" -> {
                                captureText = true; currentTag = "displayname"
                            }
                            inResponse && local == "collection" -> {
                                isCollection = true
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (captureText) {
                            val text = parser.text ?: ""
                            when (currentTag) {
                                "href"        -> href        += text
                                "displayname" -> displayName += text
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val local = parser.name.substringAfterLast(':').lowercase()
                        when {
                            local == "href" || local == "displayname" -> {
                                captureText = false; currentTag = ""
                            }
                            local == "response" && inResponse -> {
                                inResponse = false
                                if (isCollection) {
                                    val decodedHref = java.net.URLDecoder.decode(
                                        href.trimEnd('/'), "UTF-8"
                                    )
                                    val hrefPath = decodedHref
                                        .removePrefix(davBaseUrl)
                                        .trimEnd('/')
                                    if (hrefPath != basePath) {
                                        val name = displayName.trim().ifBlank {
                                            java.net.URLDecoder.decode(
                                                href.trimEnd('/').substringAfterLast('/'),
                                                "UTF-8"
                                            )
                                        }
                                        if (name.isNotBlank()) result += name
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavClient", "PROPFIND parse error: ${e.message}")
        }
        return result
    }
}
