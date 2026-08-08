package com.example.espmedalarm.entity;

import com.google.firebase.firestore.Exclude;

/**
 * Plain POJO stored in Firestore at users/{uid}/emergencyContacts/{docId}.
 * Needs a public no-arg constructor and public fields so the Firestore
 * SDK can deserialize documents into this class automatically.
 */
public class EmergencyContact {

    @Exclude
    public String id;

    public String name;
    public String relation;
    public String phone;

    public EmergencyContact() {
    }

    public EmergencyContact(String name, String relation, String phone) {
        this.name = name;
        this.relation = relation;
        this.phone = phone;
    }
}
