package com.example.espmedalarm.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.core.app.NotificationCompat;

import com.example.espmedalarm.R;
import com.example.espmedalarm.activities.AlarmActivity;

public class AlarmService extends Service {

    private static final String CHANNEL_ID = "medicine_alarm_channel";
    private static final long AUTO_STOP_MILLIS = 60 * 1000; // ring for 1 minute

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private Handler autoStopHandler;
    private Runnable autoStopRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String medicineName = intent.getStringExtra("medicineName");

        if (medicineName == null)
            medicineName = "Medicine";

        int boxNumber = intent.getIntExtra("boxNumber", 1);

        // Wake the screen
        PowerManager pm =
                (PowerManager) getSystemService(POWER_SERVICE);

        if (pm != null) {

            wakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "ESPMEDALARM:AlarmWakeLock"
            );

            wakeLock.acquire(10000);
        }

        // Open AlarmActivity
        Intent activityIntent =
                new Intent(this, AlarmActivity.class);

        activityIntent.putExtra(
                "medicineName",
                medicineName
        );

        activityIntent.putExtra(
                "boxNumber",
                boxNumber
        );

        activityIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        activityIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Medicine Reminder")
                        .setContentText(
                                "Time to take " + medicineName
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_MAX
                        )
                        .setCategory(
                                NotificationCompat.CATEGORY_ALARM
                        )
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                        .setFullScreenIntent(
                                pendingIntent,
                                true
                        )
                        .build();

        startForeground(1001, notification);

        startActivity(activityIntent);

        // Play alarm sound
        mediaPlayer =
                MediaPlayer.create(this, R.raw.alarm);

        if (mediaPlayer != null) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                AudioAttributes.Builder audioAttrsBuilder =
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);

                // Force the alarm to be audible even if the device is on
                // silent / vibrate-only / a Do Not Disturb or focus mode.
                audioAttrsBuilder.setFlags(
                        AudioAttributes.FLAG_AUDIBILITY_ENFORCED
                );

                mediaPlayer.setAudioAttributes(audioAttrsBuilder.build());
            }

            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }

        // Start vibration (every kind: repeating pattern, max amplitude,
        // resolved through the modern VibratorManager on Android 12+ so it
        // keeps firing regardless of ringer/DND/focus mode).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            VibratorManager vibratorManager =
                    (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);

            vibrator = (vibratorManager != null)
                    ? vibratorManager.getDefaultVibrator()
                    : null;

        } else {

            vibrator =
                    (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {

            long[] pattern = {
                    0,
                    1000,
                    500,
                    1000,
                    500,
                    1000
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                int[] amplitudes = {
                        0,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                        0,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                        0,
                        VibrationEffect.DEFAULT_AMPLITUDE
                };

                VibrationEffect effect = vibrator.hasAmplitudeControl()
                        ? VibrationEffect.createWaveform(pattern, amplitudes, 0)
                        : VibrationEffect.createWaveform(pattern, 0);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {

                    AudioAttributes vibrationAttrs =
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .build();

                    vibrator.vibrate(effect, vibrationAttrs);

                } else {

                    vibrator.vibrate(effect);
                }

            } else {

                vibrator.vibrate(
                        pattern,
                        0
                );
            }
        }

        // Stop the alarm automatically after 1 minute of ringing
        autoStopHandler = new Handler(Looper.getMainLooper());
        autoStopRunnable = this::stopSelf;
        autoStopHandler.postDelayed(autoStopRunnable, AUTO_STOP_MILLIS);

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Medicine Alarm",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.enableVibration(true);

            // Let this channel ring/vibrate through Do Not Disturb and
            // Focus modes (requires the user to have granted DND access,
            // requested from MainActivity).
            channel.setBypassDnd(true);

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null)
                manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {

        if (autoStopHandler != null && autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }

        if (mediaPlayer != null) {

            if (mediaPlayer.isPlaying())
                mediaPlayer.stop();

            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (vibrator != null)
            vibrator.cancel();

        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();

        stopForeground(STOP_FOREGROUND_REMOVE);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}