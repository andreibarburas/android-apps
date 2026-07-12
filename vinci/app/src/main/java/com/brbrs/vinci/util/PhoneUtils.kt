package com.brbrs.vinci.util

/**
 * Normalizes a phone number for matching unknown-number interaction logs
 * against incoming calls and contacts. Strips everything except digits and
 * a leading '+', then strips leading zeros (so "06..." and "316..." style
 * national/international variants of the same number match).
 *
 * Must stay in sync with ContactsRepository's normalization so that
 * unknown-number logs can be re-linked to contacts by phone number.
 */
fun normalizePhone(number: String): String {
    if (number.isBlank()) return ""
    return number.replace(Regex("[^0-9+]"), "").trimStart('0')
}
