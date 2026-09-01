package com.example.espmedalarm.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.espmedalarm.R;
import com.example.espmedalarm.entity.AdminUser;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** Registered-users list shown in the Admin Panel, with a Remove action per user. */
public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    public interface Listener {
        void onRemove(AdminUser user);
    }

    private static final long ACTIVE_WINDOW_MILLIS = 24L * 60 * 60 * 1000;

    private final List<AdminUser> users;
    private final Listener listener;

    public AdminUserAdapter(List<AdminUser> users, Listener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminUser user = users.get(position);

        holder.txtEmail.setText(user.email != null ? user.email : user.uid);

        String statusText;
        int statusColor;
        if (user.disabled) {
            statusText = "Removed";
            statusColor = holder.itemView.getContext().getColor(R.color.danger_red);
        } else {
            boolean active = user.lastLoginAtMillis > 0
                    && (System.currentTimeMillis() - user.lastLoginAtMillis) <= ACTIVE_WINDOW_MILLIS;
            String lastLogin = user.lastLoginAtMillis > 0
                    ? DateUtils.getRelativeTimeSpanString(user.lastLoginAtMillis).toString()
                    : "never logged in";
            statusText = (active ? "Active" : "Inactive") + " · last login " + lastLogin;
            statusColor = holder.itemView.getContext().getColor(
                    active ? R.color.accent_green : R.color.text_secondary);
        }
        holder.txtStatus.setText(statusText);
        holder.txtStatus.setTextColor(statusColor);

        holder.btnRemove.setEnabled(!user.disabled);
        holder.btnRemove.setText(user.disabled ? "Removed" : "Remove");
        holder.btnRemove.setOnClickListener(v -> listener.onRemove(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtEmail, txtStatus;
        MaterialButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtEmail = itemView.findViewById(R.id.txtAdminUserEmail);
            txtStatus = itemView.findViewById(R.id.txtAdminUserStatus);
            btnRemove = itemView.findViewById(R.id.btnRemoveUser);
        }
    }
}
