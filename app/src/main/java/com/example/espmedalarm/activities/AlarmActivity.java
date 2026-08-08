package com.example.espmedalarm.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.espmedalarm.R;
import com.example.espmedalarm.service.AlarmService;
import com.example.espmedalarm.utils.AlarmScheduler;

public class AlarmActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {

            setShowWhenLocked(true);
            setTurnScreenOn(true);

        } else {

            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        setContentView(R.layout.activity_alarm);

        TextView txtMedicine = findViewById(R.id.txtMedicine);
        TextView txtBox = findViewById(R.id.txtBox);
        Button btnTaken = findViewById(R.id.btnTaken);
        Button btnSnooze = findViewById(R.id.btnSnooze);

        String medicineName = getIntent().getStringExtra("medicineName");
        int boxNumber = getIntent().getIntExtra("boxNumber", 1);

        if (medicineName == null) {
            medicineName = "Medicine";
        }

        final String finalMedicineName = medicineName;

        txtMedicine.setText(medicineName);
        txtBox.setText("📦 Box " + boxNumber);

        btnTaken.setOnClickListener(v -> {
            stopAlarm();
            // Later we will mark the dose as taken here.
            finish();
        });

        btnSnooze.setOnClickListener(v -> {
            AlarmScheduler.scheduleSnooze(
                    this,
                    finalMedicineName,
                    boxNumber
            );
            stopAlarm();
            finish();
        });
    }

    private void stopAlarm() {

        Intent intent = new Intent(this, AlarmService.class);
        stopService(intent);

    }
}