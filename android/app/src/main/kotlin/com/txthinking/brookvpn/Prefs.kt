package com.txthinking.brookvpn

import android.content.Context
import android.content.SharedPreferences

/** Small persistence layer over SharedPreferences. */
object Prefs {
    private const val FILE = "brook"
    private const val KEY_LINK = "link"
    private const val KEY_WHITELIST = "whitelist"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun link(context: Context): String =
        prefs(context).getString(KEY_LINK, "").orEmpty()

    fun saveLink(context: Context, link: String) =
        prefs(context).edit().putString(KEY_LINK, link).apply()

    /** Package names of apps allowed to use the VPN. */
    fun whitelist(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WHITELIST, emptySet())?.toSet() ?: emptySet()

    fun saveWhitelist(context: Context, packages: Set<String>) =
        prefs(context).edit().putStringSet(KEY_WHITELIST, packages).apply()
}
