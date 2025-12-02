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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SalesOrdersFragment extends Fragment {

    private RecyclerView recyclerView;
    private SimpleOrderAdapter adapter;
    private List<Order> orderList = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales_list, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SimpleOrderAdapter(orderList);
        recyclerView.setAdapter(adapter);
        db = FirebaseFirestore.getInstance();
        return view;
    }

    public void updateFilter(Date start, Date end) {
        if (db == null) return;

        Query query = db.collection("orders").orderBy("orderedAt", Query.Direction.DESCENDING);
        if (start != null) {
            query = query.whereGreaterThanOrEqualTo("orderedAt", start)
                    .whereLessThanOrEqualTo("orderedAt", end);
        }

        query.get().addOnSuccessListener(snapshots -> {
            orderList.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                Order order = doc.toObject(Order.class);
                order.setOrderId(doc.getId());
                orderList.add(order);
            }
            if (adapter != null) adapter.notifyDataSetChanged();
        });
    }

    private static class SimpleOrderAdapter extends RecyclerView.Adapter<SimpleOrderAdapter.ViewHolder> {
        List<Order> list;
        SimpleOrderAdapter(List<Order> l) { list = l; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sales_order_simple, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Order item = list.get(position);
            String id = item.getOrderId();
            holder.txtOrderId.setText("#" + (id.length() > 8 ? id.substring(0, 8) : id));
            holder.txtTotal.setText(String.format("₱%.2f", item.getTotal()));
            holder.txtStatus.setText(item.getStatus());

            if (item.getOrderedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                holder.txtDate.setText(sdf.format(item.getOrderedAt()));
            }
        }

        @Override public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtOrderId, txtDate, txtTotal, txtStatus;
            ViewHolder(View v) {
                super(v);
                txtOrderId = v.findViewById(R.id.txtOrderId);
                txtDate = v.findViewById(R.id.txtDate);
                txtTotal = v.findViewById(R.id.txtTotal);
                txtStatus = v.findViewById(R.id.txtStatus);
            }
        }
    }
}