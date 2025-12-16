package com.example.foodorderingappadmin;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SalesOrdersFragment extends Fragment {

    private RecyclerView recyclerView;
    private SimpleOrderAdapter adapter;
    private final List<Order> orderList = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales_list, container, false);

        // Initialize RecyclerView and Firestore
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SimpleOrderAdapter(orderList);
        recyclerView.setAdapter(adapter);
        db = FirebaseFirestore.getInstance();

        return view;
    }

    // Main method called by the parent SalesFragment to apply date range filter
    public void updateFilter(Date start, Date end) {
        if (!isAdded() || db == null) return;

        Query query = db.collection("orders")
                .orderBy("orderedAt", Query.Direction.DESCENDING);

        // Apply date range filter to the query
        if (start != null) {
            query = query.whereGreaterThanOrEqualTo("orderedAt", start)
                    .whereLessThanOrEqualTo("orderedAt", end);
        }

        query.get().addOnSuccessListener(snapshots -> {
            if (!isAdded()) return;

            List<Order> incomingOrders = new ArrayList<>();
            List<Task<Void>> userFetchTasks = new ArrayList<>();

            // Step 1: Filter to get ONLY historical (Completed/Cancelled) orders
            for (QueryDocumentSnapshot doc : snapshots) {
                Order order = doc.toObject(Order.class);
                String status = order.getStatus();

                // CLIENT-SIDE FILTER: Only show orders marked as completed or cancelled
                if ("Completed".equalsIgnoreCase(status) || "Cancelled".equalsIgnoreCase(status)) {

                    order.setOrderId(doc.getId());
                    incomingOrders.add(order);

                    // Initiate asynchronous fetch for customer name
                    String userId = order.getUserId();
                    if (userId != null && !userId.isEmpty()) {
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
            }

            // Step 2: Wait for all customer names to be fetched before updating UI
            Tasks.whenAll(userFetchTasks).addOnCompleteListener(task -> {
                if (!isAdded()) return;

                orderList.clear();
                orderList.addAll(incomingOrders);
                if (adapter != null) adapter.notifyDataSetChanged();
            });

        }).addOnFailureListener(e -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Error loading history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Nested Adapter for displaying historical order summaries
    private static class SimpleOrderAdapter extends RecyclerView.Adapter<SimpleOrderAdapter.ViewHolder> {
        final List<Order> list;
        SimpleOrderAdapter(List<Order> l) { list = l; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sales_order_detailed, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Order item = list.get(position);
            String id = item.getOrderId();

            // Bind order details
            holder.txtOrderId.setText("#" + (id.length() > 8 ? id.substring(0, 8) : id));
            holder.txtTotal.setText(String.format("₱%.2f", item.getTotal()));
            holder.txtStatus.setText(item.getStatus());

            // Format and display date
            if (item.getOrderedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                holder.txtDate.setText(sdf.format(item.getOrderedAt()));
            }

            // Display pre-fetched customer name or a fallback
            holder.txtCustomerName.setText(item.getCustomerNameForSearch() != null ? item.getCustomerNameForSearch() : item.getUserId() != null ? item.getUserId().substring(0, 8) + "..." : "Guest");

            // Display a summary of the first item
            holder.txtFirstItem.setText(getFirstItemText(item.getItems()));

            // Apply color styling to the status badge
            applyStatusStyle(holder, item.getStatus());
        }

        // Utility to format a string summarizing the first item and item count
        private static String getFirstItemText(List<Map<String, Object>> items) {
            if (items == null || items.isEmpty()) return "No Items";
            Map<String, Object> firstItem = items.get(0);
            long qty = 1;
            if (firstItem.get("qty") instanceof Long) qty = (Long) firstItem.get("qty");
            else if (firstItem.get("qty") instanceof Integer) qty = (Integer) firstItem.get("qty");

            String name = (String) firstItem.get("name");

            if (items.size() > 1) {
                return String.format("%dx %s (+%d others)", qty, name, items.size() - 1);
            }
            return String.format("%dx %s", qty, name);
        }

        // Applies specific text and background colors based on status (Completed/Cancelled)
        private void applyStatusStyle(ViewHolder holder, String status) {
            int textColor;
            int bgColor;

            if (status != null) {
                switch (status) {
                    case "Cancelled":
                        textColor = Color.RED; bgColor = Color.parseColor("#FEE2E2"); break;
                    case "Completed":
                        textColor = Color.parseColor("#03543F"); bgColor = Color.parseColor("#DEF7EC"); break;
                    default:
                        textColor = Color.GRAY; bgColor = Color.LTGRAY; break;
                }
            } else {
                textColor = Color.GRAY; bgColor = Color.LTGRAY;
            }

            holder.txtStatus.setTextColor(textColor);
            holder.txtStatus.setBackgroundColor(bgColor);
        }


        @Override public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtOrderId, txtDate, txtTotal, txtStatus, txtCustomerName, txtFirstItem;
            ViewHolder(View v) {
                super(v);
                txtOrderId = v.findViewById(R.id.txtOrderId);
                txtDate = v.findViewById(R.id.txtDate);
                txtTotal = v.findViewById(R.id.txtTotal);
                txtStatus = v.findViewById(R.id.txtStatus);
                txtCustomerName = v.findViewById(R.id.txtCustomerName);
                txtFirstItem = v.findViewById(R.id.txtFirstItem);
            }
        }
    }
}