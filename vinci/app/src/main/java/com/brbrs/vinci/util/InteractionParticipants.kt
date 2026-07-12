package com.brbrs.vinci.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * An additional person tied to an interaction (e.g. a group call or joint meeting),
 * beyond the primary contact the interaction is logged against.
 *
 * [uid] is the person's CardDAV uid when they were picked from existing contacts,
 * or blank when they were entered as a free-text name for someone not in contacts.
 */
data class InteractionParticipant(
    val uid: String = "",
    val name: String,
)

/** Serializes a list of participants to the compact JSON stored in [com.brbrs.vinci.data.CallLogEntity.participants]. */
fun serializeParticipants(participants: List<InteractionParticipant>): String {
    val arr = JSONArray()
    participants.forEach { p ->
        arr.put(JSONObject().apply {
            put("uid", p.uid)
            put("name", p.name)
        })
    }
    return arr.toString()
}

/** Parses the JSON stored in [com.brbrs.vinci.data.CallLogEntity.participants] back into a list. Never throws. */
fun parseParticipants(json: String): List<InteractionParticipant> {
    if (json.isBlank() || json == "[]") return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val name = obj.optString("name", "")
            if (name.isBlank()) null else InteractionParticipant(uid = obj.optString("uid", ""), name = name)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
