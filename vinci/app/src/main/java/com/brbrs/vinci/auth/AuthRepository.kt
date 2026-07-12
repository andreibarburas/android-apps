package com.brbrs.vinci.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "vinci_auth")

data class LoginFlowInit(
    val poll: PollEndpoint,
    val login: String,
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

data class VinciSession(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val vinciFolder: String = "Vinci",
    val addressBookUrl: String = "",
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
    private val SERVER_KEY       = stringPreferencesKey("server_url")
    private val USER_KEY         = stringPreferencesKey("username")
    private val PASS_KEY         = stringPreferencesKey("app_password")
    private val APP_LOCK_KEY     = stringPreferencesKey("app_lock_enabled")
    private val VINCI_FOLDER_KEY    = stringPreferencesKey("vinci_folder")
    private val ADDRESS_BOOK_KEY    = stringPreferencesKey("address_book_url")

    val session: Flow<VinciSession?> = context.authDataStore.data.map { prefs ->
        val server = prefs[SERVER_KEY] ?: return@map null
        val user   = prefs[USER_KEY]   ?: return@map null
        val pass   = prefs[PASS_KEY]   ?: return@map null
        val folder      = prefs[VINCI_FOLDER_KEY] ?: "Vinci"
        val addressBook = prefs[ADDRESS_BOOK_KEY] ?: ""
        VinciSession(server, user, pass, folder, addressBook)
    }

    suspend fun isLoggedIn(): Boolean = session.first() != null

    suspend fun saveSession(session: VinciSession) {
        context.authDataStore.edit { prefs ->
            prefs[SERVER_KEY]       = session.serverUrl
            prefs[USER_KEY]         = session.username
            prefs[PASS_KEY]         = session.appPassword
            prefs[VINCI_FOLDER_KEY]  = session.vinciFolder
            prefs[ADDRESS_BOOK_KEY]  = session.addressBookUrl
        }
    }

    suspend fun saveAddressBookUrl(url: String) {
        context.authDataStore.edit { prefs -> prefs[ADDRESS_BOOK_KEY] = url }
    }

    suspend fun saveVinciFolder(folder: String) {
        context.authDataStore.edit { prefs -> prefs[VINCI_FOLDER_KEY] = folder }
    }

    suspend fun clearSession() {
        context.authDataStore.edit { it.clear() }
    }

    val appLockEnabled: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[APP_LOCK_KEY]?.toBooleanStrictOrNull() ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.authDataStore.edit { prefs -> prefs[APP_LOCK_KEY] = enabled.toString() }
    }

    private val RESTORE_DONE_KEY = stringPreferencesKey("restore_done")
    private val ONBOARDING_DONE_KEY = stringPreferencesKey("onboarding_done")

    val restoreDone: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[RESTORE_DONE_KEY]?.toBooleanStrictOrNull() ?: false
    }

    suspend fun setRestoreDone() {
        context.authDataStore.edit { prefs -> prefs[RESTORE_DONE_KEY] = "true" }
    }

    val onboardingDone: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[ONBOARDING_DONE_KEY]?.toBooleanStrictOrNull() ?: false
    }

    suspend fun setOnboardingDone() {
        context.authDataStore.edit { prefs -> prefs[ONBOARDING_DONE_KEY] = "true" }
    }

    // -- Nextcloud Login Flow v2

    suspend fun initiateLoginFlow(serverUrl: String): Result<LoginFlowInit> = runCatching {
        withContext(Dispatchers.IO) {
            val url = serverUrl.trimEnd('/') + "/index.php/login/v2"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            check(response.isSuccessful) { "Server returned ${response.code}" }
            val json = org.json.JSONObject(response.body!!.string())
            val poll = json.getJSONObject("poll")
            LoginFlowInit(
                poll  = PollEndpoint(
                    token    = poll.getString("token"),
                    endpoint = poll.getString("endpoint"),
                ),
                login = json.getString("login"),
            )
        }
    }

    suspend fun pollLoginFlow(poll: PollEndpoint): Result<LoginCredentials> = runCatching {
        withContext(Dispatchers.IO) {
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
