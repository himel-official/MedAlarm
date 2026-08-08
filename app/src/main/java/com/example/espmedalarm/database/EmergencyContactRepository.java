package com.example.espmedalarm.database;

import androidx.annotation.NonNull;

import com.example.espmedalarm.entity.EmergencyContact;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-user emergency contact storage backed by Cloud Firestore, at
 * users/{uid}/emergencyContacts/{docId} - same pattern as
 * MedicineRepository, so contacts are backed up and follow the account
 * across installs/devices.
 */
public class EmergencyContactRepository {

    public interface ContactsCallback {
        void onSuccess(List<EmergencyContact> contacts);
        void onError(String message);
    }

    public interface OpCallback {
        void onSuccess();
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private CollectionReference contactsCollection() {
        String uid = currentUid();
        return db.collection("users").document(uid).collection("emergencyContacts");
    }

    private String currentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            throw new IllegalStateException("No signed-in user");
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public void getAllContacts(@NonNull ContactsCallback callback) {
        contactsCollection()
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener((QuerySnapshot snapshot) -> {
                    List<EmergencyContact> contacts = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        EmergencyContact contact = doc.toObject(EmergencyContact.class);
                        if (contact != null) {
                            contact.id = doc.getId();
                            contacts.add(contact);
                        }
                    }
                    callback.onSuccess(contacts);
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not load contacts"));
    }

    public void insert(EmergencyContact contact, @NonNull OpCallback callback) {
        contactsCollection().add(contact)
                .addOnSuccessListener(ref -> {
                    contact.id = ref.getId();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not save contact"));
    }

    public void delete(EmergencyContact contact, @NonNull OpCallback callback) {
        contactsCollection().document(contact.id).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not delete contact"));
    }
}
