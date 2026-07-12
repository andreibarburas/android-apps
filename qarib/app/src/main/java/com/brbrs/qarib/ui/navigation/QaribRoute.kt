package com.brbrs.qarib.ui.navigation

/**
 * Top-level navigation destinations.
 */
sealed class QaribRoute(val route: String) {
    object AppLock : QaribRoute("app_lock")
    object Login : QaribRoute("login")
    object Permissions : QaribRoute("permissions")
    object Places : QaribRoute("places")
    object AddPlace : QaribRoute("add_place?$INITIAL_QUERY_ARG={$INITIAL_QUERY_ARG}") {
        /**
         * [initialQuery] is the user's search text from the Places screen
         * search bar, used to pre-fill and auto-run the place search on
         * the Add Place screen. Pass an empty string for the normal
         * "add a new place" flow (e.g. from the FAB).
         *
         * Uses [android.net.Uri.encode] (which encodes spaces as %20)
         * rather than [java.net.URLEncoder] (which encodes spaces as +),
         * since Navigation Compose decodes query-style route arguments
         * as URI components, not form-encoded values.
         */
        fun createRoute(initialQuery: String = ""): String {
            val encoded = android.net.Uri.encode(initialQuery)
            return "add_place?$INITIAL_QUERY_ARG=$encoded"
        }
    }
    object EditPlace : QaribRoute("edit_place/{$PLACE_ID_ARG}") {
        fun createRoute(placeId: String) = "edit_place/$placeId"
    }
    object Settings : QaribRoute("settings")

    companion object {
        const val PLACE_ID_ARG = "placeId"
        const val INITIAL_QUERY_ARG = "initialQuery"
    }
}
