package com.brbrs.runa.ui.navigation

sealed class RunaRoute(val route: String) {
    object AppLock       : RunaRoute("app_lock")
    object StorageChoice : RunaRoute("storage_choice")
    object Login         : RunaRoute("login")
    object Home          : RunaRoute("home")
    object Settings      : RunaRoute("settings")
    object LoginFromSettings : RunaRoute("login_from_settings")

    object EntryDetail : RunaRoute("entry_detail/{$ENTRY_ID_ARG}") {
        fun createRoute(entryId: String) = "entry_detail/$entryId"
    }
    object EditEntry : RunaRoute("edit_entry/{$ENTRY_ID_ARG}") {
        fun createRoute(entryId: String) = "edit_entry/$entryId"
    }

    /**
     * Write screen pre-loaded with one or more shared image URIs.
     * URIs are passed as a pipe-separated encoded string, e.g.
     * "content%3A%2F%2F...|content%3A%2F%2F..."
     */
    object WriteShared : RunaRoute("write_shared/{$SHARED_URIS_ARG}") {
        fun createRoute(uris: List<String>): String {
            val encoded = android.net.Uri.encode(uris.joinToString("|"))
            return "write_shared/$encoded"
        }
    }

    companion object {
        const val ENTRY_ID_ARG     = "entryId"
        const val SHARED_URIS_ARG  = "sharedUris"
    }
}
