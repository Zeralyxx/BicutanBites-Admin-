package com.example.foodorderingappadmin;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class AdminFragment extends Fragment {

    private FirebaseFirestore db;
    private ChipGroup categoryChipGroup;
    private TextView subtitleItemCount;
    private TextInputEditText searchEditText;
    private ImageButton btnLogout;

    private RecyclerView menuRecyclerView;
    private MenuAdapter menuAdapter;

    // The master list of ALL items retrieved from Firestore
    private List<MenuItem> masterMenuList;
    // The list currently displayed in the RecyclerView (filtered list)
    private List<MenuItem> displayedMenuList;

    // Filtering State
    // Store ONLY the raw category name or "All". Do not store the count.
    private String selectedCategory = "All";
    private String searchText = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup);
        subtitleItemCount = view.findViewById(R.id.subtitleItemCount);
        searchEditText = view.findViewById(R.id.searchEditText);
        btnLogout = view.findViewById(R.id.btnLogout);

        // Setup Logout with Confirmation
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        // Setup "Add Item" Button
        MaterialButton btnAddNewItem = view.findViewById(R.id.btnAddNewItem);
        btnAddNewItem.setOnClickListener(v -> {
            AddMenuItemDialogFragment dialog = new AddMenuItemDialogFragment();
            dialog.show(getParentFragmentManager(), "AddMenuItemDialog");
        });

        // Initialize RecyclerView and Adapter
        menuRecyclerView = view.findViewById(R.id.menuRecyclerView);
        menuRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        masterMenuList = new ArrayList<>();
        displayedMenuList = new ArrayList<>();
        menuAdapter = new MenuAdapter(getContext(), displayedMenuList);
        menuRecyclerView.setAdapter(menuAdapter);

        // Setup Search Listener
        setupSearchListener();

        // Listen to Database Changes
        setupRealtimeUpdates();
    }

    // --- Search Logic ---
    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s.toString().toLowerCase(Locale.getDefault());
                filterMenuList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // --- Data Fetching and Initialization ---
    private void setupRealtimeUpdates() {
        db.collection("menu_items")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Toast.makeText(getContext(), "Error loading data", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (value != null) {
                            masterMenuList.clear(); // Clear the master list
                            Set<String> categories = new HashSet<>();

                            for (QueryDocumentSnapshot doc : value) {
                                MenuItem item = doc.toObject(MenuItem.class);
                                item.setId(doc.getId());
                                masterMenuList.add(item);

                                if (item.getCategory() != null && !item.getCategory().isEmpty()) {
                                    categories.add(item.getCategory());
                                }
                            }

                            // Update Category Chips and then filter the list
                            updateCategoryChips(categories, masterMenuList.size());
                            filterMenuList(); // Apply filtering to the newly fetched data
                        }
                    }
                });
    }

    // --- Filtering Logic (Applied after fetch and on user input) ---
    private void filterMenuList() {
        displayedMenuList.clear();

        // 1. Filter by Category
        List<MenuItem> categoryFilteredList;

        // FIX: The filter must only check the pure category name (selectedCategory already stores pure name)
        if (selectedCategory.equals("All")) {
            categoryFilteredList = new ArrayList<>(masterMenuList);
        } else {
            categoryFilteredList = masterMenuList.stream()
                    .filter(item -> selectedCategory.equalsIgnoreCase(item.getCategory()))
                    .collect(Collectors.toList());
        }

        // 2. Filter by Search Text
        if (searchText.isEmpty()) {
            displayedMenuList.addAll(categoryFilteredList);
        } else {
            String lowerCaseSearch = searchText.toLowerCase(Locale.getDefault());
            for (MenuItem item : categoryFilteredList) {
                if (item.getName().toLowerCase(Locale.getDefault()).contains(lowerCaseSearch) ||
                        item.getDescription().toLowerCase(Locale.getDefault()).contains(lowerCaseSearch)) {
                    displayedMenuList.add(item);
                }
            }
        }

        // Update the item count text
        subtitleItemCount.setText(masterMenuList.size() + " total items • " + (displayedMenuList.size()) + " visible");

        // Notify adapter that data changed
        menuAdapter.notifyDataSetChanged();
    }


    // --- Category Chip Logic ---
    private void updateCategoryChips(Set<String> categories, int totalItems) {
        // Detach listener temporarily to prevent accidental filtering during rebuild
        categoryChipGroup.setOnCheckedStateChangeListener(null);
        categoryChipGroup.removeAllViews();

        // Build "All" chip
        boolean isAllSelected = selectedCategory.equals("All");
        addChip("All (" + totalItems + ")", isAllSelected, "All");

        // Build specific category chips
        for (String category : categories) {
            long count = masterMenuList.stream().filter(item -> category.equals(item.getCategory())).count();
            String fullText = category + " (" + count + ")";
            // FIX: Check selection against the stored raw category name
            boolean isCategorySelected = selectedCategory.equals(category);
            addChip(fullText, isCategorySelected, category);
        }

        // Re-attach the listener
        categoryChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedChipId = checkedIds.get(0);
                Chip checkedChip = group.findViewById(checkedChipId);
                if (checkedChip != null) {
                    // FIX: Store ONLY the raw category name (from the tag)
                    selectedCategory = (String) checkedChip.getTag();
                }
            } else {
                // If selection is somehow cleared, default to "All"
                selectedCategory = "All";
            }
            filterMenuList();
        });

        // After rebuilding and setting the listener, ensure the correct chip is checked
        // This is necessary because setting 'checked' in addChip might not always trigger the visual update immediately
        // if the view hasn't been added to the layout yet.
        if (isAllSelected) {
            // Re-select "All" if it was selected before the rebuild
            Chip allChip = (Chip) categoryChipGroup.findViewWithTag("All");
            if (allChip != null) {
                allChip.setChecked(true);
            }
        } else {
            // Re-select the specific category chip
            Chip selectedChip = (Chip) categoryChipGroup.findViewWithTag(selectedCategory);
            if (selectedChip != null) {
                selectedChip.setChecked(true);
            }
        }
    }

    /**
     * Creates and adds a chip, storing the raw category name in the tag.
     * @param text The display text (e.g., "Burgers (3)").
     * @param isSelected Whether the chip should be checked initially.
     * @param categoryValue The raw category name (e.g., "Burgers" or "All").
     */
    private void addChip(String text, boolean isSelected, String categoryValue) {
        Chip chip = (Chip) LayoutInflater.from(getContext()).inflate(R.layout.chip_filter_template, categoryChipGroup, false);

        // Set text and checkable state
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(isSelected);

        // CRITICAL: We use the raw category value as the tag for easy lookup and filtering
        chip.setTag(categoryValue);

        // Add to the group
        categoryChipGroup.addView(chip);
    }

    // --- Logout Confirmation ---
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of the Admin Dashboard?")
                .setPositiveButton("Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}