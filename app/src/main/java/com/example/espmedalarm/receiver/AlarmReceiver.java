package com.example.espmedalarm.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.example.espmedalarm.service.AlarmService;
import com.example.espmedalarm.utils.AlarmScheduler;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {

        Log.d(
                "ESPMEDALARM",
                "AlarmReceiver fired!"
        );

        String medicineName =
                intent.getStringExtra("medicineName");

        String time =
                intent.getStringExtra("time");

        int boxNumber =
                intent.getIntExtra("boxNumber", 1);

        Toast.makeText(
                context,
                "Alarm fired: " + medicineName,
                Toast.LENGTH_LONG
        ).show();

        Intent serviceIntent =
                new Intent(
                        context,
                        AlarmService.class
                );

        serviceIntent.putExtra(
                "medicineName",
                medicineName
        );

        serviceIntent.putExtra(
                "time",
                time
        );

        serviceIntent.putExtra(
                "boxNumber",
                boxNumber
        );

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            context.startForegroundService(
                    serviceIntent
            );

        } else {

            context.startService(
                    serviceIntent
            );
        }

        // Schedule the same alarm for tomorrow
        if (medicineName != null && time != null) {

            AlarmScheduler.scheduleAlarm(
                    context,
                    medicineName,
                    time,
                    boxNumber
            );
        }
    }
}