package com.brbrs.nota.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nota_auth")

// ── Nextcloud Login Flow v2 DTOs ──────────────────────────────────────────────

data class LoginFlowInit(
    val poll: PollEndpoint,
    val login: String,          // URL to open in browser
)

data class PollEndpoint(
    val token: String,
    val endpoint: String,
)

data class LoginCredentials(
    val server: String,
    val loginName: String,
    val appPassword: String,
)

// ── Stored session ────────────────────────────────────────────────────────────

data class NotaSession(
    val serverUrl: String,      // normalized, no trailing slash
    val username: String,
    val appPassword: String,    // Nextcloud app password (not user password)
) {
    fun basicAuthHeader(): String {
        val raw = "$username:$appPassword"
        return "Basic " + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
    }
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val SERVER_KEY  = stringPreferencesKey("server_url")
    private val USER_KEY    = stringPreferencesKey("username")
    private val PASS_KEY    = stringPreferencesKey("app_password")
    private val APP_LOCK    = stringPreferencesKey("app_lock_enabled")

    // ── Session ───────────────────────────────────────────────────────────────

    val session: Flow<NotaSession?> = context.dataStore.data.map { prefs ->
        val server = prefs[SERVER_KEY] ?: return@map null
        val user   = prefs[USER_KEY]   ?: return@map null
        val pass   = prefs[PASS_KEY]   ?: return@map null
        NotaSession(server, user, pass)
    }

    suspend fun isLoggedIn(): Boolean = session.first() != null

    suspend fun saveSession(session: NotaSession) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_KEY] = session.serverUrl
            prefs[USER_KEY]   = session.username
            prefs[PASS_KEY]   = session.appPassword
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    // ── App-lock preference ───────────────────────────────────────────────────

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[APP_LOCK]?.toBooleanStrictOrNull() ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[APP_LOCK] = enabled.toString() }
    }

    // ── Nextcloud Login Flow v2 ───────────────────────────────────────────────

    /**
     * Step 1: POST to /index.php/login/v2 on the Nextcloud server.
     * Returns the login URL (open in browser) and the poll token.
     */
    suspend fun initiateLoginFlow(serverUrl: String): Result<LoginFlowInit> = runCatching {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = serverUrl.trimEnd('/') + "/index.php/login/v2"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            check(response.isSuccessful) { "Server returned ${response.code}" }
            val json = org.json.JSONObject(response.body!!.string())
            val poll = json.getJSONObject("poll")
            val pollEndpoint = rewritePollEndpoint(poll.getString("endpoint"), serverUrl)
            LoginFlowInit(
                poll  = PollEndpoint(
                    token    = poll.getString("token"),
                    endpoint = pollEndpoint,
                ),
                login = json.getString("login"),
            )
        }
    }

    /**
     * The poll endpoint returned by the server may point to an internal hostname/IP
     * that differs from the address the user typed (e.g. Tailscale IPs, internal
     * hostnames behind a reverse proxy, or custom subdomain setups). Rewrite the
     * poll endpoint's scheme+host+port to match the user-typed server URL, keeping
     * the path/query from the server response intact.
     */
    private fun rewritePollEndpoint(pollEndpoint: String, userTypedServerUrl: String): String {
        return try {
            val pollUrl = pollEndpoint.toHttpUrl()
            val userUrl = userTypedServerUrl.trimEnd('/').toHttpUrl()
            pollUrl.newBuilder()
                .scheme(userUrl.scheme)
                .host(userUrl.host)
                .port(userUrl.port)
                .build()
                .toString()
        } catch (e: Exception) {
            pollEndpoint
        }
    }

    suspend fun pollLoginFlow(poll: PollEndpoint): Result<LoginCredentials> = runCatching {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = "token=${poll.token}".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url(poll.endpoint)
                .post(body)
                .build()
            val response = httpClient.newCall(request).execute()
            check(response.isSuccessful) { "POLL_NOT_READY:${response.code}" }
            val json = org.json.JSONObject(response.body!!.string())
            LoginCredentials(
                server      = json.getString("server"),
                loginName   = json.getString("loginName"),
                appPassword = json.getString("appPassword"),
            )
        }
    }
}
