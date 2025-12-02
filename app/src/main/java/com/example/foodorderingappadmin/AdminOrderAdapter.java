package com.example.foodorderingappadmin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminOrderAdapter extends RecyclerView.Adapter<AdminOrderAdapter.OrderViewHolder> {

    private Context context;
    private List<Order> orderList;
    private FirebaseFirestore db;
    private String[] statusOptions;

    // IMPORTANT: REPLACE "AIzaSyCF-l9ycCrInLruSbjDHAOjzElZz8gkauY" with your actual FCM Server Key
    private static final String FCM_SERVER_KEY = "AIzaSyCF-l9ycCrInLruSbjDHAOjzElZz8gkauY";

    // --- Helper for Notification Content ---
    private String[] getNotificationContentForStatus(String status) {
        String title = "";
        String message = "";

        switch (status) {
            case "Pending":
                title = "Order Received";
                message = "We’ve received your order and will begin preparing it shortly.";
                break;

            case "Being Made":
                title = "Your Order Is Being Prepared";
                message = "Our kitchen is now cooking your order. Thank you for waiting!";
                break;

            case "Being Delivered":
                title = "Your Order Is On the Way 🚗";
                message = "Our rider is heading to your location. Please keep your phone nearby.";
                break;

            case "Cancelled":
                title = "Order Cancelled";
                message = "Your order has been cancelled. If you believe this is a mistake, please contact us.";
                break;

            case "Completed":
                title = "Order Complete";
                message = "Your order has been completed. Thank you for ordering!";
                break;
            default:
                title = "Order Update";
                message = "Your order status has changed to: " + status;
                break;
        }

        return new String[]{title, message};
    }

    // --- FCM Sending Logic (Uses Volley) ---
    private void sendPushNotification(String userFcmToken, String title, String message) {
        JSONObject notification = new JSONObject();
        JSONObject notifBody = new JSONObject();
        try {
            notifBody.put("title", title);
            notifBody.put("body", message);

            notification.put("to", userFcmToken);
            notification.put("notification", notifBody);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // --- Network Request using Volley (Standard Legacy Endpoint) ---
        String url = "https://fcm.googleapis.com/fcm/send";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, notification,
                response -> Log.d("FCM_SEND", "Success: " + response),
                error -> Log.e("FCM_SEND", "Volley Error: " + error)) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "key=" + FCM_SERVER_KEY);
                return headers;
            }
        };

        // Add to request queue (Volley)
        Volley.newRequestQueue(context).add(jsonObjectRequest);
    }

    // --- Firestore Update and Token Fetch ---
    private void updateOrderStatus(String orderId, String newStatus) {
        if (orderId == null) return;

        // 1. Update status in Firestore
        db.collection("orders").document(orderId)
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Order Updated", Toast.LENGTH_SHORT).show();

                    // 2. Fetch the order again to get user ID
                    db.collection("orders").document(orderId).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    String userId = doc.getString("userID"); // Use userID field from DB

                                    if (userId != null && !userId.isEmpty()) {
                                        // 3. Fetch user token and notification preference
                                        db.collection("users").document(userId).get()
                                                .addOnSuccessListener(userDoc -> {
                                                    if (userDoc.exists()) {
                                                        String token = userDoc.getString("fcmToken");
                                                        // NOTE: Using Boolean object to handle potential null value from Firestore
                                                        Boolean receiveUpdates = userDoc.getBoolean("receiveOrderNotifications");

                                                        // Check for token and user preference (default true if field is missing/null)
                                                        if (token != null && !token.isEmpty() && (receiveUpdates == null || receiveUpdates)) {

                                                            // 4. Generate content and send
                                                            String[] notif = getNotificationContentForStatus(newStatus);
                                                            sendPushNotification(token, notif[0], notif[1]);
                                                        }
                                                    }
                                                });
                                    }
                                }
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Update Failed", Toast.LENGTH_SHORT).show()
                );
    }

    public AdminOrderAdapter(Context context, List<Order> orderList) {
        this.context = context;
        this.orderList = orderList;
        this.db = FirebaseFirestore.getInstance();
        this.statusOptions = context.getResources().getStringArray(R.array.order_status_options);
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orderList.get(position);

        // --- 1. Basic Info ---
        String displayId = order.getOrderId();
        if (displayId != null && displayId.length() > 8) displayId = displayId.substring(0, 8);
        holder.txtOrderId.setText("Order #" + displayId);
        holder.txtTotal.setText(String.format("₱%.2f", order.getTotal()));

        if (order.getOrderedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            holder.txtDate.setText(sdf.format(order.getOrderedAt()));
        }

        // --- 2. User/Delivery Info (Fetched from Users Collection) ---
        // Initialize placeholders
        holder.txtCustomerName.setText("Loading...");
        holder.txtDeliveryAddress.setText("Loading Address...");
        holder.txtCustomerPhone.setText("Loading Info...");

        String userId = order.getUserId();
        if (userId != null && !userId.isEmpty()) {
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            String address = documentSnapshot.getString("address");
                            String phone = documentSnapshot.getString("phoneNumber");
                            String email = documentSnapshot.getString("email");

                            holder.txtCustomerName.setText(name != null ? name : "Unknown User");
                            holder.txtDeliveryAddress.setText(address != null ? address : "No Address Provided");

                            // MODIFIED LOGIC: Show phone, fallback to email
                            if (phone != null && !phone.isEmpty()) {
                                holder.txtCustomerPhone.setText(phone);
                            } else if (email != null && !email.isEmpty()) {
                                holder.txtCustomerPhone.setText(email);
                            } else {
                                holder.txtCustomerPhone.setText("No Contact Info");
                            }
                        } else {
                            holder.txtCustomerName.setText("User Not Found");
                        }
                    })
                    .addOnFailureListener(e -> holder.txtCustomerName.setText("Error Loading"));
        } else {
            holder.txtCustomerName.setText("Guest / No User ID");
        }

        // --- 3. Items as Chips ---
        holder.itemChipGroup.removeAllViews();
        if (order.getItems() != null) {
            for (Map<String, Object> itemData : order.getItems()) {
                String name = (String) itemData.get("name");
                long qty = 1;
                if (itemData.get("qty") instanceof Long) qty = (Long) itemData.get("qty");
                else if (itemData.get("qty") instanceof Integer) qty = (Integer) itemData.get("qty");

                addChipToGroup(holder.itemChipGroup, qty + "x " + name);
            }
        }

        // --- 4. Customer Note ---
        if (order.getNote() != null && !order.getNote().isEmpty()) {
            holder.cardCustomerNote.setVisibility(View.VISIBLE);
            holder.txtCustomerNote.setText(order.getNote());
        } else {
            holder.cardCustomerNote.setVisibility(View.GONE);
        }

        // --- 5. Status Dropdown ---
        CustomStatusArrayAdapter adapter = new CustomStatusArrayAdapter(
                context, android.R.layout.simple_spinner_dropdown_item, statusOptions
        );
        holder.statusAutoComplete.setAdapter(adapter);
        holder.statusAutoComplete.setText(order.getStatus(), false);
        applyStatusStyle(holder, order.getStatus());

        holder.statusAutoComplete.setOnItemClickListener((parent, view, pos, id) -> {
            String newStatus = (String) parent.getItemAtPosition(pos);
            updateOrderStatus(order.getOrderId(), newStatus);
        });

        // --- 6. Mark as Done Button ---
        holder.btnMarkAsDone.setOnClickListener(v -> {
            updateOrderStatus(order.getOrderId(), "Completed");
        });
    }

    private void addChipToGroup(ChipGroup chipGroup, String text) {
        Chip chip = new Chip(context);
        chip.setText(text);
        // Use a safe light gray color for background
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F3F4F6")));
        chip.setTextColor(Color.parseColor("#374151"));
        chip.setEnsureMinTouchTargetSize(false);
        chipGroup.addView(chip);
    }

    private void applyStatusStyle(OrderViewHolder holder, String status) {
        int bgColorId = android.R.color.white;
        int tintColorId = R.color.black;
        int iconId = 0;

        if (status != null) {
            switch (status) {
                case "Pending":
                    bgColorId = R.color.status_pending_bg; tintColorId = R.color.status_pending_tint; iconId = R.drawable.ic_time; break;
                case "Being Made":
                    bgColorId = R.color.status_made_bg; tintColorId = R.color.status_made_tint; iconId = R.drawable.ic_chef; break;
                case "Being Delivered":
                    bgColorId = R.color.status_delivered_bg; tintColorId = R.color.status_delivered_tint; iconId = R.drawable.ic_delivery; break;
                case "Cancelled":
                    bgColorId = R.color.status_cancelled_bg; tintColorId = R.color.status_cancelled_tint; iconId = R.drawable.ic_cancel; break;
                case "Completed":
                    bgColorId = R.color.status_delivered_bg; tintColorId = R.color.status_delivered_tint; iconId = R.drawable.ic_archive; break;
            }
        }

        try {
            int color = ContextCompat.getColor(context, tintColorId);
            int bg = ContextCompat.getColor(context, bgColorId);
            holder.statusInputLayout.setBoxBackgroundColor(bg);
            holder.statusInputLayout.setStartIconDrawable(iconId);
            holder.statusInputLayout.setStartIconTintList(ContextCompat.getColorStateList(context, tintColorId));
            holder.statusInputLayout.setEndIconTintList(ContextCompat.getColorStateList(context, tintColorId));
            holder.statusAutoComplete.setTextColor(color);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public int getItemCount() { return orderList.size(); }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView txtOrderId, txtDate, txtTotal, txtCustomerName, txtCustomerPhone, txtDeliveryAddress, txtCustomerNote;
        TextInputLayout statusInputLayout;
        AutoCompleteTextView statusAutoComplete;
        ChipGroup itemChipGroup;
        MaterialCardView cardCustomerNote;
        MaterialButton btnMarkAsDone;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtOrderId = itemView.findViewById(R.id.txtOrderId);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtTotal = itemView.findViewById(R.id.txtTotal);
            txtCustomerName = itemView.findViewById(R.id.txtCustomerName);
            txtCustomerPhone = itemView.findViewById(R.id.txtCustomerPhone);
            txtDeliveryAddress = itemView.findViewById(R.id.txtDeliveryAddress);
            txtCustomerNote = itemView.findViewById(R.id.txtCustomerNote);
            statusInputLayout = itemView.findViewById(R.id.statusDropdownLayout);
            statusAutoComplete = itemView.findViewById(R.id.statusAutoComplete);
            itemChipGroup = itemView.findViewById(R.id.itemChipGroup);
            cardCustomerNote = itemView.findViewById(R.id.cardCustomerNote);
            btnMarkAsDone = itemView.findViewById(R.id.btnMarkAsDone);
        }
    }

    // --- Adapter for Dropdown ---
    private class CustomStatusArrayAdapter extends ArrayAdapter<String> {
        public CustomStatusArrayAdapter(@NonNull Context context, int resource, @NonNull String[] objects) {
            super(context, resource, objects);
        }
        @NonNull @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }
        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }
        private View getCustomView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_dropdown, parent, false);
            }
            TextView statusText = convertView.findViewById(R.id.statusText);
            ImageView statusIcon = convertView.findViewById(R.id.statusIcon);
            String status = getItem(position);
            if (status != null) {
                statusText.setText(status);
                int iconId = 0;
                switch (status) {
                    case "Pending": iconId = R.drawable.ic_time; break;
                    case "Being Made": iconId = R.drawable.ic_chef; break;
                    case "Being Delivered": iconId = R.drawable.ic_delivery; break;
                    case "Cancelled": iconId = R.drawable.ic_cancel; break;
                }
                if (iconId != 0) {
                    statusIcon.setImageResource(iconId);
                    statusIcon.setVisibility(View.VISIBLE);
                } else {
                    statusIcon.setVisibility(View.GONE);
                }
            }
            return convertView;
        }
    }
}