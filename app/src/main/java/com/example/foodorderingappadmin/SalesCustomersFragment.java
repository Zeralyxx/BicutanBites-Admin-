package com.example.foodorderingappadmin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalesCustomersFragment extends Fragment {

    private RecyclerView recyclerView;
    private CustomerAdapter adapter;
    private List<CustomerStat> customerList = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales_list, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CustomerAdapter(customerList);
        recyclerView.setAdapter(adapter);
        db = FirebaseFirestore.getInstance();
        return view;
    }

    public void updateFilter(Date start, Date end) {
        if (db == null) return;

        Query query = db.collection("orders");
        if (start != null) {
            query = query.whereGreaterThanOrEqualTo("orderedAt", start)
                    .whereLessThanOrEqualTo("orderedAt", end);
        }

        query.get().addOnSuccessListener(snapshots -> {
            Map<String, CustomerStat> statsMap = new HashMap<>();

            for (QueryDocumentSnapshot doc : snapshots) {
                Order order = doc.toObject(Order.class);
                String uid = order.getUserId();
                if (uid == null) continue;

                if (!statsMap.containsKey(uid)) {
                    statsMap.put(uid, new CustomerStat(uid));
                }
                CustomerStat stat = statsMap.get(uid);
                stat.totalSpent += order.getTotal();
                stat.orderCount++;
            }

            customerList.clear();
            customerList.addAll(statsMap.values());
            // Sort by Total Spent
            Collections.sort(customerList, (a, b) -> Double.compare(b.totalSpent, a.totalSpent));

            if (adapter != null) adapter.notifyDataSetChanged();
        });
    }

    static class CustomerStat {
        String userId; double totalSpent; int orderCount;
        CustomerStat(String uid) { userId = uid; }
    }

    private class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.ViewHolder> {
        List<CustomerStat> list;
        CustomerAdapter(List<CustomerStat> l) { list = l; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sales_customer, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomerStat item = list.get(position);
            holder.txtTotalSpent.setText(String.format("₱%.2f", item.totalSpent));
            holder.txtOrderCount.setText(item.orderCount + " Orders");

            // Fetch Name (Simple cache-less approach for now)
            db.collection("users").document(item.userId).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    holder.txtCustomerName.setText(name != null ? name : "Unknown");
                }
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtCustomerName, txtOrderCount, txtTotalSpent;
            ViewHolder(View v) {
                super(v);
                txtCustomerName = v.findViewById(R.id.txtCustomerName);
                txtOrderCount = v.findViewById(R.id.txtOrderCount);
                txtTotalSpent = v.findViewById(R.id.txtTotalSpent);
            }
        }
    }
}