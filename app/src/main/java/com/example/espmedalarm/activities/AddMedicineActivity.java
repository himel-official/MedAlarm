package com.example.espmedalarm.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.MedicineRepository;
import com.example.espmedalarm.entity.Medicine;
import com.example.espmedalarm.utils.AlarmScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * Add/Edit Medicine screen. Same AlarmScheduler calls as before - only the
 * storage backend changed from the local Room database to Firestore
 * (via MedicineRepository), so medicines are backed up per-account and
 * survive app uninstall/reinstall.
 */
public class AddMedicineActivity extends AppCompatActivity {

    private boolean isEditMode = false;
    private String medicineId = null;
    private long startDate;

    private final MedicineRepository medicineRepository = new MedicineRepository();

    private TextInputEditText etMedicine, etDuration;
    private MaterialButton btnAddTime, btnSave, btnStartDate;
    private ChipGroup chipGroupTimes;
    private MaterialButtonToggleGroup boxToggleGroup;
    private MaterialButton btnBox1, btnBox2, btnBox3, btnBox4;

    private int selectedBox = 1;

    private final ArrayList<String> selectedTimes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_medicine);

        etMedicine = findViewById(R.id.etMedicine);
        etDuration = findViewById(R.id.etDuration);

        chipGroupTimes = findViewById(R.id.chipGroupTimes);
        btnAddTime = findViewById(R.id.btnAddTime);
        btnSave = findViewById(R.id.btnSave);
        btnStartDate = findViewById(R.id.btnStartDate);
        boxToggleGroup = findViewById(R.id.boxToggleGroup);
        btnBox1 = findViewById(R.id.btnBox1);
        btnBox2 = findViewById(R.id.btnBox2);
        btnBox3 = findViewById(R.id.btnBox3);
        btnBox4 = findViewById(R.id.btnBox4);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        boxToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.btnBox1) selectedBox = 1;
            else if (checkedId == R.id.btnBox2) selectedBox = 2;
            else if (checkedId == R.id.btnBox3) selectedBox = 3;
            else if (checkedId == R.id.btnBox4) selectedBox = 4;
        });

        // Edit Mode
        if (getIntent().hasExtra("id")) {

            isEditMode = true;
            medicineId = getIntent().getStringExtra("id");
            startDate = getIntent().getLongExtra("startDate", System.currentTimeMillis());

            ((TextView) findViewById(R.id.txtScreenTitle)).setText("Edit Medicine");

            etMedicine.setText(getIntent().getStringExtra("name"));
            etDuration.setText(String.valueOf(getIntent().getIntExtra("duration", 0)));

            selectedBox = getIntent().getIntExtra("boxNumber", 1);
            selectBox(selectedBox);

            ArrayList<String> times = getIntent().getStringArrayListExtra("times");

            if (times != null && !times.isEmpty()) {
                for (String t : times) addTimeChip(t);
            } else {
                addTimeChip("08:00 AM");
            }

            btnSave.setText("Update Medicine");

        } else {

            startDate = System.currentTimeMillis();
            selectBox(1);
            addTimeChip("08:00 AM");
        }

        updateStartDateLabel();

        btnStartDate.setOnClickListener(v -> {

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(startDate);

            new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar picked = Calendar.getInstance();
                        picked.set(year, month, dayOfMonth,
                                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                        startDate = picked.getTimeInMillis();
                        updateStartDateLabel();
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        btnAddTime.setOnClickListener(v -> {

            TimePickerDialog dialog = new TimePickerDialog(
                    this,
                    (view, hour, minute) -> {

                        Calendar calendar = Calendar.getInstance();
                        calendar.set(Calendar.HOUR_OF_DAY, hour);
                        calendar.set(Calendar.MINUTE, minute);

                        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                        addTimeChip(sdf.format(calendar.getTime()));
                    },
                    8, 0, false
            );

            dialog.show();
        });

        btnSave.setOnClickListener(v -> {

            String medicineName = etMedicine.getText() != null ? etMedicine.getText().toString().trim() : "";
            String durationText = etDuration.getText() != null ? etDuration.getText().toString().trim() : "";

            if (medicineName.isEmpty()) {
                etMedicine.setError("Enter medicine name");
                return;
            }

            if (selectedTimes.isEmpty()) {
                Toast.makeText(this, "Add at least one reminder time", Toast.LENGTH_SHORT).show();
                return;
            }

            if (durationText.isEmpty()) {
                etDuration.setError("Enter duration");
                return;
            }

            Medicine medicine = new Medicine(
                    medicineName,
                    new ArrayList<>(selectedTimes),
                    Integer.parseInt(durationText),
                    selectedBox
            );

            if (isEditMode) {
                medicine.id = medicineId;
            }
            medicine.startDate = startDate;

            btnSave.setEnabled(false);

            MedicineRepository.OpCallback saveCallback = new MedicineRepository.OpCallback() {
                @Override
                public void onSuccess() {
                    for (String time : medicine.times) {
                        AlarmScheduler.scheduleAlarm(
                                AddMedicineActivity.this,
                                medicine.name,
                                time,
                                medicine.boxNumber
                        );
                    }

                    Toast.makeText(
                            AddMedicineActivity.this,
                            isEditMode ? "Medicine Updated!" : "Medicine Saved!",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                }

                @Override
                public void onError(String message) {
                    btnSave.setEnabled(true);
                    Toast.makeText(
                            AddMedicineActivity.this,
                            "Could not save: " + message,
                            Toast.LENGTH_LONG
                    ).show();
                }
            };

            if (isEditMode) {
                medicineRepository.update(medicine, saveCallback);
            } else {
                medicineRepository.insert(medicine, saveCallback);
            }
        });
    }

    private void addTimeChip(String time) {

        Chip chip = new Chip(this);
        chip.setText(time);
        chip.setCloseIconVisible(true);
        chip.setCheckable(false);
        chip.setChipBackgroundColorResource(R.color.primary_light);
        chip.setTextColor(getResources().getColor(R.color.primary));

        chip.setOnCloseIconClickListener(v -> {
            selectedTimes.remove(time);
            chipGroupTimes.removeView(chip);
        });

        chipGroupTimes.addView(chip);
        selectedTimes.add(time);
    }

    private void selectBox(int box) {
        selectedBox = box;

        int checkedId;
        switch (box) {
            case 2: checkedId = R.id.btnBox2; break;
            case 3: checkedId = R.id.btnBox3; break;
            case 4: checkedId = R.id.btnBox4; break;
            default: checkedId = R.id.btnBox1;
        }
        boxToggleGroup.check(checkedId);
    }

    private void updateStartDateLabel() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        btnStartDate.setText(sdf.format(new java.util.Date(startDate)));
    }
}
