package com.example.espmedalarm.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.database.EmergencyContactRepository;
import com.example.espmedalarm.entity.EmergencyContact;

import java.util.List;

/**
 * Lists the user's saved emergency contacts (family & friends), with a
 * tap-to-call action and a delete action. Backed by
 * EmergencyContactRepository (Firestore).
 */
public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder> {

    private final Context context;
    private final List<EmergencyContact> contacts;
    private final EmergencyContactRepository repository = new EmergencyContactRepository();

    public EmergencyContactAdapter(Context context, List<EmergencyContact> contacts) {
        this.context = context;
        this.contacts = contacts;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmergencyContact contact = contacts.get(position);

        holder.txtName.setText(contact.name);
        holder.txtRelation.setText(
                (contact.relation == null || contact.relation.isEmpty()) ? "Contact" : contact.relation);
        holder.txtPhone.setText(contact.phone);

        holder.btnCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact.phone));
            context.startActivity(intent);
        });

        holder.btnDelete.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setTitle("Remove Contact")
                .setMessage("Remove \"" + contact.name + "\" from emergency contacts?")
                .setPositiveButton("Remove", (dialog, which) -> repository.delete(contact,
                        new EmergencyContactRepository.OpCallback() {
                            @Override
                            public void onSuccess() {
                                int pos = holder.getAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) {
                                    contacts.remove(pos);
                                    notifyItemRemoved(pos);
                                }
                            }

                            @Override
                            public void onError(String message) {
                                android.widget.Toast.makeText(context,
                                        "Could not remove: " + message,
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show());
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtRelation, txtPhone;
        ImageButton btnCall, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtContactName);
            txtRelation = itemView.findViewById(R.id.txtContactRelation);
            txtPhone = itemView.findViewById(R.id.txtContactPhone);
            btnCall = itemView.findViewById(R.id.btnCallContact);
            btnDelete = itemView.findViewById(R.id.btnDeleteContact);
        }
    }
}
