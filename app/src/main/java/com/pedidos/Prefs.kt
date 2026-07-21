package com.pedidos

import android.content.Context

object Prefs {
    private const val FILE = "pedidos_prefs"
    private const val KEY_GROUP = "grupo_whatsapp"
    private const val KEY_SIEMPRE = "usar_siempre"

    fun getGroup(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_GROUP, "") ?: ""
    }

    fun setGroup(context: Context, group: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_GROUP, group).apply()
    }

    fun isSiempre(context: Context): Boolean {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_SIEMPRE, false)
    }

    fun setSiempre(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SIEMPRE, value).apply()
    }
}
