package com.example.espmedalarm.utils;

import com.example.espmedalarm.entity.Medicine;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Centralizes the "how many days are left / is this medicine still active"
 * logic so the Medicines list, the Details screen, and the Dashboard all
 * agree on the same numbers. Pure UI-layer helper - does not touch the
 * Room database or the ESP32 sync protocol.
 */
public final class MedicineStatusUtils {

    private MedicineStatusUtils() {
    }

    /** Whole days that have passed since the medicine's start date. */
    public static int getPassedDays(Medicine medicine) {
        long diff = System.currentTimeMillis() - medicine.startDate;
        return (int) (diff / (1000L * 60 * 60 * 24));
    }

    /** Days left in the course. Can be zero or negative once completed. */
    public static int getRemainingDays(Medicine medicine) {
        return medicine.duration - getPassedDays(medicine);
    }

    public static boolean isActive(Medicine medicine) {
        return getRemainingDays(medicine) > 0;
    }

    /** True when 2 days or fewer remain, but the course isn't finished yet. */
    public static boolean isExpiringSoon(Medicine medicine) {
        int remaining = getRemainingDays(medicine);
        return remaining > 0 && remaining <= 2;
    }

    public static String getDurationLabel(Medicine medicine) {
        int remaining = getRemainingDays(medicine);
        if (remaining <= 0) {
            return "Course completed";
        }
        return remaining + " day(s) remaining";
    }

    /**
     * Finds the next upcoming reminder time (today or tomorrow) across all
     * currently-active medicines. Returns null if nothing is scheduled.
     */
    public static NextDose findNextDose(List<Medicine> medicines) {

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        Calendar now = Calendar.getInstance();

        NextDose best = null;
        long bestMillisFromNow = Long.MAX_VALUE;

        for (Medicine medicine : medicines) {

            if (!isActive(medicine) || medicine.times == null) continue;

            for (String time : medicine.times) {

                Date parsed;
                try {
                    parsed = sdf.parse(time);
                } catch (Exception e) {
                    continue;
                }
                if (parsed == null) continue;

                Calendar candidate = Calendar.getInstance();
                Calendar parsedCal = Calendar.getInstance();
                parsedCal.setTime(parsed);

                candidate.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY));
                candidate.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE));
                candidate.set(Calendar.SECOND, 0);
                candidate.set(Calendar.MILLISECOND, 0);

                if (candidate.before(now)) {
                    candidate.add(Calendar.DATE, 1);
                }

                long delta = candidate.getTimeInMillis() - now.getTimeInMillis();

                if (delta < bestMillisFromNow) {
                    bestMillisFromNow = delta;
                    best = new NextDose(medicine, time);
                }
            }
        }

        return best;
    }

    /** Counts how many reminder times fire today across active medicines. */
    public static int countDosesToday(List<Medicine> medicines) {
        int count = 0;
        for (Medicine medicine : medicines) {
            if (isActive(medicine) && medicine.times != null) {
                count += medicine.times.size();
            }
        }
        return count;
    }

    public static class NextDose {
        public final Medicine medicine;
        public final String time;

        public NextDose(Medicine medicine, String time) {
            this.medicine = medicine;
            this.time = time;
        }
    }
}
