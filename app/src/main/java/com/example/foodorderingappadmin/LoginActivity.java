package com.example.foodorderingappadmin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private MaterialButton btnLogin;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force Light Mode for the Admin application
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_login);

        // Initialize Firebase services
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize Views
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        btnLogin = findViewById(R.id.btnLogin);

        // Check if user is already logged in (persistence check)
        if (mAuth.getCurrentUser() != null) {
            checkAdminRoleAndNavigate(mAuth.getCurrentUser());
        }

        // Setup Login Button Listener
        btnLogin.setOnClickListener(v -> loginUser());
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Basic Validation
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        setLoadingState(true);

        // 1. Authenticate user credentials with Firebase Authentication
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // 2. If Auth succeeds, verify the user has the 'admin' role
                            checkAdminRoleAndNavigate(user);
                        } else {
                            handleLoginFailure("Authentication failed: User object is null.");
                        }
                    } else {
                        // Handle specific Auth errors (e.g., wrong password/email)
                        String errorMessage;
                        try {
                            throw task.getException();
                        } catch (FirebaseAuthInvalidUserException e) {
                            errorMessage = "User not found or account disabled.";
                        } catch (FirebaseAuthInvalidCredentialsException e) {
                            errorMessage = "Invalid email or password.";
                        } catch (Exception e) {
                            errorMessage = "Authentication failed. " + e.getMessage();
                        }
                        handleLoginFailure(errorMessage);
                    }
                });
    }

    private void checkAdminRoleAndNavigate(FirebaseUser user) {
        // 3. Verify Admin Role: Check for the user's UID in the 'admins' collection
        db.collection("admins").document(user.getUid()).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Role Check Success: User is an admin
                            Toast.makeText(LoginActivity.this, "Admin Login Successful", Toast.LENGTH_SHORT).show();
                            navigateToDashboard();
                        } else {
                            // Role Check Failure: Valid Firebase user, but NOT an authorized admin.
                            // CRITICAL: Sign out the user immediately to prevent non-admin access.
                            mAuth.signOut();
                            handleLoginFailure("Access Denied: Account is not authorized as an administrator.");
                        }
                    } else {
                        // Firestore Read Failure (network/permissions error)
                        mAuth.signOut();
                        handleLoginFailure("Error checking permissions. Please try again.");
                    }
                });
    }

    private void handleLoginFailure(String message) {
        setLoadingState(false);
        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
    }

    private void setLoadingState(boolean isLoading) {
        // Manages button state during the asynchronous login process
        if (isLoading) {
            btnLogin.setEnabled(false);
            btnLogin.setText("Signing In...");
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("Sign In");
        }
    }

    private void navigateToDashboard() {
        // Navigates to the main activity and clears the back stack
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}