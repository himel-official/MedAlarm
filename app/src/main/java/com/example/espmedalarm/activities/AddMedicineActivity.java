package com.example.espmedalarm.activities;
import com.example.espmedalarm.utils.AlarmScheduler;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.AppDatabase;
import com.example.espmedalarm.entity.Medicine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class AddMedicineActivity extends AppCompatActivity {

    private boolean isEditMode = false;
    private int medicineId = -1;
    private long startDate;

    private EditText etMedicine, etDuration;
    private Button btnAddTime, btnSave;
    private LinearLayout layoutTimes;
    private Button btnBox1, btnBox2, btnBox3, btnBox4;

    private int selectedBox = 1;

    private final ArrayList<String> selectedTimes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_medicine);

        etMedicine = findViewById(R.id.etMedicine);
        etDuration = findViewById(R.id.etDuration);

        layoutTimes = findViewById(R.id.layoutTimes);
        btnAddTime = findViewById(R.id.btnAddTime);
        btnSave = findViewById(R.id.btnSave);
        btnBox1 = findViewById(R.id.btnBox1);
        btnBox2 = findViewById(R.id.btnBox2);
        btnBox3 = findViewById(R.id.btnBox3);
        btnBox4 = findViewById(R.id.btnBox4);

        selectBox(1);

        btnBox1.setOnClickListener(v -> selectBox(1));
        btnBox2.setOnClickListener(v -> selectBox(2));
        btnBox3.setOnClickListener(v -> selectBox(3));
        btnBox4.setOnClickListener(v -> selectBox(4));

        // Edit Mode
        if (getIntent().hasExtra("id")) {

            isEditMode = true;

            medicineId = getIntent().getIntExtra("id", -1);

            startDate = getIntent().getLongExtra(
                    "startDate",
                    System.currentTimeMillis()
            );

            etMedicine.setText(getIntent().getStringExtra("name"));

            etDuration.setText(
                    String.valueOf(
                            getIntent().getIntExtra("duration", 0)
                    )
            );
            selectedBox = getIntent().getIntExtra("boxNumber", 1);
            selectBox(selectedBox);

            ArrayList<String> times =
                    getIntent().getStringArrayListExtra("times");

            if (times != null && !times.isEmpty()) {

                for (String t : times) {
                    addTimeButton(t);
                }

            } else {

                addTimeButton("08:00 AM");

            }

            btnSave.setText("Update Medicine");

        } else {

            startDate = System.currentTimeMillis();
            addTimeButton("08:00 AM");

        }

        btnAddTime.setOnClickListener(v -> {

            TimePickerDialog dialog = new TimePickerDialog(
                    this,
                    (view, hour, minute) -> {

                        String time24 = String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                hour,
                                minute
                        );

                        addTimeButton(time24);

                    },
                    8,
                    0,
                    false
            );

            dialog.show();

        });

        btnSave.setOnClickListener(v -> {

            String medicineName = etMedicine.getText().toString().trim();
            String durationText = etDuration.getText().toString().trim();

            if (medicineName.isEmpty()) {

                etMedicine.setError("Enter medicine name");
                return;

            }

            if (selectedTimes.isEmpty()) {

                Toast.makeText(
                        this,
                        "Add at least one reminder time",
                        Toast.LENGTH_SHORT
                ).show();

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

            if (isEditMode) {

                AppDatabase.getInstance(this)
                        .medicineDao()
                        .update(medicine);

                Toast.makeText(
                        this,
                        "Medicine Updated!",
                        Toast.LENGTH_SHORT
                ).show();

            } else {

                AppDatabase.getInstance(this)
                        .medicineDao()
                        .insert(medicine);

                Toast.makeText(
                        this,
                        "Medicine Saved!",
                        Toast.LENGTH_SHORT
                ).show();

            }

            for (String time : medicine.times) {

                com.example.espmedalarm.utils.AlarmScheduler.scheduleAlarm(
                        this,
                        medicine.name,
                        time,
                        medicine.boxNumber
                );

            }

            finish();

        });

    }
    private String formatTimeForDisplay(String time24) {

        try {

            SimpleDateFormat input =
                    new SimpleDateFormat("HH:mm", Locale.getDefault());

            SimpleDateFormat output =
                    new SimpleDateFormat("hh:mm a", Locale.getDefault());

            return output.format(input.parse(time24));

        } catch (Exception e) {

            return time24;

        }
    }
    private void addTimeButton(String time) {

        Button button = new Button(this);

        button.setAllCaps(false);

        button.setText(formatTimeForDisplay(time) + "   ❌");

        button.setOnClickListener(v -> {

            selectedTimes.remove(time);

            layoutTimes.removeView(button);

        });

        layoutTimes.addView(button);

        selectedTimes.add(time);

    }
    private void selectBox(int box) {

        selectedBox = box;

        btnBox1.setSelected(false);
        btnBox2.setSelected(false);
        btnBox3.setSelected(false);
        btnBox4.setSelected(false);

        btnBox1.setAlpha(0.5f);
        btnBox2.setAlpha(0.5f);
        btnBox3.setAlpha(0.5f);
        btnBox4.setAlpha(0.5f);

        switch (box) {

            case 1:
                btnBox1.setSelected(true);
                btnBox1.setAlpha(1f);
                break;

            case 2:
                btnBox2.setSelected(true);
                btnBox2.setAlpha(1f);
                break;

            case 3:
                btnBox3.setSelected(true);
                btnBox3.setAlpha(1f);
                break;

            case 4:
                btnBox4.setSelected(true);
                btnBox4.setAlpha(1f);
                break;
        }
    }

}
