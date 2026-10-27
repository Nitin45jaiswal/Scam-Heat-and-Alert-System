package com.example.scamheat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Login extends AppCompatActivity {

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private TextView tvRegister;

    private FirebaseAuth mAuth;

    private String supabaseUrl;
    private String supabaseKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        mAuth = FirebaseAuth.getInstance();

        supabaseUrl = getString(R.string.supabase_url);
        supabaseKey = getString(R.string.supabase_publishable_key);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> loginUser());

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, Register.class);
            startActivity(intent);
        });
    }

    private void loginUser() {
        String email = etEmail.getText() != null
                ? etEmail.getText().toString().trim()
                : "";

        // Do not trim passwords: spaces may be part of a valid password.
        String password = etPassword.getText() != null
                ? etPassword.getText().toString()
                : "";

        if (email.isEmpty()) {
            etEmail.setError("Enter your email");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (password.trim().isEmpty()) {
            etPassword.setError("Password cannot be empty");
            etPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("LOGGING IN...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        if (firebaseUser == null) {
                            loginFailed("Unable to get your account. Please try again.");
                            return;
                        }

                        checkAdminFromSupabase(firebaseUser.getUid());
                    } else {
                        loginFailed(getFriendlyLoginError(task.getException()));
                    }
                });
    }

    private String getFriendlyLoginError(Exception exception) {
        if (exception instanceof FirebaseAuthException) {
            String errorCode = ((FirebaseAuthException) exception).getErrorCode();

            switch (errorCode) {
                case "ERROR_INVALID_EMAIL":
                    return "Please enter a valid email address.";

                case "ERROR_INVALID_CREDENTIAL":
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_USER_NOT_FOUND":
                    return "Incorrect email or password.";

                case "ERROR_USER_DISABLED":
                    return "This account has been disabled. Please contact support.";

                case "ERROR_TOO_MANY_REQUESTS":
                    return "Too many login attempts. Please try again later.";

                case "ERROR_NETWORK_REQUEST_FAILED":
                    return "Network error. Check your internet connection and try again.";

                default:
                    return "Login failed. Please check your details and try again.";
            }
        }

        return "Unable to connect. Check your internet connection and try again.";
    }

    /**
     * Checks whether the authenticated Firebase UID exists in Supabase admins.
     * A missing or non-admin role is treated as a normal user.
     */
    private void checkAdminFromSupabase(String firebaseUid) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String encodedUid = URLEncoder.encode(
                        firebaseUid,
                        StandardCharsets.UTF_8.name()
                );

                String requestUrl = supabaseUrl
                        + "/rest/v1/admins"
                        + "?firebase_uid=eq."
                        + encodedUid
                        + "&select=id,email,role";

                URL url = new URL(requestUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", supabaseKey);
                connection.setRequestProperty("Authorization", "Bearer " + supabaseKey);
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String response = readResponse(inputStream);

                if (responseCode >= 200 && responseCode < 300) {
                    JSONArray admins = new JSONArray(response);
                    boolean isAdmin = false;

                    if (admins.length() > 0) {
                        JSONObject adminObject = admins.getJSONObject(0);
                        String role = adminObject.optString("role", "");
                        isAdmin = "admin".equalsIgnoreCase(role.trim());
                    }

                    final boolean finalIsAdmin = isAdmin;

                    runOnUiThread(() -> {
                        if (finalIsAdmin) {
                            Toast.makeText(
                                    Login.this,
                                    "Admin login successful",
                                    Toast.LENGTH_SHORT
                            ).show();
                            openAdminDashboard();
                        } else {
                            Toast.makeText(
                                    Login.this,
                                    "Login successful",
                                    Toast.LENGTH_SHORT
                            ).show();
                            openMainActivity();
                        }
                    });
                } else {
                    runOnUiThread(() -> loginFailed(
                            "Unable to verify your account right now. Please try again."
                    ));
                }

            } catch (Exception e) {
                runOnUiThread(() -> loginFailed(
                        "Unable to connect to the server. Check your internet connection and try again."
                ));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void openAdminDashboard() {
        Intent intent = new Intent(Login.this, AdminDashboard.class);
        startActivity(intent);
        finish();
    }

    private void openMainActivity() {
        Intent intent = new Intent(Login.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void loginFailed(String message) {
        runOnUiThread(() -> {
            btnLogin.setEnabled(true);
            btnLogin.setText("LOGIN");

            new androidx.appcompat.app.AlertDialog.Builder(Login.this)
                    .setTitle("Login Failed")
                    .setMessage(message)
                    .setPositiveButton("OK", (dialog, which) -> {
                        // Sign out only if Firebase authentication succeeded
                        // but admin verification or a later login step failed.
                        if (mAuth.getCurrentUser() != null) {
                            mAuth.signOut();
                        }
                    })
                    .show();
        });
    }

    private String readResponse(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (Exception e) {
            return "";
        }

        return response.toString();
    }
}
