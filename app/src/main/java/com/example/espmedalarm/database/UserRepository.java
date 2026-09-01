package com.example.espmedalarm.database;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes to users/{uid} on signup/login so the Admin Panel can show total
 * users, who's recently active, and let an admin disable an account. This
 * is separate from MedicineRepository/EmergencyContactRepository, which use
 * the users/{uid}/medicines and users/{uid}/emergencyContacts subcollections
 * under the same parent doc.
 */
public class UserRepository {

    public interface DisabledCallback {
        void onResult(boolean disabled);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference userDoc(String uid) {
        return db.collection("users").document(uid);
    }

    public void recordSignup(@NonNull String uid, @NonNull String email) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("lastLoginAt", FieldValue.serverTimestamp());
        data.put("disabled", false);
        userDoc(uid).set(data, SetOptions.merge());
    }

    public void recordLogin(@NonNull String uid, @NonNull String email) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("lastLoginAt", FieldValue.serverTimestamp());
        userDoc(uid).set(data, SetOptions.merge());
    }

    /** Reports false (not disabled) if the check itself fails, so a transient
     *  read error never locks a legitimate user out of the app. */
    public void checkDisabled(@NonNull String uid, @NonNull DisabledCallback callback) {
        userDoc(uid).get()
                .addOnSuccessListener(doc -> {
                    Boolean disabled = doc.exists() ? doc.getBoolean("disabled") : Boolean.FALSE;
                    callback.onResult(Boolean.TRUE.equals(disabled));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }
}
