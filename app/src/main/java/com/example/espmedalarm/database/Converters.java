package com.example.espmedalarm.database;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Converters {

    @TypeConverter
    public static String fromList(List<String> list) {
        return new Gson().toJson(list);
    }

    @TypeConverter
    public static List<String> toList(String value) {

        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<String>>() {}.getType();

        return new Gson().fromJson(value, type);
    }
}