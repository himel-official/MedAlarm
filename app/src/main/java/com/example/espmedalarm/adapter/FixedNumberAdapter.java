package com.example.espmedalarm.adapter;

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
import com.example.espmedalarm.utils.FixedEmergencyNumbers;

import java.util.List;

/**
 * Lists fixed (non-editable) emergency service numbers - hospital,
 * ambulance, police, national emergency, etc.
 */
public class FixedNumberAdapter extends RecyclerView.Adapter<FixedNumberAdapter.ViewHolder> {

    private final Context context;
    private final List<FixedEmergencyNumbers.Entry> entries;

    public FixedNumberAdapter(Context context, List<FixedEmergencyNumbers.Entry> entries) {
        this.context = context;
        this.entries = entries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fixed_number, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FixedEmergencyNumbers.Entry entry = entries.get(position);

        holder.txtLabel.setText(entry.label);
        holder.txtSubtitle.setText(entry.subtitle);
        holder.txtNumber.setText(entry.number);

        holder.btnCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + entry.number));
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtLabel, txtSubtitle, txtNumber;
        ImageButton btnCall;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtLabel = itemView.findViewById(R.id.txtNumberLabel);
            txtSubtitle = itemView.findViewById(R.id.txtNumberSubtitle);
            txtNumber = itemView.findViewById(R.id.txtNumberValue);
            btnCall = itemView.findViewById(R.id.btnCallNumber);
        }
    }
}
