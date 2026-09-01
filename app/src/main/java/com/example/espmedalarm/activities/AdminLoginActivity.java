package com.example.espmedalarm.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.example.espmedalarm.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class AdminLoginActivity extends AppCompatActivity {

    private static final String ADMIN_PASSWORD = "20245103282";

    private TextInputEditText etAdminPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        etAdminPassword = findViewById(R.id.etAdminPassword);
        MaterialButton btnAdminEnter = findViewById(R.id.btnAdminEnter);

        btnAdminEnter.setOnClickListener(v -> attemptEnter());
    }

    private void attemptEnter() {
        String entered = etAdminPassword.getText() != null
                ? etAdminPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(entered)) {
            etAdminPassword.setError("Enter the admin password");
            return;
        }

        if (ADMIN_PASSWORD.equals(entered)) {
            startActivity(new Intent(this, AdminPanelActivity.class));
            finish();
        } else {
            etAdminPassword.setError("Incorrect password");
        }
    }
}
