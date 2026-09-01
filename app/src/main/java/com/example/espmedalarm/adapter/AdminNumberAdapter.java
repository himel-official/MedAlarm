package com.example.espmedalarm.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.utils.FixedEmergencyNumbers;

import java.util.List;

/**
 * Editable list of emergency numbers shown in the Admin Panel. Edits happen
 * in-memory on the passed-in list; the activity is responsible for
 * persisting via AdminRepository.saveEmergencyNumbers when the admin taps
 * "Save Changes".
 */
public class AdminNumberAdapter extends RecyclerView.Adapter<AdminNumberAdapter.ViewHolder> {

    public interface Listener {
        void onEdit(int position, FixedEmergencyNumbers.Entry entry);
        void onDelete(int position);
    }

    private final List<FixedEmergencyNumbers.Entry> entries;
    private final Listener listener;

    public AdminNumberAdapter(List<FixedEmergencyNumbers.Entry> entries, Listener listener) {
        this.entries = entries;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_number, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FixedEmergencyNumbers.Entry entry = entries.get(position);

        holder.txtLabel.setText(entry.label);
        holder.txtSubtitle.setText(entry.subtitle);
        holder.txtNumber.setText(entry.number);

        holder.btnEdit.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onEdit(pos, entries.get(pos));
        });

        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onDelete(pos);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtLabel, txtSubtitle, txtNumber;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtLabel = itemView.findViewById(R.id.txtAdminNumberLabel);
            txtSubtitle = itemView.findViewById(R.id.txtAdminNumberSubtitle);
            txtNumber = itemView.findViewById(R.id.txtAdminNumberValue);
            btnEdit = itemView.findViewById(R.id.btnEditNumber);
            btnDelete = itemView.findViewById(R.id.btnDeleteNumber);
        }
    }
}
