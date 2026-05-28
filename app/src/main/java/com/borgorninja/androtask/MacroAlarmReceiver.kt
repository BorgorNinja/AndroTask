package com.borgorninja.androtask

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borgorninja.androtask.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MacroAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> rescheduleAll(context)
            ACTION_TRIGGER               -> {
                val macroId = intent.getLongExtra(EXTRA_MACRO_ID, -1L)
                if (macroId != -1L) executeMacro(context, macroId)
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun executeMacro(context: Context, macroId: Long) {
        val svc = MacroAccessibilityService.instance ?: return
        val db  = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            val mws = db.macroDao().getMacroWithSteps(macroId) ?: return@launch
            withContext(Dispatchers.Main) {
                svc.playSteps(mws.steps, mws.macro.loopCount, mws.macro.loopDelay)
            }
        }
    }

    private fun rescheduleAll(context: Context) {
        val db = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            db.macroDao().getEnabledTriggers().forEach { t ->
                schedule(context, t.id, t.macroId, t.hourOfDay, t.minute)
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER   = "com.borgorninja.androtask.MACRO_TRIGGER"
        const val EXTRA_MACRO_ID   = "macroId"
        const val EXTRA_TRIGGER_ID = "triggerId"

        fun schedule(context: Context, triggerId: Long, macroId: Long, hour: Int, minute: Int) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context, triggerId, macroId)

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis())
                    add(Calendar.DAY_OF_YEAR, 1)
            }

            try {
                am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }

        fun cancel(context: Context, triggerId: Long, macroId: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(context, triggerId, macroId))
        }

        private fun pendingIntent(context: Context, triggerId: Long, macroId: Long): PendingIntent {
            val intent = Intent(context, MacroAlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER
                putExtra(EXTRA_MACRO_ID, macroId)
                putExtra(EXTRA_TRIGGER_ID, triggerId)
            }
            return PendingIntent.getBroadcast(
                context, triggerId.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
