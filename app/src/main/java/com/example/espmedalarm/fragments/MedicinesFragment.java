package com.example.espmedalarm.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.activities.AddMedicineActivity;
import com.example.espmedalarm.adapter.MedicineAdapter;
import com.example.espmedalarm.database.MedicineRepository;
import com.example.espmedalarm.entity.Medicine;
import com.example.espmedalarm.network.Esp32Api;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * Medicines tab: the redesigned version of the original MainActivity list
 * screen. Medicines are backed up per-account in Firestore
 * (MedicineRepository); the ESP32 /sync call itself is unchanged - it
 * still POSTs the same JSON shape (with a sequential int id) via Esp32Api,
 * it just now sources the list from Firestore instead of Room.
 */
public class MedicinesFragment extends Fragment {

    private static final String ESP_IP = "192.168.4.1";

    private final MedicineRepository medicineRepository = new MedicineRepository();

    private RecyclerView recyclerView;
    private MaterialButton btnSyncEsp;
    private FloatingActionButton fabAdd;
    private View emptyState;
    private SharedPreferences prefs;

    /** Set by MainActivity when the user taps "Quick Sync" from the Dashboard. */
    private boolean pendingAutoSync = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_medicines, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(HomeFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE);

        recyclerView = view.findViewById(R.id.recyclerMedicines);
        btnSyncEsp = view.findViewById(R.id.btnSyncEsp);
        fabAdd = view.findViewById(R.id.fabAdd);
        emptyState = view.findViewById(R.id.emptyState);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        btnSyncEsp.setOnClickListener(v -> syncWithEsp(ESP_IP));

        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddMedicineActivity.class)));

        if (pendingAutoSync) {
            pendingAutoSync = false;
            syncWithEsp(ESP_IP);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMedicines();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Without this, btnSyncEsp stays non-null after leaving this tab (it's a Fragment
        // field, not tied to the View's lifecycle) - triggerSync() would then think the
        // view is still ready and call syncWithEsp() immediately, at a point where this
        // fragment is mid-transition and detached, so it silently no-ops instead of
        // deferring via pendingAutoSync like it should.
        btnSyncEsp = null;
    }

    /** Called by MainActivity right after switching to this tab. */
    public void triggerSync() {
        if (btnSyncEsp != null) {
            syncWithEsp(ESP_IP);
        } else {
            pendingAutoSync = true;
        }
    }

    private void loadMedicines() {
        if (getContext() == null) return;

        medicineRepository.getAllMedicines(new MedicineRepository.MedicinesCallback() {
            @Override
            public void onSuccess(List<Medicine> medicines) {
                if (getContext() == null || recyclerView == null) return;

                recyclerView.setAdapter(new MedicineAdapter(getContext(), medicines));

                emptyState.setVisibility(medicines.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(medicines.isEmpty() ? View.GONE : View.VISIBLE);
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

    private void syncWithEsp(String ipOrUrl) {
        if (getContext() == null) return;

        String baseUrl = Esp32Api.normalizeBaseUrl(ipOrUrl);

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            android.widget.Toast.makeText(getContext(), "Invalid ESP IP address", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        android.widget.Toast.makeText(getContext(), "Connecting to ESP: " + baseUrl + " - syncing...", android.widget.Toast.LENGTH_SHORT).show();

        medicineRepository.getAllMedicines(new MedicineRepository.MedicinesCallback() {
            @Override
            public void onSuccess(List<Medicine> medicines) {
                sendToEsp(baseUrl, medicines);
            }

            @Override
            public void onError(String message) {
                if (getContext() == null) return;
                android.widget.Toast.makeText(getContext(),
                        "Could not load medicines to sync: " + message,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendToEsp(String baseUrl, List<Medicine> medicines) {
        new Esp32Api().syncMedicines(baseUrl, medicines, new Esp32Api.SyncCallback() {

            @Override
            public void onSuccess(String responseBody) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    prefs.edit()
                            .putBoolean(HomeFragment.KEY_DEVICE_CONNECTED, true)
                            .putLong(HomeFragment.KEY_LAST_SYNC, System.currentTimeMillis())
                            .apply();

                    android.widget.Toast.makeText(getContext(),
                            "Synced " + medicines.size() + " medicine(s) to ESP32",
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    prefs.edit().putBoolean(HomeFragment.KEY_DEVICE_CONNECTED, false).apply();

                    android.widget.Toast.makeText(getContext(),
                            "Sync failed: " + errorMessage,
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
