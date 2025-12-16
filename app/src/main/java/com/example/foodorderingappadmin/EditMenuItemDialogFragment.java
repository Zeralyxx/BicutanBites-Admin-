package com.example.foodorderingappadmin;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditMenuItemDialogFragment extends DialogFragment {

    private TextInputEditText etName, etDescription, etPrice, etImage;
    private AutoCompleteTextView actCategory;
    private MaterialSwitch switchAvailability;
    private FirebaseFirestore db;
    private MenuItem menuItemToEdit;

    // Static factory method to pass the MenuItem data to the dialog.
    public static EditMenuItemDialogFragment newInstance(MenuItem item) {
        EditMenuItemDialogFragment fragment = new EditMenuItemDialogFragment();
        fragment.menuItemToEdit = item;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_add_menu_item, container, false);

        // Configure Dialog appearance: transparent background and no title.
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        db = FirebaseFirestore.getInstance();

        // Initialize Views
        TextView dialogTitle = view.findViewById(R.id.dialogTitle);
        etName = view.findViewById(R.id.etName);
        etDescription = view.findViewById(R.id.etDescription);
        etPrice = view.findViewById(R.id.etPrice);
        actCategory = view.findViewById(R.id.actCategory);
        etImage = view.findViewById(R.id.etImage);
        switchAvailability = view.findViewById(R.id.switchAvailability);
        MaterialButton btnUpdate = view.findViewById(R.id.btnAdd);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        ImageButton btnClose = view.findViewById(R.id.btnClose);

        // --- Customization for Edit Mode ---
        dialogTitle.setText("Edit Menu Item");
        btnUpdate.setText("Update");

        // Pre-fill existing data into the form fields.
        if (menuItemToEdit != null) {
            etName.setText(menuItemToEdit.getName());
            etDescription.setText(menuItemToEdit.getDescription());
            etPrice.setText(String.valueOf(menuItemToEdit.getPrice()));
            actCategory.setText(menuItemToEdit.getCategory());
            etImage.setText(menuItemToEdit.getImageUrl());
            switchAvailability.setChecked(menuItemToEdit.isAvailable());
        }

        // Setup Category Dropdown options.
        String[] categories = {
                "Sandwiches & Rolls",
                "Sizzling Specials",
                "Pasta & Noodles",
                "Chicken & Meats",
                "Appetizers/Sides",
                "Noodle Soup",
                "Filipino Classics",
                "Desserts",
                "Beverages"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories);
        actCategory.setAdapter(adapter);

        // Set action listeners.
        btnClose.setOnClickListener(v -> dismiss());
        btnCancel.setOnClickListener(v -> dismiss());
        btnUpdate.setOnClickListener(v -> updateItemInFirebase());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Set dialog width to match parent.
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void updateItemInFirebase() {
        String name = etName.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String category = actCategory.getText().toString().trim();
        String imageUrl = etImage.getText().toString().trim();
        boolean isAvailable = switchAvailability.isChecked();

        // Validation check for required fields.
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(priceStr)) {
            Toast.makeText(getContext(), "Name and Price are required", Toast.LENGTH_SHORT).show();
            return;
        }

        double price = Double.parseDouble(priceStr);

        // Prepare fields for Firestore update.
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("description", desc);
        updates.put("price", price);
        updates.put("category", category);
        updates.put("imageUrl", imageUrl);
        updates.put("available", isAvailable);

        // Execute the Firestore update using the item's document ID.
        if (menuItemToEdit != null && menuItemToEdit.getId() != null) {
            db.collection("menu_items").document(menuItemToEdit.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Item updated", Toast.LENGTH_SHORT).show();
                        dismiss();
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }
}