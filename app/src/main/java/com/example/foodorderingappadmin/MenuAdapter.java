package com.example.foodorderingappadmin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity; // Required for showing DialogFragment
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.MenuViewHolder> {

    private Context context;
    private List<MenuItem> menuList;
    private FirebaseFirestore db;

    public MenuAdapter(Context context, List<MenuItem> menuList) {
        this.context = context;
        this.menuList = menuList;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public MenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_menu, parent, false);
        return new MenuViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MenuViewHolder holder, int position) {
        MenuItem item = menuList.get(position);

        holder.itemName.setText(item.getName());
        holder.itemDescription.setText(item.getDescription());
        holder.itemPrice.setText(String.format("$%.2f", item.getPrice()));
        holder.itemCategoryChip.setText(item.getCategory());

        // Load Image using Glide
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(item.getImageUrl())
                    .placeholder(android.R.color.darker_gray)
                    .into(holder.itemImage);
        }

        // --- OUT OF STOCK VISUALS ---
        if (!item.isAvailable()) {
            // Gray out the item if out of stock
            holder.itemImage.setAlpha(0.4f); // Dim image
            holder.itemName.setTextColor(Color.GRAY);
            holder.itemDescription.setTextColor(Color.LTGRAY);
            holder.itemPrice.setText("Out of Stock");
            holder.itemPrice.setTextColor(Color.RED);
        } else {
            // Reset to normal
            holder.itemImage.setAlpha(1.0f);
            holder.itemName.setTextColor(Color.parseColor("#111827")); // Dark Gray/Black
            holder.itemDescription.setTextColor(Color.parseColor("#6B7280")); // Gray
            holder.itemPrice.setText(String.format("$%.2f", item.getPrice()));
            holder.itemPrice.setTextColor(Color.parseColor("#111827"));
        }

        // --- DELETE CONFIRMATION ---
        holder.deleteButton.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Item")
                    .setMessage("Are you sure you want to delete " + item.getName() + "?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (item.getId() != null) {
                            db.collection("menu_items").document(item.getId())
                                    .delete()
                                    .addOnSuccessListener(aVoid -> Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(context, "Error deleting", Toast.LENGTH_SHORT).show());
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // --- EDIT FUNCTION ---
        holder.editButton.setOnClickListener(v -> {
            if (context instanceof FragmentActivity) {
                // Pass the item to the Edit Dialog
                EditMenuItemDialogFragment dialog = EditMenuItemDialogFragment.newInstance(item);
                dialog.show(((FragmentActivity) context).getSupportFragmentManager(), "EditMenuItemDialog");
            }
        });
    }

    @Override
    public int getItemCount() {
        return menuList.size();
    }

    public static class MenuViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView itemImage;
        TextView itemName, itemDescription, itemPrice;
        Chip itemCategoryChip;
        ImageButton editButton, deleteButton;

        public MenuViewHolder(@NonNull View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.itemImage);
            itemName = itemView.findViewById(R.id.itemName);
            itemDescription = itemView.findViewById(R.id.itemDescription);
            itemPrice = itemView.findViewById(R.id.itemPrice);
            itemCategoryChip = itemView.findViewById(R.id.itemCategoryChip);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}