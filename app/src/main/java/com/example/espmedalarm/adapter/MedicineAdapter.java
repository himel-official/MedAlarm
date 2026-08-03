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
import com.example.espmedalarm.database.AppDatabase;
import com.example.espmedalarm.entity.Medicine;
import com.example.espmedalarm.utils.AlarmScheduler;

import java.util.List;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.ViewHolder> {

    private final Context context;
    private final List<Medicine> medicineList;

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

        holder.txtName.setText(medicine.name + "\nBox " + medicine.boxNumber);
        holder.txtTime.setText("" + String.join(", ", medicine.times));
        //holder.txtDuration.setText("" + medicine.duration + " Days");
        long today = System.currentTimeMillis();

        long diff = today - medicine.startDate;

        int passedDays = (int) (diff / (1000L * 60 * 60 * 24));

        int remaining = medicine.duration - passedDays;
        if (remaining <= 0) {

            holder.txtDuration.setText("Course Completed");

        } else {

            holder.txtDuration.setText("" + remaining + " day(s) remaining");

        }
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

                        AppDatabase.getInstance(context)
                                .medicineDao()
                                .delete(medicine);

                        int pos = holder.getAdapterPosition();

                        if (pos != RecyclerView.NO_POSITION) {
                            medicineList.remove(pos);
                            notifyItemRemoved(pos);
                        }

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

        TextView txtName, txtTime, txtDuration;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtName = itemView.findViewById(R.id.txtMedicineName);
            txtTime = itemView.findViewById(R.id.txtMedicineTime);
            txtDuration = itemView.findViewById(R.id.txtMedicineDuration);

            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}