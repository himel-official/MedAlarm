package com.example.espmedalarm.fragments;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.adapter.EmergencyContactAdapter;
import com.example.espmedalarm.adapter.FixedNumberAdapter;
import com.example.espmedalarm.database.AdminRepository;
import com.example.espmedalarm.database.EmergencyContactRepository;
import com.example.espmedalarm.entity.EmergencyContact;
import com.example.espmedalarm.utils.FixedEmergencyNumbers;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

/**
 * Emergency tab:
 *  1) "Hospitals" / "Medical Stores" buttons still launch Google Maps (or
 *     any installed maps app) centered on the device's own location, same
 *     as before.
 *  2) A live small map preview (Leaflet + OpenStreetMap, no API key/billing
 *     needed) shows the current location plus nearby hospitals and medical
 *     stores together. "View Bigger Map" opens Google Maps externally with
 *     the current location and both categories.
 *  3) Emergency Contacts (family & friends), stored per-account in
 *     Firestore via EmergencyContactRepository, same pattern as medicines.
 *  4) Emergency numbers (National Emergency, Ambulance, etc.) - editable by
 *     an admin (see AdminRepository); falls back to FixedEmergencyNumbers
 *     defaults if none have been configured yet.
 */
public class EmergencyFragment extends Fragment {

    private final EmergencyContactRepository contactRepository = new EmergencyContactRepository();
    private final AdminRepository adminRepository = new AdminRepository();

    private RecyclerView recyclerContacts;
    private RecyclerView recyclerFixedNumbers;
    private View txtContactsEmpty;

