package com.example.espmedalarm.database;

import androidx.annotation.NonNull;

import com.example.espmedalarm.entity.Medicine;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-user medicine storage backed by Cloud Firestore, at
 * users/{uid}/medicines/{docId}. This is the source of truth for medicine
 * data - it's a cloud backup that survives app uninstall/reinstall and
 * follows the signed-in account across devices, instead of living only in
 * a local on-device database.
 */
public class MedicineRepository {

    public interface MedicinesCallback {
        void onSuccess(List<Medicine> medicines);
        void onError(String message);
    }

    public interface MedicineCallback {
        void onSuccess(Medicine medicine);
        void onError(String message);
    }

    public interface OpCallback {
        void onSuccess();
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private CollectionReference medicinesCollection() {
        String uid = currentUid();
        return db.collection("users").document(uid).collection("medicines");
    }

    private String currentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            throw new IllegalStateException("No signed-in user");
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public void getAllMedicines(@NonNull MedicinesCallback callback) {
        medicinesCollection()
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener((QuerySnapshot snapshot) -> {
                    List<Medicine> medicines = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Medicine medicine = doc.toObject(Medicine.class);
                        if (medicine != null) {
                            medicine.id = doc.getId();
                            medicines.add(medicine);
                        }
                    }
                    callback.onSuccess(medicines);
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not load medicines"));
    }

    public void getMedicineById(String id, @NonNull MedicineCallback callback) {
        medicinesCollection().document(id).get()
                .addOnSuccessListener(doc -> {
                    Medicine medicine = doc.exists() ? doc.toObject(Medicine.class) : null;
                    if (medicine == null) {
                        callback.onError("Medicine not found");
                        return;
                    }
                    medicine.id = doc.getId();
                    callback.onSuccess(medicine);
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not load medicine"));
    }

    public void insert(Medicine medicine, @NonNull OpCallback callback) {
        medicinesCollection().add(medicine)
                .addOnSuccessListener(ref -> {
                    medicine.id = ref.getId();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not save medicine"));
    }

    public void update(Medicine medicine, @NonNull OpCallback callback) {
        medicinesCollection().document(medicine.id).set(medicine)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not update medicine"));
    }

    public void delete(Medicine medicine, @NonNull OpCallback callback) {
        medicinesCollection().document(medicine.id).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not delete medicine"));
    }
}
