package com.example.espmedalarm.database;

import androidx.annotation.NonNull;

import com.example.espmedalarm.entity.AdminUser;
import com.example.espmedalarm.utils.FixedEmergencyNumbers;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the password-protected Admin Panel:
 *  - total / active user counts and the full user list, read from the
 *    users/{uid} docs written by UserRepository on signup/login
 *  - removing (disabling) a user's access and wiping their per-account data
 *  - the editable "Emergency Numbers" list shown on the Emergency tab,
 *    stored at config/emergencyNumbers, falling back to
 *    FixedEmergencyNumbers defaults until an admin sets one
 *
 * Note: this can disable a user's in-app access and delete their app data,
 * but it can't delete their underlying Firebase Authentication account -
 * that requires the Firebase Admin SDK from a trusted backend (e.g. a
 * Cloud Function), which a client app cannot do for security reasons.
 */
public class AdminRepository {

    public interface StatsCallback {
        void onSuccess(int totalUsers, int activeUsers);
        void onError(String message);
    }

    public interface UsersCallback {
        void onSuccess(List<AdminUser> users);
        void onError(String message);
    }

    public interface OpCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface NumbersCallback {
        void onSuccess(List<FixedEmergencyNumbers.Entry> numbers);
        void onError(String message);
    }

    // A user is considered "active" if they've logged in within this window.
    private static final long ACTIVE_WINDOW_MILLIS = 24L * 60 * 60 * 1000;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void getAllUsers(@NonNull UsersCallback callback) {
        db.collection("users")
                .orderBy("lastLoginAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener((QuerySnapshot snapshot) -> {
                    List<AdminUser> users = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        AdminUser user = new AdminUser();
                        user.uid = doc.getId();
                        user.email = doc.getString("email");

                        Timestamp lastLogin = doc.getTimestamp("lastLoginAt");
                        user.lastLoginAtMillis = lastLogin != null ? lastLogin.toDate().getTime() : 0L;

                        Timestamp createdAt = doc.getTimestamp("createdAt");
                        user.createdAtMillis = createdAt != null ? createdAt.toDate().getTime() : 0L;

                        Boolean disabled = doc.getBoolean("disabled");
                        user.disabled = Boolean.TRUE.equals(disabled);

                        users.add(user);
                    }
                    callback.onSuccess(users);
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not load users"));
    }

    public void getStats(@NonNull StatsCallback callback) {
        getAllUsers(new UsersCallback() {
            @Override
            public void onSuccess(List<AdminUser> users) {
                long cutoff = System.currentTimeMillis() - ACTIVE_WINDOW_MILLIS;
                int active = 0;
                for (AdminUser user : users) {
                    if (!user.disabled && user.lastLoginAtMillis >= cutoff) {
                        active++;
                    }
                }
                callback.onSuccess(users.size(), active);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Revokes the user's access (checked on their next login attempt) and
     * removes their medicines and emergency contacts. Does not delete the
     * underlying Firebase Authentication account - see class note above.
     */
    public void removeUser(@NonNull String uid, @NonNull OpCallback callback) {
        db.collection("users").document(uid)
                .update("disabled", true)
                .addOnSuccessListener(unused -> {
                    deleteAllDocs(db.collection("users").document(uid).collection("medicines"));
                    deleteAllDocs(db.collection("users").document(uid).collection("emergencyContacts"));
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not remove user"));
    }

    private void deleteAllDocs(com.google.firebase.firestore.CollectionReference collection) {
        collection.get().addOnSuccessListener(snapshot -> {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                doc.getReference().delete();
            }
        });
    }

    public void getEmergencyNumbers(@NonNull NumbersCallback callback) {
        db.collection("config").document("emergencyNumbers").get()
                .addOnSuccessListener(doc -> {
                    List<FixedEmergencyNumbers.Entry> entries = new ArrayList<>();
                    if (doc.exists()) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> raw =
                                (List<Map<String, Object>>) doc.get("entries");
                        if (raw != null) {
                            for (Map<String, Object> item : raw) {
                                entries.add(new FixedEmergencyNumbers.Entry(
                                        String.valueOf(item.get("label")),
                                        String.valueOf(item.get("subtitle")),
                                        String.valueOf(item.get("number"))));
                            }
                        }
                    }
                    callback.onSuccess(entries);
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not load emergency numbers"));
    }

    public void saveEmergencyNumbers(@NonNull List<FixedEmergencyNumbers.Entry> entries,
                                      @NonNull OpCallback callback) {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (FixedEmergencyNumbers.Entry entry : entries) {
            Map<String, Object> item = new HashMap<>();
            item.put("label", entry.label);
            item.put("subtitle", entry.subtitle);
            item.put("number", entry.number);
            raw.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("entries", raw);

        db.collection("config").document("emergencyNumbers").set(data)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not save emergency numbers"));
    }
}
