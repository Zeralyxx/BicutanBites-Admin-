package com.example.foodorderingappadmin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class OrdersFragment extends Fragment {

    private RecyclerView ordersRecyclerView;
    private AdminOrderAdapter orderAdapter;
    private List<Order> orderList;
    private FirebaseFirestore db;

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

        // Setup RecyclerView
        ordersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        orderList = new ArrayList<>();
        orderAdapter = new AdminOrderAdapter(getContext(), orderList);
        ordersRecyclerView.setAdapter(orderAdapter);

        fetchOrders();
    }

    private void fetchOrders() {
        // Fetch all orders, sorted by date (newest first)
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
                            orderList.clear();
                            for (QueryDocumentSnapshot doc : value) {
                                Order order = doc.toObject(Order.class);
                                order.setOrderId(doc.getId()); // Ensure ID is captured
                                orderList.add(order);
                            }
                            orderAdapter.notifyDataSetChanged();
                        }
                    }
                });
    }
}