package com.example.espmedalarm.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.espmedalarm.R;
import com.example.espmedalarm.activities.AdminLoginActivity;
import com.example.espmedalarm.activities.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Profile tab: shows the signed-in account's email and lets the user
 * log out. Logging out clears the Firebase session and returns to
 * LoginActivity with a fresh task, so the back button can't return
 * into the signed-out app.
 */
public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView txtEmail = view.findViewById(R.id.txtEmail);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        MaterialButton btnAdminPanel = view.findViewById(R.id.btnAdminPanel);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        txtEmail.setText(user != null && user.getEmail() != null ? user.getEmail() : "Signed in");

        btnAdminPanel.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AdminLoginActivity.class)));

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }
}
