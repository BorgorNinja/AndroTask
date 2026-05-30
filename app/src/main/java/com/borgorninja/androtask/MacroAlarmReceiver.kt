package com.borgorninja.androtask

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borgorninja.androtask.data.AppDatabase
import com.borgorninja.androtask.data.ScheduledTrigger
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

    private fun executeMacro(context: Context, macroId: Long) {
        val svc = MacroAccessibilityService.instance ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val mws = AppDatabase.getDatabase(context).macroDao().getMacroWithSteps(macroId) ?: return@launch
            withContext(Dispatchers.Main) {
                svc.playSteps(mws.steps, mws.macro.loopCount, mws.macro.loopDelay)
            }
        }
    }

    private fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).macroDao().getEnabledTriggers().forEach { t ->
                schedule(context, t.id, t.macroId, t.hourOfDay, t.minute, t.repeatDays)
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER   = "com.borgorninja.androtask.MACRO_TRIGGER"
        const val EXTRA_MACRO_ID   = "macroId"
        const val EXTRA_TRIGGER_ID = "triggerId"

        /**
         * Fix #3: Schedule one alarm per selected weekday instead of blindly
         * using setRepeating(INTERVAL_DAY).
         *
         * repeatDays uses 1=Mon…7=Sun (matches ScheduleDialog index+1).
         * Calendar uses 1=Sun, 2=Mon … 7=Sat, so we convert:
         *   stored 1(Mon) → Calendar.MONDAY(2), …, stored 7(Sun) → Calendar.SUNDAY(1)
         */
        private fun storedDayToCalendar(d: Int): Int = when (d) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        fun schedule(
            context: Context,
            triggerId: Long,
            macroId: Long,
            hour: Int,
            minute: Int,
            repeatDays: String = "1,2,3,4,5,6,7"
        ) {
            val am    = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val days  = repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
            val effective = if (days.isEmpty()) listOf(1,2,3,4,5,6,7) else days

            effective.forEach { storedDay ->
                val calDay = storedDayToCalendar(storedDay)
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, calDay)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
                }
                // Use triggerId * 10 + storedDay to keep per-day PendingIntents distinct
                val pi = pendingIntent(context, triggerId * 10 + storedDay, macroId)
                try {
                    am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY * 7, pi)
                } catch (_: SecurityException) {
                    am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            }
        }

        fun cancel(context: Context, triggerId: Long, macroId: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Cancel all 7 possible per-day intents
            for (d in 1..7) am.cancel(pendingIntent(context, triggerId * 10 + d, macroId))
        }

        private fun pendingIntent(context: Context, requestCode: Long, macroId: Long): PendingIntent {
            val intent = Intent(context, MacroAlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER
                putExtra(EXTRA_MACRO_ID, macroId)
            }
            return PendingIntent.getBroadcast(
                context, requestCode.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
