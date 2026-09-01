package com.example.espmedalarm.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Entry point / launcher activity. Shows an email + password sign-in form.
 * If a Firebase session already exists (e.g. app was reopened without
 * logging out), skips straight to MainActivity.
 */
public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private final UserRepository userRepository = new UserRepository();

    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        setContentView(R.layout.activity_login);

        if (auth.getCurrentUser() != null) {
            checkNotDisabledThenGoToMain(auth.getCurrentUser());
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());

        findViewById(R.id.txtGoToSignup).setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
    }

    private void attemptLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter your email");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Enter your password");
            return;
        }

        btnLogin.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        userRepository.recordLogin(user.getUid(),
                                user.getEmail() != null ? user.getEmail() : "");
                    }
                    checkNotDisabledThenGoToMain(user);
                })
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    Toast.makeText(
                            this,
                            "Login failed: " + (e.getMessage() != null ? e.getMessage() : "please try again"),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    /** Blocks sign-in if an admin has removed this account (see AdminPanelActivity). */
    private void checkNotDisabledThenGoToMain(FirebaseUser user) {
        if (user == null) {
            goToMain();
            return;
        }

        userRepository.checkDisabled(user.getUid(), disabled -> {
            if (disabled) {
                auth.signOut();
                if (btnLogin != null) btnLogin.setEnabled(true);
                Toast.makeText(this,
                        "This account has been disabled by an admin.", Toast.LENGTH_LONG).show();
            } else {
                goToMain();
            }
        });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
