package com.example.espmedalarm.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.adapter.AdminNumberAdapter;
import com.example.espmedalarm.adapter.AdminUserAdapter;
import com.example.espmedalarm.database.AdminRepository;
import com.example.espmedalarm.entity.AdminUser;
import com.example.espmedalarm.utils.FixedEmergencyNumbers;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin screen reached via AdminLoginActivity, which checks that the
 * currently logged-in account's uid has a document at admins/{uid}:
 *  - total / active (last 24h) user counts
 *  - add/edit/delete the Emergency Numbers list shown on the Emergency tab
 *  - view registered users and remove (disable) their access
 */
public class AdminPanelActivity extends AppCompatActivity {

    private final AdminRepository adminRepository = new AdminRepository();

    private TextView txtTotalUsers, txtActiveUsers, txtNumbersEmpty, txtUsersEmpty;
    private RecyclerView recyclerAdminNumbers, recyclerAdminUsers;

    private final List<FixedEmergencyNumbers.Entry> numberEntries = new ArrayList<>();
    private AdminNumberAdapter numberAdapter;
    private boolean numbersWereDefaulted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_panel);

        // TEMP DEBUG: shows the signed-in Firebase Auth UID so it can be
        // compared against the admins/{uid} doc in Firestore. Remove once
        // the admin-panel permission issue is confirmed fixed.
        com.google.firebase.auth.FirebaseUser debugUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        Toast.makeText(this, "My UID: " + (debugUser != null ? debugUser.getUid() : "not signed in"),
                Toast.LENGTH_LONG).show();

        txtTotalUsers = findViewById(R.id.txtTotalUsers);
        txtActiveUsers = findViewById(R.id.txtActiveUsers);
        txtNumbersEmpty = findViewById(R.id.txtNumbersEmpty);
        txtUsersEmpty = findViewById(R.id.txtUsersEmpty);
        recyclerAdminNumbers = findViewById(R.id.recyclerAdminNumbers);
        recyclerAdminUsers = findViewById(R.id.recyclerAdminUsers);

        recyclerAdminNumbers.setLayoutManager(new LinearLayoutManager(this));
        recyclerAdminUsers.setLayoutManager(new LinearLayoutManager(this));

        numberAdapter = new AdminNumberAdapter(numberEntries, new AdminNumberAdapter.Listener() {
            @Override
            public void onEdit(int position, FixedEmergencyNumbers.Entry entry) {
                showEditNumberDialog(position, entry);
            }

            @Override
            public void onDelete(int position) {
                numberEntries.remove(position);
                numberAdapter.notifyItemRemoved(position);
            }
        });
        recyclerAdminNumbers.setAdapter(numberAdapter);

        findViewById(R.id.btnAdminBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddNumber).setOnClickListener(v -> showEditNumberDialog(-1, null));
        findViewById(R.id.btnSaveNumbers).setOnClickListener(v -> saveNumbers());

        loadStats();
        loadNumbers();
        loadUsers();
    }

    private void loadStats() {
        adminRepository.getStats(new AdminRepository.StatsCallback() {
            @Override
            public void onSuccess(int totalUsers, int activeUsers) {
                txtTotalUsers.setText(String.valueOf(totalUsers));
                txtActiveUsers.setText(String.valueOf(activeUsers));
            }

            @Override
            public void onError(String message) {
                txtTotalUsers.setText("-");
                txtActiveUsers.setText("-");
            }
        });
    }

    private void loadNumbers() {
        adminRepository.getEmergencyNumbers(new AdminRepository.NumbersCallback() {
            @Override
            public void onSuccess(List<FixedEmergencyNumbers.Entry> numbers) {
                numberEntries.clear();
                if (numbers.isEmpty()) {
                    numberEntries.addAll(FixedEmergencyNumbers.getAll());
                    numbersWereDefaulted = true;
                } else {
                    numberEntries.addAll(numbers);
                    numbersWereDefaulted = false;
                }
                numberAdapter.notifyDataSetChanged();
                txtNumbersEmpty.setVisibility(numbersWereDefaulted ? android.view.View.VISIBLE : android.view.View.GONE);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminPanelActivity.this,
                        "Could not load numbers: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadUsers() {
        adminRepository.getAllUsers(new AdminRepository.UsersCallback() {
            @Override
            public void onSuccess(List<AdminUser> users) {
                txtUsersEmpty.setVisibility(users.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                recyclerAdminUsers.setAdapter(new AdminUserAdapter(users, AdminPanelActivity.this::confirmRemoveUser));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminPanelActivity.this,
                        "Could not load users: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmRemoveUser(AdminUser user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove user?")
                .setMessage("This blocks " + (user.email != null ? user.email : user.uid)
                        + " from signing in and deletes their medicines and emergency contacts.")
                .setPositiveButton("Remove", (dialog, which) ->
                        adminRepository.removeUser(user.uid, new AdminRepository.OpCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(AdminPanelActivity.this, "User removed", Toast.LENGTH_SHORT).show();
                                loadStats();
                                loadUsers();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(AdminPanelActivity.this,
                                        "Could not remove user: " + message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditNumberDialog(int position, FixedEmergencyNumbers.Entry existing) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_number, null);

        TextInputEditText etLabel = dialogView.findViewById(R.id.etNumberLabel);
        TextInputEditText etSubtitle = dialogView.findViewById(R.id.etNumberSubtitle);
        TextInputEditText etValue = dialogView.findViewById(R.id.etNumberValue);

        if (existing != null) {
            etLabel.setText(existing.label);
            etSubtitle.setText(existing.subtitle);
            etValue.setText(existing.number);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing != null ? "Edit Number" : "Add Number")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            MaterialButton btnSave = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String label = etLabel.getText() != null ? etLabel.getText().toString().trim() : "";
                String subtitle = etSubtitle.getText() != null ? etSubtitle.getText().toString().trim() : "";
                String number = etValue.getText() != null ? etValue.getText().toString().trim() : "";

                if (TextUtils.isEmpty(label)) {
                    etLabel.setError("Enter a label");
                    return;
                }
                if (TextUtils.isEmpty(number)) {
                    etValue.setError("Enter a phone number");
                    return;
                }

                FixedEmergencyNumbers.Entry entry = new FixedEmergencyNumbers.Entry(label, subtitle, number);

                // The very first edit replaces the shown defaults instead of
                // appending to them.
                if (numbersWereDefaulted) {
                    numberEntries.clear();
                    numbersWereDefaulted = false;
                    txtNumbersEmpty.setVisibility(android.view.View.GONE);
                }

                if (position >= 0 && position < numberEntries.size()) {
                    numberEntries.set(position, entry);
                } else {
                    numberEntries.add(entry);
                }
                numberAdapter.notifyDataSetChanged();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void saveNumbers() {
        adminRepository.saveEmergencyNumbers(numberEntries, new AdminRepository.OpCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(AdminPanelActivity.this, "Emergency numbers saved", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminPanelActivity.this,
                        "Could not save: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }
}