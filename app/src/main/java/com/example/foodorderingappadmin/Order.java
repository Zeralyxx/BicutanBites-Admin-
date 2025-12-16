package com.example.foodorderingappadmin;

import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.Exclude; // Import this for Exclude annotation
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Order {

    // Core fields matching Firestore document keys
    private String orderId;
    private String userId; // Mapped via @PropertyName
    private String status;
    private double total;
    private Date orderedAt;
    private String note;
    private String paymentMethod;
    private List<Map<String, Object>> items;

    // Temporary field used only for client-side filtering/display (e.g., searching by name).
    @Exclude
    private String customerNameForSearch;

    // Empty constructor required by Firestore for automatic object mapping (toObject)
    public Order() {}

    // --- Getters and Setters ---
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    // Use @PropertyName to map the 'userId' Java field to 'userID' Firestore field (case matters)
    @PropertyName("userID")
    public String getUserId() { return userId; }

    @PropertyName("userID")
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public Date getOrderedAt() { return orderedAt; }
    public void setOrderedAt(Date orderedAt) { this.orderedAt = orderedAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    // Use @Exclude to prevent Firestore from attempting to read/write this field
    @Exclude
    public String getCustomerNameForSearch() { return customerNameForSearch; }
    @Exclude
    public void setCustomerNameForSearch(String customerNameForSearch) { this.customerNameForSearch = customerNameForSearch; }
}