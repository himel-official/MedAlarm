package com.example.espmedalarm.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.activities.AddMedicineActivity;
import com.example.espmedalarm.activities.MedicineDetailsActivity;
import com.example.espmedalarm.database.MedicineRepository;
import com.example.espmedalarm.entity.Medicine;
import com.example.espmedalarm.utils.AlarmScheduler;
import com.example.espmedalarm.utils.MedicineStatusUtils;

import java.util.List;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.ViewHolder> {

    private final Context context;
    private final List<Medicine> medicineList;
    private final MedicineRepository medicineRepository = new MedicineRepository();

    public MedicineAdapter(Context context, List<Medicine> medicineList) {
        this.context = context;
        this.medicineList = medicineList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_medicine, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Medicine medicine = medicineList.get(position);

        holder.txtName.setText(medicine.name);
        holder.txtBox.setText("Box " + medicine.boxNumber);
        holder.txtTime.setText(String.join(", ", medicine.times));
        holder.txtDuration.setText(MedicineStatusUtils.getDurationLabel(medicine));

        boolean active = MedicineStatusUtils.isActive(medicine);
        boolean expiringSoon = MedicineStatusUtils.isExpiringSoon(medicine);

        if (!active) {
            holder.txtStatus.setText("Expired");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_expired);
            holder.txtStatus.setTextColor(context.getResources().getColor(R.color.danger_red));
        } else if (expiringSoon) {
            holder.txtStatus.setText("Ending soon");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_warning);
            holder.txtStatus.setTextColor(context.getResources().getColor(R.color.warning_orange));
        } else {
            holder.txtStatus.setText("Active");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_active);
            holder.txtStatus.setTextColor(context.getResources().getColor(R.color.accent_green));
        }

        // Open Details on card tap
        holder.itemView.setOnClickListener(v -> {

            Intent intent = new Intent(context, MedicineDetailsActivity.class);
            intent.putExtra("id", medicine.id);
            context.startActivity(intent);
        });

        // EDIT
        holder.btnEdit.setOnClickListener(v -> {

            Intent intent = new Intent(context, AddMedicineActivity.class);

            intent.putExtra("id", medicine.id);
            intent.putExtra("name", medicine.name);
            intent.putStringArrayListExtra("times", new java.util.ArrayList<>(medicine.times));
            intent.putExtra("duration", medicine.duration);
            intent.putExtra("boxNumber", medicine.boxNumber);
            intent.putExtra("startDate", medicine.startDate);

            context.startActivity(intent);

        });

        // DELETE
        holder.btnDelete.setOnClickListener(v -> {

            new AlertDialog.Builder(context)
                    .setTitle("Delete Medicine")
                    .setMessage("Delete \"" + medicine.name + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> {

                        for (String time : medicine.times) {

                            AlarmScheduler.cancelAlarm(
                                    context,
                                    medicine.name,
                                    time
                            );

                        }

                        medicineRepository.delete(medicine, new MedicineRepository.OpCallback() {
                            @Override
                            public void onSuccess() {
                                int pos = holder.getAdapterPosition();

                                if (pos != RecyclerView.NO_POSITION) {
                                    medicineList.remove(pos);
                                    notifyItemRemoved(pos);
                                }
                            }

                            @Override
                            public void onError(String message) {
                                android.widget.Toast.makeText(context,
                                        "Could not delete: " + message,
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        });

                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        });

    }

    @Override
    public int getItemCount() {
        return medicineList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtName, txtTime, txtDuration, txtStatus, txtBox;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtName = itemView.findViewById(R.id.txtMedicineName);
            txtTime = itemView.findViewById(R.id.txtMedicineTime);
            txtDuration = itemView.findViewById(R.id.txtMedicineDuration);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtBox = itemView.findViewById(R.id.txtBox);

            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
