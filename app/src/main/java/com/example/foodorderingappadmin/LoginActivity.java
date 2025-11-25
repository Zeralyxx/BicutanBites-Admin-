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
    private FirebaseFirestore db; // Initialize Firestore

    // The single, authorized admin email (Optional, but good for quick verification)
    // IMPORTANT: If you want only one specific email to work, you can uncomment these lines
    // private static final String AUTHORIZED_EMAIL = "admin@foodexpress.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force Light Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_login);

        // Initialize Firebase services
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize Views
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        btnLogin = findViewById(R.id.btnLogin);

        // Check if user is already logged in
        if (mAuth.getCurrentUser() != null) {
            checkAdminRoleAndNavigate(mAuth.getCurrentUser());
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });
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

        // Show loading state
        setLoadingState(true);

        // 1. Authenticate with Firebase
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // 2. If authentication succeeds, verify the admin role
                                checkAdminRoleAndNavigate(user);
                            } else {
                                handleLoginFailure("Authentication failed: User object is null.");
                            }
                        } else {
                            // Login Failure
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
                    }
                });
    }

    private void checkAdminRoleAndNavigate(FirebaseUser user) {
        // We will check a dedicated collection 'admins'.
        // A document exists in 'admins' with the user's UID if they are an admin.
        db.collection("admins").document(user.getUid()).get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // Role Check Success: User is an admin
                                Toast.makeText(LoginActivity.this, "Admin Login Successful", Toast.LENGTH_SHORT).show();
                                navigateToDashboard();
                            } else {
                                // Role Check Failure: Valid user but not an admin
                                // IMPORTANT: Sign out the user immediately to prevent access.
                                mAuth.signOut();
                                handleLoginFailure("Access Denied: Account is not authorized as an administrator.");
                            }
                        } else {
                            // Firestore Read Failure (e.g., network error)
                            mAuth.signOut();
                            handleLoginFailure("Error checking permissions. Please try again.");
                        }
                    }
                });
    }

    private void handleLoginFailure(String message) {
        setLoadingState(false);
        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            btnLogin.setEnabled(false);
            btnLogin.setText("Signing In...");
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("Sign In");
        }
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        // Clear the back stack so user can't go back to login screen
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}