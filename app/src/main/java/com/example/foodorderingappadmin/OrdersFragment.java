package com.example.foodorderingappadmin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class OrdersFragment extends Fragment {

    private static final String TAG = "OrdersFragment";

    private RecyclerView ordersRecyclerView;
    private AdminOrderAdapter orderAdapter;
    private List<Order> masterOrderList;
    private List<Order> displayedOrderList;
    private FirebaseFirestore db;
    private ChipGroup filterChipGroup;
    private TextInputEditText searchEditText;

    // Listener object to manage the Firestore subscription
    private ListenerRegistration orderListenerRegistration;

    // FIX: Removed private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String selectedStatus = "All";
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
    }

    // Start listening when the fragment becomes visible
    @Override
    public void onStart() {
        super.onStart();
        fetchOrders();
    }

    // Stop listening when the fragment goes into the background
    @Override
    public void onStop() {
        super.onStop();
        if (orderListenerRegistration != null) {
            // Unsubscribe from Firestore updates
            orderListenerRegistration.remove();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // FIX: No executor shutdown needed since the field was removed.
    }

    private void fetchOrders() {
        if (orderListenerRegistration != null) {
            orderListenerRegistration.remove();
        }

        Query query = db.collection("orders")
                .whereNotIn("status", Arrays.asList("Completed", "Cancelled"))
                .orderBy("orderedAt", Query.Direction.DESCENDING);

        orderListenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                if (!isAdded() || getContext() == null) {
                    return;
                }

                if (error != null) {
                    Log.e(TAG, "Listen failed for orders: ", error);
                    Toast.makeText(getContext(), "Failed to get real-time orders: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }

                if (value != null) {
                    List<Order> incomingOrders = new ArrayList<>();
                    List<Task<Void>> userFetchTasks = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : value) {
                        Order order = doc.toObject(Order.class);
                        order.setOrderId(doc.getId());
                        incomingOrders.add(order);

                        String userId = order.getUserId();
                        if (userId != null && !userId.isEmpty()) {
                            // FIX: Removed custom executor from continueWith.
                            // The task now uses the GMS Task internal background pool.
                            Task<Void> userTask = db.collection("users").document(userId).get()
                                    .continueWith((Task<DocumentSnapshot> task) -> {
                                        if (task.isSuccessful() && task.getResult().exists()) {
                                            String name = task.getResult().getString("name");
                                            order.setCustomerNameForSearch(name);
                                        }
                                        return null;
                                    });
                            userFetchTasks.add(userTask);
                        }
                    }

                    // The Tasks.whenAll().addOnCompleteListener() runs on the Main Thread by default,
                    // which is correct for updating the UI.
                    Tasks.whenAll(userFetchTasks).addOnCompleteListener(task -> {
                        if (!isAdded()) return;

                        masterOrderList.clear();
                        masterOrderList.addAll(incomingOrders);

                        // This call executes on the Main Thread, resolving the crash.
                        updateChipCountsAndFilter(masterOrderList);
                    });
                }
            }
        });
    }

    private void updateChipCountsAndFilter(List<Order> orders) {
        if (getView() == null) {
            return;
        }

        int pendingCount = 0;
        int madeCount = 0;
        int deliveredCount = 0;

        String currentRawStatus = selectedStatus;

        for (Order order : orders) {
            switch (order.getStatus()) {
                case "Pending": pendingCount++; break;
                case "Being Made": madeCount++; break;
                case "Being Delivered": deliveredCount++; break;
            }
        }

        updateChipCount(R.id.chipAll, orders.size(), currentRawStatus, "All");
        updateChipCount(R.id.chipPending, pendingCount, currentRawStatus, "Pending");
        updateChipCount(R.id.chipBeingMade, madeCount, currentRawStatus, "Being Made");
        updateChipCount(R.id.chipDelivering, deliveredCount, currentRawStatus, "Being Delivered");

        filterOrderList();
    }

    private void updateChipCount(int chipId, int count, String currentRawStatus, String dbStatusName) {
        if (getView() == null) return;
        Chip chip = getView().findViewById(chipId);
        if (chip != null) {
            String currentText = chip.getText().toString().split(" ")[0];
            chip.setText(currentText + " (" + count + ")");

            if (dbStatusName.equalsIgnoreCase(currentRawStatus)) {
                filterChipGroup.check(chipId);
            }
        }
    }

    private void setupStatusChipListener() {
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                group.check(R.id.chipAll);
                return;
            }

            Chip checkedChip = group.findViewById(checkedIds.get(0));
            if (checkedChip != null) {
                int id = checkedChip.getId();
                if (id == R.id.chipAll) selectedStatus = "All";
                else if (id == R.id.chipPending) selectedStatus = "Pending";
                else if (id == R.id.chipBeingMade) selectedStatus = "Being Made";
                else if (id == R.id.chipDelivering) selectedStatus = "Being Delivered";
                else selectedStatus = "All";
            } else {
                selectedStatus = "All";
            }

            filterOrderList();
        });

        filterChipGroup.check(R.id.chipAll);
    }

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

    private void filterOrderList() {
        if (getView() == null) {
            return;
        }

        displayedOrderList.clear();

        String rawStatus = selectedStatus;
        boolean filterByStatus = !rawStatus.equalsIgnoreCase("All");

        List<Order> statusFilteredList;
        if (filterByStatus) {
            statusFilteredList = masterOrderList.stream()
                    .filter(order -> rawStatus.equalsIgnoreCase(order.getStatus()))
                    .collect(Collectors.toList());
        } else {
            statusFilteredList = masterOrderList;
        }

        if (searchText.isEmpty()) {
            displayedOrderList.addAll(statusFilteredList);
        } else {
            String finalSearchText = searchText.toLowerCase(Locale.getDefault());

            for (Order order : statusFilteredList) {
                String id = order.getOrderId().toLowerCase(Locale.getDefault());
                String note = order.getNote() != null ? order.getNote().toLowerCase(Locale.getDefault()) : "";

                String customerName = order.getCustomerNameForSearch() != null ? order.getCustomerNameForSearch().toLowerCase(Locale.getDefault()) : "";

                if (id.contains(finalSearchText) || note.contains(finalSearchText) || customerName.contains(finalSearchText)) {
                    displayedOrderList.add(order);
                }
            }
        }

        orderAdapter.notifyDataSetChanged();
    }
}