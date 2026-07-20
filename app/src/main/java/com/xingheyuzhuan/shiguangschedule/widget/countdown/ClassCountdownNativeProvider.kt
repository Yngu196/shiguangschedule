package com.xingheyuzhuan.shiguangschedule.widget.countdown

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.xingheyuzhuan.shiguangschedule.widget.WorkManagerHelper
import com.xingheyuzhuan.shiguangschedule.widget.updateAllWidgets
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.LocalTime

class ClassCountdownNativeProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_MINUTE_TICK = "com.xingheyuzhuan.shiguangschedule.widget.COUNTDOWN_MINUTE_TICK"
        private const val ALARM_REQUEST_CODE = 2001
    }

    private val scope = MainScope()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        scope.launch {
            updateAllWidgets(context)
            scheduleNextMinuteAlarm(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MINUTE_TICK) {
            scope.launch {
                updateAllWidgets(context)
                scheduleNextMinuteAlarm(context)
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkManagerHelper.schedulePeriodicWork(context)
        scope.launch {
            updateAllWidgets(context)
            scheduleNextMinuteAlarm(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManagerHelper.cancelAllWork(context)
        cancelAlarm(context)
    }

    private fun scheduleNextMinuteAlarm(context: Context) {
        val now = LocalTime.now()
        val secondsToNextMinute = 60 - now.second
        val triggerAtMillis = System.currentTimeMillis() + secondsToNextMinute * 1000L + 500L

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, ClassCountdownNativeProvider::class.java).apply {
            action = ACTION_MINUTE_TICK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)

        try {
            alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (e: SecurityException) {
            Log.w("ClassCountdownWidget", "无法设置精确闹钟，降级为非精确", e)
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context) {
        val intent = Intent(context, ClassCountdownNativeProvider::class.java).apply {
            action = ACTION_MINUTE_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}