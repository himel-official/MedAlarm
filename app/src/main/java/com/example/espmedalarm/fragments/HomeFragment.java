package com.example.espmedalarm.fragments;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.MedicineRepository;
import com.example.espmedalarm.entity.Medicine;
import com.example.espmedalarm.utils.MedicineStatusUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard (Home) tab. Reads the medicine list from Firestore
 * (MedicineRepository) so it reflects the same per-account backup as the
 * Medicines tab - device status/last-sync info still comes from local
 * SharedPreferences, since that's about this specific phone's connection
 * to the ESP32, not medicine data.
 */
public class HomeFragment extends Fragment {

    public static final String PREFS_NAME = "espmedalarm_prefs";
    public static final String KEY_LAST_SYNC = "last_sync_time";
    public static final String KEY_DEVICE_CONNECTED = "device_connected";

    // The Wi-Fi network the ESP32 device broadcasts. The Home screen only
    // shows "Connected" when the phone is actually joined to this SSID.
    private static final String TARGET_SSID = "MedAlarm-Setup";

    private final MedicineRepository medicineRepository = new MedicineRepository();

    private TextView txtGreeting, txtUserName, txtDateTime;
    private TextView txtNextMedicineName, txtNextMedicineDetail;
    private ImageView imgDeviceStatus;
    private TextView txtDeviceStatus, txtLastSync;
    private TextView txtTodayCount, txtOverviewSummary;
    private LinearProgressIndicator progressActive;
    private MaterialButton btnQuickSync, btnQuickAdd;

    private ActivityResultLauncher<String> locationPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Reading the currently-connected SSID requires location permission
        // on Android 8+. Re-check the connection status once the user
        // responds to the permission prompt.
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> renderDeviceStatus());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtGreeting = view.findViewById(R.id.txtGreeting);
        txtUserName = view.findViewById(R.id.txtUserName);
        txtDateTime = view.findViewById(R.id.txtDateTime);
        txtNextMedicineName = view.findViewById(R.id.txtNextMedicineName);
        txtNextMedicineDetail = view.findViewById(R.id.txtNextMedicineDetail);
        imgDeviceStatus = view.findViewById(R.id.imgDeviceStatus);
        txtDeviceStatus = view.findViewById(R.id.txtDeviceStatus);
        txtLastSync = view.findViewById(R.id.txtLastSync);
        txtTodayCount = view.findViewById(R.id.txtTodayCount);
        txtOverviewSummary = view.findViewById(R.id.txtOverviewSummary);
        progressActive = view.findViewById(R.id.progressActive);
        btnQuickSync = view.findViewById(R.id.btnQuickSync);
        btnQuickAdd = view.findViewById(R.id.btnQuickAdd);

        // Quick actions hand off to the Medicines tab so we reuse the
        // existing sync/add logic instead of duplicating it.
        btnQuickSync.setOnClickListener(v -> {
            if (getActivity() instanceof com.example.espmedalarm.activities.MainActivity) {
                ((com.example.espmedalarm.activities.MainActivity) getActivity())
                        .switchToMedicinesAndSync();
            }
        });

        btnQuickAdd.setOnClickListener(v -> {
            if (getActivity() instanceof com.example.espmedalarm.activities.MainActivity) {
                ((com.example.espmedalarm.activities.MainActivity) getActivity())
                        .switchToMedicinesAndAdd();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (getContext() == null) return;

        renderGreeting();
        renderDeviceStatus();

        medicineRepository.getAllMedicines(new MedicineRepository.MedicinesCallback() {
            @Override
            public void onSuccess(List<Medicine> medicines) {
                if (getContext() == null) return;
                renderNextMedicine(medicines);
                renderOverview(medicines);
            }

            @Override
            public void onError(String message) {
                if (getContext() == null) return;
                android.widget.Toast.makeText(getContext(),
                        "Could not load medicines: " + message,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String greeting;
        if (hour < 12) {
            greeting = "Good Morning";
        } else if (hour < 17) {
            greeting = "Good Afternoon";
        } else {
            greeting = "Good Evening";
        }

        txtGreeting.setText(greeting);
        txtUserName.setText("Welcome back");

        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d · hh:mm a", Locale.getDefault());
        txtDateTime.setText(sdf.format(calendar.getTime()));
    }

    private void renderDeviceStatus() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);

        boolean connected = isConnectedToMedAlarmSetup();
        long lastSync = prefs.getLong(KEY_LAST_SYNC, -1L);

        if (connected) {
            imgDeviceStatus.setImageResource(R.drawable.ic_wifi);
            txtDeviceStatus.setText("Connected");
        } else {
            imgDeviceStatus.setImageResource(R.drawable.ic_wifi_off);
            txtDeviceStatus.setText("Not Connected");
        }

        if (lastSync <= 0) {
            txtLastSync.setText("Never synced");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault());
            txtLastSync.setText("Last sync: " + sdf.format(new java.util.Date(lastSync)));
        }
    }

    /**
     * True only if the phone's active Wi-Fi connection is the ESP32's
     * MedAlarm-Setup network. Reading the real SSID (not just "is Wi-Fi
     * on") requires ACCESS_FINE_LOCATION on Android 8+; if that isn't
     * granted yet, this requests it and reports "not connected" until
     * the check can actually be made.
     */
    private boolean isConnectedToMedAlarmSetup() {
        android.content.Context context = getContext();
        if (context == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                if (locationPermissionLauncher != null) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                }
                return false;
            }
        }

        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(android.content.Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            return false;
        }

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) return false;

        String ssid = wifiInfo.getSSID();
        if (ssid == null) return false;

        // Android returns the SSID wrapped in quotes, e.g. "MedAlarm-Setup"
        ssid = ssid.replace("\"", "");

        return TARGET_SSID.equals(ssid);
    }

    private void renderNextMedicine(List<Medicine> medicines) {
        MedicineStatusUtils.NextDose next = MedicineStatusUtils.findNextDose(medicines);

        if (next == null) {
            txtNextMedicineName.setText("No upcoming doses");
            txtNextMedicineDetail.setText("Add a medicine to get started");
        } else {
            txtNextMedicineName.setText(next.medicine.name);
            txtNextMedicineDetail.setText("Box " + next.medicine.boxNumber + " · " + next.time);
        }

        txtTodayCount.setText(MedicineStatusUtils.countDosesToday(medicines) + " dose(s) today");
    }

    private void renderOverview(List<Medicine> medicines) {
        int active = 0, expiringSoon = 0, expired = 0;

        for (Medicine medicine : medicines) {
            if (!MedicineStatusUtils.isActive(medicine)) {
                expired++;
            } else if (MedicineStatusUtils.isExpiringSoon(medicine)) {
                expiringSoon++;
                active++;
            } else {
                active++;
            }
        }

        txtOverviewSummary.setText(active + " active · " + expiringSoon + " expiring soon · " + expired + " expired");

        int total = medicines.size();
        int progress = total == 0 ? 0 : Math.round((active * 100f) / total);
        progressActive.setProgress(progress);
    }
}
