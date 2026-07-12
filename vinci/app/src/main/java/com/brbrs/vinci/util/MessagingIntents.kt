package com.brbrs.vinci.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Helpers for launching chats in third-party messaging apps directly from a
 * phone number, mirroring the behaviour of the stock Contacts app (tap to
 * open WhatsApp / Signal conversation with that contact).
 */

private const val PACKAGE_WHATSAPP = "com.whatsapp"
private const val PACKAGE_SIGNAL = "org.thoughtcrime.securesms"

fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }
}

fun isWhatsAppInstalled(context: Context): Boolean = isPackageInstalled(context, PACKAGE_WHATSAPP)
fun isSignalInstalled(context: Context): Boolean = isPackageInstalled(context, PACKAGE_SIGNAL)

/**
 * Normalizes a phone number to E.164-ish digits for use with WhatsApp/Signal
 * deep links. If the number already starts with '+' (or already starts with
 * the given country code), it's used as-is. Otherwise, any leading trunk
 * '0' is stripped and the default country code is prepended.
 *
 * [defaultCountryCode] is digits only, e.g. "31" for the Netherlands.
 */
fun normalizeForChat(phoneNumber: String, defaultCountryCode: String): String {
    val trimmed = phoneNumber.trim()
    if (trimmed.startsWith("+")) {
        return trimmed.replace(Regex("[^0-9+]"), "")
    }
    val digits = trimmed.replace(Regex("[^0-9]"), "")
    if (digits.isBlank()) return ""
    if (defaultCountryCode.isBlank()) return digits
    if (digits.startsWith(defaultCountryCode)) return digits
    val withoutTrunkZero = digits.trimStart('0')
    return "$defaultCountryCode$withoutTrunkZero"
}

/**
 * Opens a WhatsApp chat with the given phone number. Numbers missing a
 * country code are normalized using [defaultCountryCode] (digits only,
 * e.g. "31"), which can be left blank if unknown.
 */
fun openWhatsAppChat(context: Context, phoneNumber: String, defaultCountryCode: String = ""): Boolean {
    val digits = normalizeForChat(phoneNumber, defaultCountryCode)
    if (digits.isBlank()) return false
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            setPackage(PACKAGE_WHATSAPP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * Opens a Signal chat with the given phone number. Numbers missing a
 * country code are normalized using [defaultCountryCode] (digits only,
 * e.g. "31"), which can be left blank if unknown.
 */
fun openSignalChat(context: Context, phoneNumber: String, defaultCountryCode: String = ""): Boolean {
    val digits = normalizeForChat(phoneNumber, defaultCountryCode)
    if (digits.isBlank()) return false
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$digits")).apply {
            setPackage(PACKAGE_SIGNAL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
