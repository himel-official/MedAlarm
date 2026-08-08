package com.example.espmedalarm.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.adapter.EmergencyContactAdapter;
import com.example.espmedalarm.adapter.FixedNumberAdapter;
import com.example.espmedalarm.database.EmergencyContactRepository;
import com.example.espmedalarm.entity.EmergencyContact;
import com.example.espmedalarm.utils.FixedEmergencyNumbers;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * Emergency tab:
 *  1) "Open Map" launches Google Maps (or any installed maps app) centered
 *     on the device's own location with a "hospitals near me" search -
 *     this avoids requiring a Google Maps SDK API key/billing setup just
 *     to show nearby hospitals.
 *  2) Emergency Contacts (family & friends), stored per-account in
 *     Firestore via EmergencyContactRepository, same pattern as medicines.
 *  3) Fixed emergency numbers (National Emergency, Ambulance, etc.) -
 *     see FixedEmergencyNumbers.
 */
public class EmergencyFragment extends Fragment {

    private final EmergencyContactRepository contactRepository = new EmergencyContactRepository();

    private RecyclerView recyclerContacts;
    private RecyclerView recyclerFixedNumbers;
    private View txtContactsEmpty;

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

        recyclerContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerFixedNumbers.setLayoutManager(new LinearLayoutManager(requireContext()));

        recyclerFixedNumbers.setAdapter(
                new FixedNumberAdapter(requireContext(), FixedEmergencyNumbers.getAll()));

        view.findViewById(R.id.btnFindHospitals).setOnClickListener(v ->
                openNearbyPlaces("hospitals+near+me"));
        view.findViewById(R.id.btnFindPharmacies).setOnClickListener(v ->
                openNearbyPlaces("medical+store+pharmacy+near+me"));
        view.findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactDialog());
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
}
