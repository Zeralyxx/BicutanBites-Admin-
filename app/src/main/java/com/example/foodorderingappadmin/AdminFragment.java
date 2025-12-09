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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AdminFragment extends Fragment {

    private FirebaseFirestore db;
    private ChipGroup categoryChipGroup;
    private TextView subtitleItemCount;
    private TextInputEditText searchEditText;
    private ImageButton btnLogout;

    private RecyclerView menuRecyclerView;
    private MenuAdapter menuAdapter;

    private List<MenuItem> masterMenuList;
    private List<MenuItem> displayedMenuList;

    // Executor for background tasks
    private ExecutorService executor = Executors.newSingleThreadExecutor();

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

        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        MaterialButton btnAddNewItem = view.findViewById(R.id.btnAddNewItem);
        btnAddNewItem.setOnClickListener(v -> {
            AddMenuItemDialogFragment dialog = new AddMenuItemDialogFragment();
            dialog.show(getParentFragmentManager(), "AddMenuItemDialog");
        });

        menuRecyclerView = view.findViewById(R.id.menuRecyclerView);
        menuRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        masterMenuList = new ArrayList<>();
        displayedMenuList = new ArrayList<>();
        menuAdapter = new MenuAdapter(getContext(), displayedMenuList);
        menuRecyclerView.setAdapter(menuAdapter);

        setupSearchListener();
        setupRealtimeUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow(); // IMPORTANT: Shut down the executor when the fragment is destroyed
    }


    // --- Data Fetching and Initialization ---
    private void setupRealtimeUpdates() {
        db.collection("menu_items")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }

                        if (error != null) {
                            Toast.makeText(getContext(), "Error loading data", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (value != null) {
                            final List<MenuItem> incomingItems = new ArrayList<>();

                            // Phase 1: Build Item list (fast loop)
                            for (QueryDocumentSnapshot doc : value) {
                                MenuItem item = doc.toObject(MenuItem.class);
                                item.setId(doc.getId());
                                incomingItems.add(item);
                            }

                            // Phase 2: Offload heavy processing (counting/categorizing)
                            executor.execute(() -> {
                                final Set<String> categories = new HashSet<>();
                                // Perform the category counting and filtering logic in the background
                                for (MenuItem item : incomingItems) {
                                    if (item.getCategory() != null && !item.getCategory().isEmpty()) {
                                        categories.add(item.getCategory());
                                    }
                                }

                                // Phase 3: Update UI on main thread
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (!isAdded()) return;

                                        masterMenuList.clear();
                                        masterMenuList.addAll(incomingItems);

                                        updateCategoryChips(categories, masterMenuList.size());
                                        filterMenuList();
                                    });
                                }
                            });
                        }
                    }
                });
    }

    // --- Search Logic ---
    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s.toString().toLowerCase(Locale.getDefault());
                filterMenuList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // --- Filtering Logic ---
    private void filterMenuList() {
        if (getView() == null) {
            return;
        }

        displayedMenuList.clear();
        // ... (Filtering logic remains the same) ...

        List<MenuItem> categoryFilteredList;

        if (selectedCategory.equals("All")) {
            categoryFilteredList = new ArrayList<>(masterMenuList);
        } else {
            categoryFilteredList = masterMenuList.stream()
                    .filter(item -> selectedCategory.equalsIgnoreCase(item.getCategory()))
                    .collect(Collectors.toList());
        }

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

        subtitleItemCount.setText(masterMenuList.size() + " total items • " + (displayedMenuList.size()) + " visible");
        menuAdapter.notifyDataSetChanged();
    }


    // --- Chip Logic ---
    private void updateCategoryChips(Set<String> categories, int totalItems) {
        if (getView() == null) {
            return;
        }
        // ... (Chip building logic remains the same) ...

        categoryChipGroup.setOnCheckedStateChangeListener(null);
        categoryChipGroup.removeAllViews();

        boolean isAllSelected = selectedCategory.equals("All");
        addChip("All (" + totalItems + ")", isAllSelected, "All");

        for (String category : categories) {
            long count = masterMenuList.stream().filter(item -> category.equals(item.getCategory())).count();
            String fullText = category + " (" + count + ")";
            boolean isCategorySelected = selectedCategory.equals(category);
            addChip(fullText, isCategorySelected, category);
        }

        categoryChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip checkedChip = group.findViewById(checkedIds.get(0));
                if (checkedChip != null) {
                    selectedCategory = (String) checkedChip.getTag();
                }
            } else {
                selectedCategory = "All";
            }
            filterMenuList();
        });

        if (isAllSelected) {
            Chip allChip = (Chip) categoryChipGroup.findViewWithTag("All");
            if (allChip != null) {
                allChip.setChecked(true);
            }
        } else {
            Chip selectedChip = (Chip) categoryChipGroup.findViewWithTag(selectedCategory);
            if (selectedChip != null) {
                selectedChip.setChecked(true);
            }
        }
    }

    private void addChip(String text, boolean isSelected, String categoryValue) {
        if (getContext() == null) return;

        Chip chip = (Chip) LayoutInflater.from(getContext()).inflate(R.layout.chip_filter_template, categoryChipGroup, false);

        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(isSelected);
        chip.setTag(categoryValue);

        categoryChipGroup.addView(chip);
    }

    // --- Logout Confirmation ---
    private void showLogoutConfirmationDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of the Admin Dashboard?")
                .setPositiveButton("Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logoutUser() {
        if (isAdded()) {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }
}