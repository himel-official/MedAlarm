package com.example.espmedalarm.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.espmedalarm.receiver.AlarmReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AlarmScheduler {

    public static void scheduleAlarm(
            Context context,
            String medicineName,
            String time,
            int boxNumber
    ) {

        try {

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

            Date date = sdf.parse(time);

            if (date == null)
                return;

            Calendar parsed = Calendar.getInstance();
            parsed.setTime(date);

            Calendar alarm = Calendar.getInstance();

            alarm.set(
                    Calendar.HOUR_OF_DAY,
                    parsed.get(Calendar.HOUR_OF_DAY)
            );

            alarm.set(
                    Calendar.MINUTE,
                    parsed.get(Calendar.MINUTE)
            );

            alarm.set(Calendar.SECOND, 0);
            alarm.set(Calendar.MILLISECOND, 0);

            if (alarm.before(Calendar.getInstance())) {
                alarm.add(Calendar.DATE, 1);
            }

            Intent intent =
                    new Intent(
                            context,
                            AlarmReceiver.class
                    );

            intent.putExtra(
                    "medicineName",
                    medicineName
            );

            intent.putExtra(
                    "time",
                    time
            );

            intent.putExtra(
                    "boxNumber",
                    boxNumber
            );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            (medicineName + time).hashCode(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(
                            Context.ALARM_SERVICE
                    );

            if (alarmManager == null)
                return;

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.S) {

                if (!alarmManager.canScheduleExactAlarms()) {

                    Intent permissionIntent =
                            new Intent(
                                    android.provider.Settings
                                            .ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            );

                    permissionIntent.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

                    context.startActivity(
                            permissionIntent
                    );

                    return;
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.getTimeInMillis(),
                    pendingIntent
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Fires the alarm again in 10 minutes (used by the Snooze button).
    // No "time" extra is sent, so AlarmReceiver will NOT reschedule the
    // regular daily alarm off of this one.
    public static void scheduleSnooze(
            Context context,
            String medicineName,
            int boxNumber
    ) {

        try {

            Intent intent =
                    new Intent(
                            context,
                            AlarmReceiver.class
                    );

            intent.putExtra(
                    "medicineName",
                    medicineName
            );

            intent.putExtra(
                    "boxNumber",
                    boxNumber
            );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            (medicineName + "_snooze").hashCode(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(
                            Context.ALARM_SERVICE
                    );

            if (alarmManager == null)
                return;

            long triggerAt =
                    System.currentTimeMillis() + (10 * 60 * 1000);

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.S) {

                if (!alarmManager.canScheduleExactAlarms()) {
                    return;
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Add this method
    public static void cancelAlarm(
            Context context,
            String medicineName,
            String time
    ) {

        Intent intent =
                new Intent(
                        context,
                        AlarmReceiver.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        (medicineName + time).hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(
                        Context.ALARM_SERVICE
                );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }

        pendingIntent.cancel();
    }
}
