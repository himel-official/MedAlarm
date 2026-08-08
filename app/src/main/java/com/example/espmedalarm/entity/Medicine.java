package com.example.espmedalarm.entity;

import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain POJO stored in Firestore at users/{uid}/medicines/{id}.
 * Needs a public no-arg constructor and public fields so the Firestore
 * SDK can deserialize documents into this class automatically.
 */
public class Medicine {

    // Firestore document ID. Not written back into the document body -
    // it's already the document's path segment.
    @Exclude
    public String id;

    public String name;

    public List<String> times;

    public int boxNumber;

    public int duration;

    public long startDate;

    public Medicine() {
        times = new ArrayList<>();
        boxNumber = 1; // Default to Box 1
    }

    public Medicine(String name, List<String> times, int duration, int boxNumber) {
        this.name = name;
        this.times = times;
        this.duration = duration;
        this.boxNumber = boxNumber;
        this.startDate = System.currentTimeMillis();
    }
}
