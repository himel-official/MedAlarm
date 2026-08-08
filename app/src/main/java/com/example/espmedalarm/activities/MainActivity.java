package com.example.espmedalarm.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.espmedalarm.R;
import com.example.espmedalarm.fragments.EmergencyFragment;
import com.example.espmedalarm.fragments.HomeFragment;
import com.example.espmedalarm.fragments.MedicinesFragment;
import com.example.espmedalarm.fragments.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private final HomeFragment homeFragment = new HomeFragment();
    private final MedicinesFragment medicinesFragment = new MedicinesFragment();
    private final EmergencyFragment emergencyFragment = new EmergencyFragment();
    private final ProfileFragment profileFragment = new ProfileFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Defensive check: if there's no signed-in Firebase user (e.g. this
        // Activity was somehow reached directly), bounce back to LoginActivity
        // instead of showing medicine data that isn't tied to anyone.
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        requestNotificationPermission();

        bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            showFragment(homeFragment);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                showFragment(homeFragment);
                return true;
            } else if (id == R.id.nav_medicines) {
                showFragment(medicinesFragment);
                return true;
            } else if (id == R.id.nav_emergency) {
                showFragment(emergencyFragment);
                return true;
            } else if (id == R.id.nav_profile) {
                showFragment(profileFragment);
                return true;
            }

            return false;
        });
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    /** Used by the Dashboard's "Quick Sync" button. */
    public void switchToMedicinesAndSync() {
        bottomNav.setSelectedItemId(R.id.nav_medicines);
        medicinesFragment.triggerSync();
    }

    /** Used by the Dashboard's "Add Medicine" button. */
    public void switchToMedicinesAndAdd() {
        bottomNav.setSelectedItemId(R.id.nav_medicines);
        startActivity(new android.content.Intent(this, AddMedicineActivity.class));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }
}
