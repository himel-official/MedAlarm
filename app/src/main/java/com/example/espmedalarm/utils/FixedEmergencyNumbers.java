package com.example.espmedalarm.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed (non-editable) emergency service numbers. Defaults to Bangladesh's
 * National Emergency Service directory - update these if the app is used
 * in a different country.
 */
public class FixedEmergencyNumbers {

    public static class Entry {
        public final String label;
        public final String subtitle;
        public final String number;

        public Entry(String label, String subtitle, String number) {
            this.label = label;
            this.subtitle = subtitle;
            this.number = number;
        }
    }

    public static List<Entry> getAll() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry("National Emergency", "Police · Fire · Ambulance", "999"));
        list.add(new Entry("Health Helpline", "Shastho Batayon", "16263"));
        list.add(new Entry("National Disaster Helpline", "Disaster & crisis support", "1090"));
        list.add(new Entry("Women & Child Helpline", "24/7 support", "109"));
        return list;
    }
}
