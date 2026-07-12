package com.brbrs.blik.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.brbrs.blik.di.AuthDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AuthCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
)

@Singleton
class AuthManager @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private val KEY_SERVER    = stringPreferencesKey("server_url")
        private val KEY_USER      = stringPreferencesKey("username")
        private val KEY_PASSWORD  = stringPreferencesKey("app_password")
        private val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
    }

    val credentials: Flow<AuthCredentials?> = dataStore.data.map { prefs ->
        val server = prefs[KEY_SERVER]   ?: return@map null
        val user   = prefs[KEY_USER]     ?: return@map null
        val pass   = prefs[KEY_PASSWORD] ?: return@map null
        AuthCredentials(server, user, pass)
    }

    val biometricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC] ?: false
    }

    suspend fun saveCredentials(server: String, username: String, appPassword: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER]   = server.trimEnd('/')
            prefs[KEY_USER]     = username
            prefs[KEY_PASSWORD] = appPassword
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC] = enabled }
    }

    suspend fun clearCredentials() {
        dataStore.edit { it.clear() }
    }

    data class LoginFlowInit(
        val loginUrl: String,
        val pollEndpoint: String,
        val token: String,
    )

    private fun normalizeUrl(input: String): String {
        var url = input.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url
    }

    /**
     * Rewrites the scheme+host+port portion of [serverProvidedUrl] to match
     * [userTypedBase], keeping the path/query/fragment intact.
     *
     * Nextcloud's Login Flow v2 "poll.endpoint" can point to an internal
     * hostname or IP that differs from the address the user actually typed
     * (e.g. Tailscale MagicDNS names, reverse-proxy subdomains). Using the
     * server-provided host directly can fail to resolve or connect, so we
     * substitute the user-typed origin instead.
     */
    private fun rewriteToUserHost(serverProvidedUrl: String, userTypedBase: String): String {
        return try {
            val userBase = java.net.URI(normalizeUrl(userTypedBase))
            val provided = java.net.URI(serverProvidedUrl)
            java.net.URI(
                userBase.scheme,
                provided.userInfo,
                userBase.host,
                userBase.port,
                provided.path,
                provided.query,
                provided.fragment
            ).toString()
        } catch (e: Exception) {
            serverProvidedUrl
        }
    }


    suspend fun initiateLoginFlow(serverUrl: String): Result<LoginFlowInit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = normalizeUrl(serverUrl)
                val url  = "$base/index.php/login/v2"
                val body = "".toRequestBody("application/x-www-form-urlencoded".toMediaType())
                val req  = Request.Builder().url(url).post(body).build()
                val resp = try {
                    okHttpClient.newCall(req).execute()
                } catch (e: Exception) { throw Exception(friendlyNetworkError(e)) }
                if (!resp.isSuccessful) throw Exception("Server returned HTTP ${resp.code}")
                val bodyStr = resp.body?.string() ?: throw Exception("Empty response from server")
                val json = try { JSONObject(bodyStr) } catch (e: Exception) {
                    throw Exception("Unexpected response — is this a Nextcloud instance?")
                }
                val pollObj = json.getJSONObject("poll")
                val rawPollEndpoint = pollObj.getString("endpoint")
                LoginFlowInit(
                    loginUrl     = json.getString("login"),
                    pollEndpoint = rewriteToUserHost(rawPollEndpoint, base),
                    token        = pollObj.getString("token"),
                )
            }
        }

    suspend fun pollLoginFlow(pollEndpoint: String, token: String): Result<AuthCredentials> =
        withContext(Dispatchers.IO) {
            runCatching {
                repeat(120) {
                    val body = "token=$token".toRequestBody("application/x-www-form-urlencoded".toMediaType())
                    val req  = Request.Builder().url(pollEndpoint).post(body).build()
                    val resp = try {
                        okHttpClient.newCall(req).execute()
                    } catch (e: Exception) { delay(3_000); return@repeat }
                    when (resp.code) {
                        200 -> {
                            val json   = JSONObject(resp.body!!.string())
                            val server = json.getString("server").trimEnd('/')
                            val user   = json.getString("loginName")
                            val pass   = json.getString("appPassword")
                            saveCredentials(server, user, pass)
                            return@runCatching AuthCredentials(server, user, pass)
                        }
                        else -> delay(2_000)
                    }
                }
                throw Exception("Login timed out — please try again")
            }
        }

    private fun friendlyNetworkError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Cannot reach server — check the URL and your internet connection"
            msg.contains("CERTIFICATE", ignoreCase = true) ||
            msg.contains("SSL", ignoreCase = true) -> "SSL certificate error"
            msg.contains("timeout", ignoreCase = true) -> "Connection timed out"
            msg.contains("Connection refused", ignoreCase = true) -> "Connection refused"
            else -> "Connection failed: $msg"
        }
    }
}
