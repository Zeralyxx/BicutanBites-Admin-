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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

public class AddMenuItemDialogFragment extends DialogFragment {

    private TextInputEditText etName, etDescription, etPrice, etImage;
    private AutoCompleteTextView actCategory;
    private MaterialSwitch switchAvailability;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_add_menu_item, container, false);

        // Make dialog background transparent to show our CardView rounded corners
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        db = FirebaseFirestore.getInstance();

        // Initialize Views
        etName = view.findViewById(R.id.etName);
        etDescription = view.findViewById(R.id.etDescription);
        etPrice = view.findViewById(R.id.etPrice);
        actCategory = view.findViewById(R.id.actCategory);
        etImage = view.findViewById(R.id.etImage);
        switchAvailability = view.findViewById(R.id.switchAvailability);
        MaterialButton btnAdd = view.findViewById(R.id.btnAdd);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        ImageButton btnClose = view.findViewById(R.id.btnClose);

        // Setup Category Dropdown
        // We provide default suggestions, but the user can type whatever they want
        String[] categories = {"Burgers", "Pizza", "Pasta", "Chicken", "Salads", "Sushi", "Steaks", "Desserts", "Beverages"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories);
        actCategory.setAdapter(adapter);

        // Button Listeners
        btnClose.setOnClickListener(v -> dismiss());
        btnCancel.setOnClickListener(v -> dismiss());
        btnAdd.setOnClickListener(v -> saveItemToFirebase());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make the dialog width match the design (fill width with some margin)
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    private void saveItemToFirebase() {
        String name = etName.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String category = actCategory.getText().toString().trim(); // Gets text even if typed manually
        String imageUrl = etImage.getText().toString().trim();
        boolean isAvailable = switchAvailability.isChecked();

        // Validation
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(priceStr) || TextUtils.isEmpty(category)) {
            Toast.makeText(getContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double price = Double.parseDouble(priceStr);

        // Create Item Object
        MenuItem newItem = new MenuItem(name, desc, price, category, imageUrl, isAvailable);

        // Save to Firestore "menu_items" collection
        db.collection("menu_items")
                .add(newItem)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Item added successfully", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error adding item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}