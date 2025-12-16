package com.example.foodorderingappadmin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalesTopItemsFragment extends Fragment {

    private RecyclerView recyclerView;
    private TopItemsAdapter adapter;
    private final List<TopItem> topItemList = new ArrayList<>();
    private FirebaseFirestore db;

    // Cache map to store item names and their corresponding image URLs
    private final Map<String, String> itemImageMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales_list, container, false);

        // Initialize RecyclerView and Adapter
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TopItemsAdapter(topItemList);
        recyclerView.setAdapter(adapter);
        db = FirebaseFirestore.getInstance();

        // Fetch image URLs for all menu items to use for display optimization
        fetchItemImageMap();

        return view;
    }

    // Fetches all menu item images and stores them in a local map
    private void fetchItemImageMap() {
        db.collection("menu_items").get().addOnSuccessListener(snapshots -> {
            itemImageMap.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                String name = doc.getString("name");
                String url = doc.getString("imageUrl");
                if (name != null && url != null) {
                    itemImageMap.put(name, url);
                }
            }
            if (adapter != null) adapter.notifyDataSetChanged();
        });
    }

    // Main function called by the parent SalesFragment to aggregate top selling items
    public void updateFilter(Date start, Date end) {
        if (db == null) return;

        Query query = db.collection("orders");

        // Apply date range filters
        if (start != null) {
            query = query.whereGreaterThanOrEqualTo("orderedAt", start)
                    .whereLessThanOrEqualTo("orderedAt", end);
        }

        query.get().addOnSuccessListener(snapshots -> {
            Map<String, Integer> counts = new HashMap<>();

            // Aggregate item quantities across all filtered orders
            for (QueryDocumentSnapshot doc : snapshots) {
                Order order = doc.toObject(Order.class);
                if (order.getItems() != null) {
                    for (Map<String, Object> item : order.getItems()) {
                        String name = (String) item.get("name");
                        long qty = 1;
                        if (item.get("qty") instanceof Long) qty = (Long) item.get("qty");
                        else if (item.get("qty") instanceof Integer) qty = (Integer) item.get("qty");

                        // Sum quantities for the item name
                        counts.put(name, counts.getOrDefault(name, 0) + (int)qty);
                    }
                }
            }

            // Convert map results into the list model
            topItemList.clear();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                topItemList.add(new TopItem(entry.getKey(), entry.getValue()));
            }

            // Sort items by count (quantity sold) in descending order
            Collections.sort(topItemList, (a, b) -> b.count - a.count);

            if (adapter != null) adapter.notifyDataSetChanged();
        });
    }

    // Data structure to hold item name and its total quantity sold
    static class TopItem {
        String name;
        int count;
        TopItem(String n, int c) { name = n; count = c; }
    }

    // RecyclerView Adapter for displaying top items
    private class TopItemsAdapter extends RecyclerView.Adapter<TopItemsAdapter.ViewHolder> {
        final List<TopItem> list;
        TopItemsAdapter(List<TopItem> l) { list = l; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sales_top_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TopItem item = list.get(position);

            // Bind rank, name, and quantity sold
            holder.txtRank.setText("#" + (position + 1));
            holder.txtItemName.setText(item.name);
            holder.txtQuantity.setText(String.valueOf(item.count));

            // Load Image using the pre-fetched cache map
            String imageUrl = itemImageMap.get(item.name);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_food_placeholder)
                        .into(holder.itemImage);
            } else {
                holder.itemImage.setImageResource(R.drawable.ic_food_placeholder);
            }
        }

        @Override public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtRank, txtItemName, txtQuantity;
            ImageView itemImage;

            ViewHolder(View v) {
                super(v);
                // Bind UI components
                txtRank = v.findViewById(R.id.txtRank);
                txtItemName = v.findViewById(R.id.txtItemName);
                txtQuantity = v.findViewById(R.id.txtQuantity);
                itemImage = v.findViewById(R.id.itemImage);
            }
        }
    }
}