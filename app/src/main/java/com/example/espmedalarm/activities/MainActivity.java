//Final Version
/*
Micro Controller and Micro Processor
Himel Mahmud
 */
package com.example.espmedalarm.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.adapter.MedicineAdapter;
import com.example.espmedalarm.database.AppDatabase;
import com.example.espmedalarm.entity.Medicine;
import com.example.espmedalarm.network.Esp32Api;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "espmedalarm_prefs";
    private static final String KEY_ESP_IP = "esp_ip";

    // Fixed ESP32 IP address (manual entry removed)
    private static final String ESP_IP = "192.168.4.1";

    private FloatingActionButton fabAdd;

    private RecyclerView recyclerView;

    private Button btnSyncEsp;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.activity_main
        );

        requestNotificationPermission();

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Find views
        fabAdd =
                findViewById(
                        R.id.fabAdd
                );

        recyclerView =
                findViewById(
                        R.id.recyclerMedicines
                );

        btnSyncEsp =
                findViewById(
                        R.id.btnSyncEsp
                );

        // RecyclerView
        recyclerView.setLayoutManager(
                new LinearLayoutManager(
                        this
                )
        );

        loadMedicines();

        // Sync ESP button - uses the fixed ESP32 IP address
        btnSyncEsp.setOnClickListener(
                view -> {

                    // Remember it for next time
                    prefs.edit()
                            .putString(KEY_ESP_IP, ESP_IP)
                            .apply();

                    syncWithEsp(ESP_IP);
                }
        );

        // Add medicine button
        fabAdd.setOnClickListener(
                view -> {

                    Intent intent =
                            new Intent(
                                    MainActivity.this,
                                    AddMedicineActivity.class
                            );

                    startActivity(
                            intent
                    );
                }
        );
    }

    private void requestNotificationPermission() {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(
                    Manifest.permission
                            .POST_NOTIFICATIONS
            ) != PackageManager
                    .PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission
                                        .POST_NOTIFICATIONS
                        },
                        100
                );
            }
        }
    }

    private void syncWithEsp(
            String ipOrUrl
    ) {

        /*
         * Expected input:
         *
         * 192.168.1.42
         * (or "http://192.168.1.42" - Esp32Api normalizes it)
         *
         * This is the IP address printed on the ESP32's Serial
         * Monitor after it connects to your WiFi network.
         */

        String baseUrl = Esp32Api.normalizeBaseUrl(ipOrUrl);

        if (!baseUrl.startsWith("http://")
                && !baseUrl.startsWith("https://")) {

            Toast.makeText(
                    this,
                    "Invalid ESP IP address",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Toast.makeText(
                this,
                "Connecting to ESP: " + baseUrl + " - syncing...",
                Toast.LENGTH_SHORT
        ).show();

        // Read medicines from Room database
        List<Medicine> medicines =
                AppDatabase
                        .getInstance(this)
                        .medicineDao()
                        .getAllMedicines();

        // Send them to http://<esp-ip>/sync as JSON
        new Esp32Api().syncMedicines(
                baseUrl,
                medicines,
                new Esp32Api.SyncCallback() {

                    @Override
                    public void onSuccess(String responseBody) {

                        runOnUiThread(() ->
                                Toast.makeText(
                                        MainActivity.this,
                                        "Synced " + medicines.size()
                                                + " medicine(s) to ESP32",
                                        Toast.LENGTH_LONG
                                ).show()
                        );
                    }

                    @Override
                    public void onFailure(String errorMessage) {

                        runOnUiThread(() ->
                                Toast.makeText(
                                        MainActivity.this,
                                        "Sync failed: " + errorMessage,
                                        Toast.LENGTH_LONG
                                ).show()
                        );
                    }
                }
        );
    }

    @Override
    protected void onResume() {

        super.onResume();

        loadMedicines();
    }

    private void loadMedicines() {

        List<Medicine> medicines =
                AppDatabase
                        .getInstance(this)
                        .medicineDao()
                        .getAllMedicines();

        MedicineAdapter adapter =
                new MedicineAdapter(
                        this,
                        medicines
                );

        recyclerView.setAdapter(
                adapter
        );
    }
}
