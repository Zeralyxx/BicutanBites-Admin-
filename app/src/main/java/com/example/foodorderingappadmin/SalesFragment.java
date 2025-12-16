package com.example.foodorderingappadmin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SalesFragment extends Fragment {

    private TextView valueRevenue, valueOrders, valueItemsSold, valueCustomers;
    private TextView currentRangeLabel;
    private ChipGroup timeFilterGroup;
    private TabLayout segmentTabs;
    private FirebaseFirestore db;

    // Current filter state for child fragments
    private Date startDate;
    private Date endDate;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sales, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        // Initialize primary sales metric views
        valueRevenue = view.findViewById(R.id.valueRevenue);
        valueOrders = view.findViewById(R.id.valueOrders);
        valueItemsSold = view.findViewById(R.id.valueItemsSold);
        valueCustomers = view.findViewById(R.id.valueCustomers);
        currentRangeLabel = view.findViewById(R.id.currentRangeLabel);
        timeFilterGroup = view.findViewById(R.id.timeFilterGroup);
        segmentTabs = view.findViewById(R.id.segmentTabs);

        // Setup UI listeners and initial load
        setupTimeFilters();
        setupTabs();
        setFilterAllTime(); // Default to 'All Time' on startup
    }

    // --- Time Filter Logic ---

    private void setupTimeFilters() {
        // Listener to handle time filter chip selection (Week, Month, Year, All Time)
        timeFilterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);

            if (id == R.id.chipWeek) {
                setFilterWeek();
            } else if (id == R.id.chipMonth) {
                setFilterMonth();
            } else if (id == R.id.chipYear) {
                setFilterYear();
            } else {
                setFilterAllTime();
            }
        });
    }

    // Methods to calculate and apply the date range filter
    private void setFilterWeek() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        updateData(cal.getTime(), new Date(), "Last 7 Days");
    }

    private void setFilterMonth() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        updateData(cal.getTime(), new Date(), "Last 30 Days");
    }

    private void setFilterYear() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        updateData(cal.getTime(), new Date(), "Last Year");
    }

    private void setFilterAllTime() {
        updateData(null, null, "All Time");
    }

    // --- Data Aggregation and UI Update ---

    private void updateData(@Nullable Date start, @Nullable Date end, String label) {
        this.startDate = start;
        this.endDate = end;
        currentRangeLabel.setText(label);

        Query query = db.collection("orders");

        // Apply date filtering to the Firestore query
        if (start != null) {
            query = query.whereGreaterThanOrEqualTo("orderedAt", start)
                    .whereLessThanOrEqualTo("orderedAt", end);
        }

        // Fetch orders once for the current date range
        query.get().addOnSuccessListener(snapshots -> {
            // CRITICAL: Check if fragment is still attached before updating UI
            if (!isAdded() || getContext() == null) {
                return;
            }

            double totalRevenue = 0;
            int totalOrders = 0;
            long totalItems = 0;
            Set<String> uniqueCustomers = new HashSet<>();

            // Aggregate metrics from the order snapshots
            for (QueryDocumentSnapshot doc : snapshots) {
                Order order = doc.toObject(Order.class);

                totalRevenue += order.getTotal();
                totalOrders++;
                if (order.getUserId() != null) uniqueCustomers.add(order.getUserId());

                if (order.getItems() != null) {
                    for (Map<String, Object> item : order.getItems()) {
                        Object qtyObj = item.get("qty");
                        if (qtyObj instanceof Long) totalItems += (Long) qtyObj;
                        else if (qtyObj instanceof Integer) totalItems += (Integer) qtyObj;
                    }
                }
            }

            // Update main sales metrics UI
            valueRevenue.setText(String.format("₱%.2f", totalRevenue));
            valueOrders.setText(String.valueOf(totalOrders));
            valueItemsSold.setText(String.valueOf(totalItems));
            valueCustomers.setText(String.valueOf(uniqueCustomers.size()));

            // Refresh the child fragment to update its segmented list view
            refreshCurrentFragment();

        }).addOnFailureListener(e -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Failed to fetch data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- Tab Management ---

    private void setupTabs() {
        segmentTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadFragmentForTab(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Load default fragment into the segment container
        loadFragmentForTab(0);
    }

    private void loadFragmentForTab(int position) {
        Fragment fragment;
        // Select the appropriate child fragment based on tab position
        switch (position) {
            case 0: fragment = new SalesCustomersFragment(); break;
            case 1: fragment = new SalesTopItemsFragment(); break;
            default: fragment = new SalesOrdersFragment(); break;
        }

        // Use commitNow() to ensure the fragment is attached immediately, preventing race conditions
        getChildFragmentManager().beginTransaction()
                .replace(R.id.segmentContainer, fragment)
                .commitNow();

        // Push the currently selected dates to the newly loaded child fragment
        refreshCurrentFragment();
    }

    private void refreshCurrentFragment() {
        // Check if the parent fragment is still active
        if (!isAdded()) {
            return;
        }

        FragmentManager fm = getChildFragmentManager();
        Fragment currentFragment = fm.findFragmentById(R.id.segmentContainer);

        // Dynamically call the updateFilter method on the correct child fragment type
        if (currentFragment instanceof SalesTopItemsFragment) {
            ((SalesTopItemsFragment) currentFragment).updateFilter(startDate, endDate);
        } else if (currentFragment instanceof SalesCustomersFragment) {
            ((SalesCustomersFragment) currentFragment).updateFilter(startDate, endDate);
        } else if (currentFragment instanceof SalesOrdersFragment) {
            ((SalesOrdersFragment) currentFragment).updateFilter(startDate, endDate);
        }
    }
}