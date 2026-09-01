package com.example.espmedalarm.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.AdminRepository;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Access to the Admin Panel is tied to the account that's already logged
 * into the app: whoever's Firebase Auth uid has a document at
 * admins/{uid} in Firestore gets in, no separate admin password needed.
 * Different admins are simply different documents in that collection.
 */
public class AdminLoginActivity extends AppCompatActivity {

    private final AdminRepository adminRepository = new AdminRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        checkAdminAccess();
    }

    private void checkAdminAccess() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            Toast.makeText(this, "You need to be logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adminRepository.isAdmin(uid, isAdmin -> {
            if (isAdmin) {
                startActivity(new Intent(this, AdminPanelActivity.class));
            } else {
                Toast.makeText(this, "You don't have admin access", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }
}
