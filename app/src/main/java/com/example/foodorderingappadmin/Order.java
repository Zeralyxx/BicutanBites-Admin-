package com.example.foodorderingappadmin;

import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.Exclude; // Import this for Exclude annotation
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Order {
    private String orderId;
    private String userId;
    private String status;
    private double total;
    private Date orderedAt;
    private String note;
    private String paymentMethod;
    private List<Map<String, Object>> items;

    // NEW FIELD: Used only for filtering/display logic in the fragment/adapter.
    // We exclude it so Firestore doesn't try to write this field back to the database.
    @Exclude
    private String customerNameForSearch;

    public Order() {}

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

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

    // Getter/Setter for the temporary search name field
    @Exclude
    public String getCustomerNameForSearch() { return customerNameForSearch; }
    @Exclude
    public void setCustomerNameForSearch(String customerNameForSearch) { this.customerNameForSearch = customerNameForSearch; }
}