    private WebView webMapPreview;
    private View txtMapPermissionNeeded;
    private ActivityResultLauncher<String> locationPermissionLauncher;
    private LocationManager locationManager;
    private Double lastLat;
    private Double lastLng;
    private boolean mapPageLoaded = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        loadLiveLocation();
                    } else if (txtMapPermissionNeeded != null) {
                        txtMapPermissionNeeded.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_emergency, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerContacts = view.findViewById(R.id.recyclerContacts);
        recyclerFixedNumbers = view.findViewById(R.id.recyclerFixedNumbers);
        txtContactsEmpty = view.findViewById(R.id.txtContactsEmpty);
        webMapPreview = view.findViewById(R.id.webMapPreview);
        txtMapPermissionNeeded = view.findViewById(R.id.txtMapPermissionNeeded);

        recyclerContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerFixedNumbers.setLayoutManager(new LinearLayoutManager(requireContext()));

        loadEmergencyNumbers();

        view.findViewById(R.id.btnFindHospitals).setOnClickListener(v ->
                openNearbyPlaces("hospitals+near+me"));
        view.findViewById(R.id.btnFindPharmacies).setOnClickListener(v ->
                openNearbyPlaces("medical+store+pharmacy+near+me"));
        view.findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactDialog());
        view.findViewById(R.id.btnViewBiggerMap).setOnClickListener(v -> openBiggerMap());

        setupMapPreview();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadContacts();
    }

    private void loadContacts() {
        if (getContext() == null) return;

        contactRepository.getAllContacts(new EmergencyContactRepository.ContactsCallback() {
            @Override
            public void onSuccess(List<EmergencyContact> contacts) {
                if (getContext() == null || recyclerContacts == null) return;

                recyclerContacts.setAdapter(new EmergencyContactAdapter(getContext(), contacts));

                boolean empty = contacts.isEmpty();
                txtContactsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                recyclerContacts.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Could not load contacts: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openNearbyPlaces(String encodedQuery) {
        // geo:0,0?q=... lets the maps app resolve "near me" using the
        // device's own current location - no location permission or
        // Maps API key needed on our side.
        Uri uri = Uri.parse("geo:0,0?q=" + encodedQuery);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Google Maps isn't installed - fall back to whatever maps app is available.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(getContext(), "No maps app found", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---- Live map preview (small window: current location + hospitals + pharmacies) ----

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupMapPreview() {
        if (webMapPreview == null) return;

        webMapPreview.getSettings().setJavaScriptEnabled(true);
        webMapPreview.getSettings().setDomStorageEnabled(true);
        webMapPreview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mapPageLoaded = true;
                pushLocationToMap();
            }
        });
        webMapPreview.loadUrl("file:///android_asset/emergency_map.html");

        loadLiveLocation();
    }

    private void loadLiveLocation() {
        if (getContext() == null) return;

        boolean granted = ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!granted) {
            granted = ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        if (!granted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        if (txtMapPermissionNeeded != null) {
            txtMapPermissionNeeded.setVisibility(View.GONE);
        }

        if (locationManager == null) {
            locationManager = (LocationManager) getContext().getSystemService(android.content.Context.LOCATION_SERVICE);
        }
        if (locationManager == null) return;

        Location best = null;
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null && network != null) {
                best = gps.getTime() >= network.getTime() ? gps : network;
            } else {
                best = gps != null ? gps : network;
            }
        } catch (SecurityException ignored) {
            // Permission was revoked between the check above and this call.
        }

        if (best != null) {
            onLocationReady(best);
            return;
        }

        // No cached fix yet - ask for a single fresh update.
        try {
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    onLocationReady(location);
                    if (locationManager != null) {
                        locationManager.removeUpdates(this);
                    }
                }
            };

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null);
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null);
            }
        } catch (SecurityException ignored) {
            // Permission was revoked between the check above and this call.
        }
    }

    private void onLocationReady(Location location) {
        lastLat = location.getLatitude();
        lastLng = location.getLongitude();
        pushLocationToMap();
    }

    private void pushLocationToMap() {
        if (webMapPreview == null || !mapPageLoaded || lastLat == null || lastLng == null) return;
        String js = String.format(Locale.US, "setLocation(%f, %f);", lastLat, lastLng);
        webMapPreview.evaluateJavascript(js, null);
    }

    private void openBiggerMap() {
        // Opens Google Maps externally, centered on the current location (if
        // known) with both hospitals and medical stores in the search.
        Uri uri = lastLat != null && lastLng != null
                ? Uri.parse("geo:" + lastLat + "," + lastLng + "?q=hospitals+and+medical+stores+near+me")
                : Uri.parse("geo:0,0?q=hospitals+and+medical+stores+near+me");

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(getContext(), "No maps app found", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadEmergencyNumbers() {
        adminRepository.getEmergencyNumbers(new AdminRepository.NumbersCallback() {
            @Override
            public void onSuccess(List<FixedEmergencyNumbers.Entry> numbers) {
                if (getContext() == null || recyclerFixedNumbers == null) return;
                List<FixedEmergencyNumbers.Entry> toShow =
                        numbers.isEmpty() ? FixedEmergencyNumbers.getAll() : numbers;
                recyclerFixedNumbers.setAdapter(new FixedNumberAdapter(requireContext(), toShow));
            }

            @Override
            public void onError(String message) {
                if (getContext() == null || recyclerFixedNumbers == null) return;
                recyclerFixedNumbers.setAdapter(
                        new FixedNumberAdapter(requireContext(), FixedEmergencyNumbers.getAll()));
            }
        });
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_contact, null);

        TextInputEditText etName = dialogView.findViewById(R.id.etContactName);
        TextInputEditText etRelation = dialogView.findViewById(R.id.etContactRelation);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etContactPhone);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Emergency Contact")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            MaterialButton btnSave = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {

                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                String relation = etRelation.getText() != null ? etRelation.getText().toString().trim() : "";
                String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    etName.setError("Enter a name");
                    return;
                }

                if (TextUtils.isEmpty(phone)) {
                    etPhone.setError("Enter a phone number");
                    return;
                }

                btnSave.setEnabled(false);

                EmergencyContact contact = new EmergencyContact(name, relation, phone);

                contactRepository.insert(contact, new EmergencyContactRepository.OpCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getContext(), "Contact added", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        loadContacts();
                    }

                    @Override
                    public void onError(String message) {
                        btnSave.setEnabled(true);
                        Toast.makeText(getContext(), "Could not add: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webMapPreview != null) {
            webMapPreview.destroy();
            webMapPreview = null;
        }
        mapPageLoaded = false;
    }
}
