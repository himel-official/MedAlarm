package com.example.espmedalarm.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.espmedalarm.entity.Medicine;

import java.util.List;

@Dao
public interface MedicineDao {

    @Insert
    void insert(Medicine medicine);

    @Update
    void update(Medicine medicine);

    @Delete
    void delete(Medicine medicine);

    @Query("SELECT * FROM medicines ORDER BY name ASC")
    List<Medicine> getAllMedicines();

    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    Medicine getMedicineById(int id);

}