package com.example.espmedalarm.network;

import com.example.espmedalarm.entity.Medicine;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles all HTTP communication with the ESP32.
 *
 * The ESP32 is expected to run a small web server (see the companion
 * esp32_firmware.ino sketch) that exposes:
 *
 *   POST /sync   - accepts a JSON body describing all medicines/schedules
 *
 * The QR code printed on / shown by the ESP32 should just contain its
 * base URL, e.g.  http://192.168.4.1
 */
public class Esp32Api {

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    public interface SyncCallback {
        void onSuccess(String responseBody);
        void onFailure(String errorMessage);
    }

    private final OkHttpClient client;

    public Esp32Api() {
        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Turns whatever the QR code contained into a clean base URL.
     * Accepts "192.168.4.1", "http://192.168.4.1", "http://192.168.4.1/" ...
     */
    public static String normalizeBaseUrl(String raw) {

        String url = raw.trim();

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    /**
     * Builds the JSON payload sent to the ESP32:
     *
     * {
     *   "currentTime": 1735689600000,
     *   "medicines": [
     *     {
     *       "id": 1,
     *       "name": "Paracetamol",
     *       "box": 1,
     *       "duration": 7,
     *       "startDate": 1735689600000,
     *       "times": ["08:00", "20:00"]
     *     }
     *   ]
     * }
     */
    public String buildSyncJson(List<Medicine> medicines) {

        JsonArray array = new JsonArray();

        // The ESP32 firmware expects a numeric "id" - Firestore document IDs
        // are strings, so we send each medicine's position in the list
        // (1-based) instead. This keeps the /sync JSON contract with the
        // ESP32 exactly the same as before, regardless of how medicines are
        // stored on the phone.
        int nextId = 1;

        for (Medicine m : medicines) {

            JsonObject obj = new JsonObject();
            obj.addProperty("id", nextId++);
            obj.addProperty("name", m.name);
            obj.addProperty("box", m.boxNumber);
            obj.addProperty("duration", m.duration);
            obj.addProperty("startDate", m.startDate);

            JsonArray times = new JsonArray();
            if (m.times != null) {
                for (String t : m.times) {
                    times.add(to24Hour(t));
                }
            }
            obj.add("times", times);

            array.add(obj);
        }

        JsonObject root = new JsonObject();
        // Phone's current time (epoch millis), so the ESP32 can set its own clock
        root.addProperty("currentTime", System.currentTimeMillis());
        root.add("medicines", array);

        return new Gson().toJson(root);
    }

    /**
     * The app stores/displays reminder times as 12-hour strings with
     * AM/PM (e.g. "08:00 PM") - that's what AlarmScheduler and the UI
     * expect. The ESP32 firmware, however, only reads the first 5
     * characters of each time string and expects 24-hour "HH:mm" (e.g.
     * "20:00") - without this conversion, every PM time would silently
     * get truncated to its AM equivalent on the device. This only
     * affects what's sent over the wire; storage/display elsewhere in
     * the app is untouched.
     */
    private static String to24Hour(String time12h) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("hh:mm a", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("HH:mm", Locale.US);
            return out.format(in.parse(time12h));
        } catch (ParseException e) {
            // Already in some other format (e.g. 24-hour) - send as-is.
            return time12h;
        }
    }

    /**
     * Sends all medicines to the ESP32's /sync endpoint.
     * Callback methods are invoked on a background thread -
     * hop back to the UI thread (e.g. runOnUiThread) before touching views.
     */
    public void syncMedicines(
            String baseUrl,
            List<Medicine> medicines,
            SyncCallback callback
    ) {

        String json = buildSyncJson(medicines);

        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(normalizeBaseUrl(baseUrl) + "/sync")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    callback.onFailure(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "Could not reach ESP32"
                    );
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {

                    String responseBody =
                            r.body() != null ? r.body().string() : "";

                    if (r.isSuccessful()) {
                        if (callback != null) callback.onSuccess(responseBody);
                    } else {
                        if (callback != null) {
                            callback.onFailure("ESP32 responded with HTTP " + r.code());
                        }
                    }
                }
            }
        });
    }
}
