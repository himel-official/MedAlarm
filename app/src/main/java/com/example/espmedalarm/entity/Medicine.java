package com.example.espmedalarm.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.espmedalarm.database.Converters;

import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "medicines")
public class Medicine {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;

    @TypeConverters(Converters.class)
    public List<String> times;

    @ColumnInfo(name = "box_number")
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