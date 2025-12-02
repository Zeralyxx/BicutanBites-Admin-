package com.example.foodorderingappadmin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class OrdersFragment extends Fragment {

    private RecyclerView ordersRecyclerView;
    private AdminOrderAdapter orderAdapter;
    private List<Order> masterOrderList;
    private List<Order> displayedOrderList;
    private FirebaseFirestore db;
    private ChipGroup filterChipGroup;
    private TextInputEditText searchEditText;

    private String selectedStatus = "All (9)";
    private String searchText = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        ordersRecyclerView = view.findViewById(R.id.ordersRecyclerView);
        filterChipGroup = view.findViewById(R.id.filterChipGroup);
        searchEditText = view.findViewById(R.id.searchEditText);

        ordersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        masterOrderList = new ArrayList<>();
        displayedOrderList = new ArrayList<>();
        orderAdapter = new AdminOrderAdapter(getContext(), displayedOrderList);
        ordersRecyclerView.setAdapter(orderAdapter);

        setupStatusChipListener();
        setupSearchListener();
        fetchOrders();
    }

    private void fetchOrders() {
        db.collection("orders")
                .orderBy("orderedAt", Query.Direction.DESCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Toast.makeText(getContext(), "Error getting orders", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (value != null) {
                            masterOrderList.clear();
                            List<Task<Void>> userFetchTasks = new ArrayList<>();

                            // Phase 1: Build Order list and initiate user name fetches
                            for (QueryDocumentSnapshot doc : value) {
                                Order order = doc.toObject(Order.class);
                                order.setOrderId(doc.getId());
                                masterOrderList.add(order);

                                // If userId exists, fetch customer name and update the Order object
                                String userId = order.getUserId();
                                if (userId != null && !userId.isEmpty()) {
                                    Task<Void> userTask = db.collection("users").document(userId).get()
                                            .continueWith((Task<DocumentSnapshot> task) -> {
                                                if (task.isSuccessful() && task.getResult().exists()) {
                                                    String name = task.getResult().getString("name");
                                                    // Store the name directly in the Order object for filtering
                                                    order.setCustomerNameForSearch(name);
                                                }
                                                return null;
                                            });
                                    userFetchTasks.add(userTask);
                                }
                            }

                            // Phase 2: Wait for all names to be fetched
                            Tasks.whenAll(userFetchTasks).addOnCompleteListener(task -> {
                                // 3. Update Chip Counts (Counts are based on masterOrderList)
                                updateChipCountsAndFilter(masterOrderList);
                            });
                        }
                    }
                });
    }

    // New helper method to consolidate UI updates and filtering
    private void updateChipCountsAndFilter(List<Order> orders) {
        int pendingCount = 0;
        int madeCount = 0;
        int deliveredCount = 0;

        for (Order order : orders) {
            switch (order.getStatus()) {
                case "Pending": pendingCount++; break;
                case "Being Made": madeCount++; break;
                case "Being Delivered": deliveredCount++; break;
            }
        }

        updateChipCount(R.id.chipAll, orders.size());
        updateChipCount(R.id.chipPending, pendingCount);
        updateChipCount(R.id.chipBeingMade, madeCount);
        updateChipCount(R.id.chipDelivering, deliveredCount);

        filterOrderList();
    }

    // --- Chip/Status Filtering Logic ---

    private void setupStatusChipListener() {
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                group.check(R.id.chipAll);
                return;
            }

            Chip checkedChip = group.findViewById(checkedIds.get(0));
            if (checkedChip != null) {
                selectedStatus = checkedChip.getText().toString();
            } else {
                selectedStatus = "All";
            }

            filterOrderList();
        });
    }

    private void updateChipCount(int chipId, int count) {
        Chip chip = getView().findViewById(chipId);
        if (chip != null) {
            String currentText = chip.getText().toString().split(" ")[0];
            chip.setText(currentText + " (" + count + ")");
        }
    }

    // --- Search Logic ---

    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s.toString().toLowerCase(Locale.getDefault());
                filterOrderList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // --- Core Filtering Function ---

    private void filterOrderList() {
        displayedOrderList.clear();

        String rawStatus = selectedStatus.split(" ")[0].trim();
        boolean filterByStatus = !rawStatus.equalsIgnoreCase("All");

        // 1. Filter by Status (Master List -> Status Filter)
        List<Order> statusFilteredList;
        if (filterByStatus) {
            statusFilteredList = masterOrderList.stream()
                    .filter(order -> rawStatus.equalsIgnoreCase(order.getStatus()))
                    .collect(Collectors.toList());
        } else {
            statusFilteredList = masterOrderList;
        }

        // 2. Filter by Search Text (Status Filter -> Final Display List)
        if (searchText.isEmpty()) {
            displayedOrderList.addAll(statusFilteredList);
        } else {
            String finalSearchText = searchText.toLowerCase(Locale.getDefault());

            for (Order order : statusFilteredList) {
                String id = order.getOrderId().toLowerCase(Locale.getDefault());
                String note = order.getNote() != null ? order.getNote().toLowerCase(Locale.getDefault()) : "";

                // Search against customer name (now available) or order ID/note
                String customerName = order.getCustomerNameForSearch() != null ? order.getCustomerNameForSearch().toLowerCase(Locale.getDefault()) : "";

                if (id.contains(finalSearchText) || note.contains(finalSearchText) || customerName.contains(finalSearchText)) {
                    displayedOrderList.add(order);
                }
            }
        }

        orderAdapter.notifyDataSetChanged();
    }
}