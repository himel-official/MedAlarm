package com.example.espmedalarm.activities;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.UserRepository;
import com.example.espmedalarm.fragments.EmergencyFragment;
import com.example.espmedalarm.fragments.HomeFragment;
import com.example.espmedalarm.fragments.MedicinesFragment;
import com.example.espmedalarm.fragments.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
Final Version
Author: Himel Mahmud
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private final HomeFragment homeFragment = new HomeFragment();
    private final MedicinesFragment medicinesFragment = new MedicinesFragment();
    private final EmergencyFragment emergencyFragment = new EmergencyFragment();
    private final ProfileFragment profileFragment = new ProfileFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        new UserRepository().checkDisabled(FirebaseAuth.getInstance().getCurrentUser().getUid(),
                disabled -> {
                    if (disabled) {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });

        setContentView(R.layout.activity_main);

        requestNotificationPermission();
        requestDndBypassAccess();
        requestFullScreenIntentAccess();

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

    public void switchToMedicinesAndSync() {
        bottomNav.setSelectedItemId(R.id.nav_medicines);
        medicinesFragment.triggerSync();
    }

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

    // Ask the user to grant "Do Not Disturb access" so the medicine alarm
    // (sound + vibration) can bypass DND / silent / focus modes.
    private void requestDndBypassAccess() {
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (nm != null && !nm.isNotificationPolicyAccessGranted()) {
            Intent intent = new Intent(
                    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    // On Android 14+ (API 34+), full-screen alarm intents also need this
    // explicit permission grant so the AlarmActivity page reliably pops up.
    private void requestFullScreenIntentAccess() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            if (nm != null && !nm.canUseFullScreenIntent()) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }
}
