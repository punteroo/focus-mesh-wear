package com.focusmesh.wear.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Receives exact wakeup alarms from AlarmManager when a timer phase completes while the watch CPU is asleep.
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_ALARM_EXPIRED
        }
        context.startForegroundService(serviceIntent)
    }
}
