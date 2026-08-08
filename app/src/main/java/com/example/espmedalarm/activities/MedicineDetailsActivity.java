package com.example.espmedalarm.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.MedicineRepository;
import com.example.espmedalarm.entity.Medicine;
import com.example.espmedalarm.fragments.HomeFragment;
import com.example.espmedalarm.utils.AlarmScheduler;
import com.example.espmedalarm.utils.MedicineStatusUtils;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class MedicineDetailsActivity extends AppCompatActivity {

    private final MedicineRepository medicineRepository = new MedicineRepository();

    private String medicineId = null;
    private Medicine medicine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_details);

        medicineId = getIntent().getStringExtra("id");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        MaterialButton btnEdit = findViewById(R.id.btnEdit);
        MaterialButton btnDelete = findViewById(R.id.btnDelete);

        btnEdit.setOnClickListener(v -> {
            if (medicine == null) return;

            Intent intent = new Intent(this, AddMedicineActivity.class);
            intent.putExtra("id", medicine.id);
            intent.putExtra("name", medicine.name);
            intent.putStringArrayListExtra("times", new java.util.ArrayList<>(medicine.times));
            intent.putExtra("duration", medicine.duration);
            intent.putExtra("boxNumber", medicine.boxNumber);
            intent.putExtra("startDate", medicine.startDate);
            startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> {
            if (medicine == null) return;

            new AlertDialog.Builder(this)
                    .setTitle("Delete Medicine")
                    .setMessage("Delete \"" + medicine.name + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> {

                        for (String time : medicine.times) {
                            AlarmScheduler.cancelAlarm(this, medicine.name, time);
                        }

                        medicineRepository.delete(medicine, new MedicineRepository.OpCallback() {
                            @Override
                            public void onSuccess() {
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                android.widget.Toast.makeText(MedicineDetailsActivity.this,
                                        "Could not delete: " + message,
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMedicine();
    }

    private void loadMedicine() {
        if (medicineId == null) {
            finish();
            return;
        }

        medicineRepository.getMedicineById(medicineId, new MedicineRepository.MedicineCallback() {
            @Override
            public void onSuccess(Medicine result) {
                medicine = result;
                bindMedicine();
            }

            @Override
            public void onError(String message) {
                finish();
            }
        });
    }

    private void bindMedicine() {
        ((TextView) findViewById(R.id.txtName)).setText(medicine.name);

        TextView txtStatus = findViewById(R.id.txtStatus);
        boolean active = MedicineStatusUtils.isActive(medicine);
        boolean expiringSoon = MedicineStatusUtils.isExpiringSoon(medicine);

        if (!active) {
            txtStatus.setText("Expired");
            txtStatus.setBackgroundResource(R.drawable.bg_status_expired);
            txtStatus.setTextColor(getResources().getColor(R.color.danger_red));
        } else if (expiringSoon) {
            txtStatus.setText("Ending soon");
            txtStatus.setBackgroundResource(R.drawable.bg_status_warning);
            txtStatus.setTextColor(getResources().getColor(R.color.warning_orange));
        } else {
            txtStatus.setText("Active");
            txtStatus.setBackgroundResource(R.drawable.bg_status_active);
            txtStatus.setTextColor(getResources().getColor(R.color.accent_green));
        }

        bindRow(R.id.rowBox, "Assigned Box", "Box " + medicine.boxNumber);
        bindRow(R.id.rowDuration, "Duration", medicine.duration + " day(s)");
        bindRow(R.id.rowRemaining, "Remaining", MedicineStatusUtils.getDurationLabel(medicine));
        bindRow(R.id.rowTimes, "Reminder Times", String.join(", ", medicine.times));

        SharedPreferences prefs = getSharedPreferences(HomeFragment.PREFS_NAME, MODE_PRIVATE);
        long lastSync = prefs.getLong(HomeFragment.KEY_LAST_SYNC, -1L);
        String lastSyncLabel;
        if (lastSync <= 0) {
            lastSyncLabel = "Never synced";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault());
            lastSyncLabel = sdf.format(new java.util.Date(lastSync));
        }
        bindRow(R.id.rowLastSync, "Last Sync Time", lastSyncLabel);
    }

    private void bindRow(int rowId, String label, String value) {
        android.view.View row = findViewById(rowId);
        ((TextView) row.findViewById(R.id.txtLabel)).setText(label);
        ((TextView) row.findViewById(R.id.txtValue)).setText(value);
    }
}
