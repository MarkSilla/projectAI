package com.marksilla.auraagent

import android.content.Context
import android.content.Intent

object AuraServiceState {
    const val ACTION_STATUS = "com.marksilla.auraagent.action.AURA_STATUS"
    const val EXTRA_ACTIVE = "extra_active"
    const val EXTRA_STATUS = "extra_status"
    const val EXTRA_LISTENING = "extra_listening"
    const val EXTRA_MODE = "extra_mode"

    private const val PREFS_NAME = "aura_service_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_STATUS = "status"
    private const val KEY_LISTENING = "listening"
    private const val DEFAULT_STATUS = "Ready"

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(
            KEY_ACTIVE,
            false
        )

    fun lastStatus(context: Context): String =
        prefs(context).getString(
            KEY_STATUS,
            DEFAULT_STATUS
        ) ?: DEFAULT_STATUS

    fun isListening(context: Context): Boolean =
        prefs(context).getBoolean(
            KEY_LISTENING,
            false
        )

    fun publish(
        context: Context,
        active: Boolean,
        status: String,
        listening: Boolean,
        mode: String
    ) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ACTIVE, active)
            .putString(KEY_STATUS, status)
            .putBoolean(KEY_LISTENING, listening)
            .apply()

        context.sendBroadcast(
            Intent(ACTION_STATUS).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ACTIVE, active)
                putExtra(EXTRA_STATUS, status)
                putExtra(EXTRA_LISTENING, listening)
                putExtra(EXTRA_MODE, mode)
            }
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
}
