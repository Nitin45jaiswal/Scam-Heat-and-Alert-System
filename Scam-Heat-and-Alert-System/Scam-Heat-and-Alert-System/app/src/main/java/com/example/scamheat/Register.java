
package com.example.scamheat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Register extends AppCompatActivity {

    private static final String TAG = "ScamHeatRegister";
    private static final String ADMIN_EMAIL = "admin@scamheat.com";

    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton btnRegister;

    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private double registrationLatitude = 0.0;
    private double registrationLongitude = 0.0;
    private boolean hasRegistrationLocation = false;
    private boolean locationCheckFinished = false;
    private boolean registrationInProgress = false;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);

        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);

        locationPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        result -> {
                            boolean fineGranted = Boolean.TRUE.equals(
                                    result.get(Manifest.permission.ACCESS_FINE_LOCATION));

                            boolean coarseGranted = Boolean.TRUE.equals(
                                    result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                            if (fineGranted || coarseGranted ||
                                    hasLocationPermission()) {
                                fetchRegistrationLocation();
                            } else {
                                // Location is optional. Registration can continue.
                                locationCheckFinished = true;
                                enableRegistrationButton();
                            }
                        });

        btnRegister.setOnClickListener(v -> registerUser());

        if (findViewById(R.id.tvLogin) != null) {
            findViewById(R.id.tvLogin).setOnClickListener(v -> openLogin());
        }

        // Ask for location before registration, but do not require it.
        checkLocationPermission();
    }

    private void checkLocationPermission() {
        boolean fineGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            fetchRegistrationLocation();
        } else {
            btnRegister.setEnabled(false);
            btnRegister.setText("Checking location...");

            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void fetchRegistrationLocation() {
        if (!hasLocationPermission()) {
            locationCheckFinished = true;
            enableRegistrationButton();
            return;
        }

        try {
            CancellationTokenSource cancellationTokenSource =
                    new CancellationTokenSource();

            fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.getToken()
            ).addOnSuccessListener(location -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (location != null) {
                    registrationLatitude = location.getLatitude();
                    registrationLongitude = location.getLongitude();
                    hasRegistrationLocation = true;

                    Log.d(TAG, "Registration location captured.");
                } else {
                    hasRegistrationLocation = false;
                    Log.w(TAG, "Location unavailable; continuing without it.");
                }

                locationCheckFinished = true;
                enableRegistrationButton();

            }).addOnFailureListener(error -> {
                Log.w(TAG, "Unable to obtain registration location.", error);

                hasRegistrationLocation = false;
                locationCheckFinished = true;
                enableRegistrationButton();
            });

        } catch (SecurityException exception) {
            Log.w(TAG, "Location permission unavailable.", exception);

            hasRegistrationLocation = false;
            locationCheckFinished = true;
            enableRegistrationButton();
        }
    }

    private void enableRegistrationButton() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (!registrationInProgress) {
            btnRegister.setEnabled(true);
            btnRegister.setText("REGISTER");
        }
    }

    private void registerUser() {
        if (registrationInProgress) {
            return;
        }

        String name = getText(etName);
        String email = getText(etEmail).toLowerCase(java.util.Locale.ROOT);
        String password = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);

        if (name.isEmpty()) {
            etName.setError("Please enter your full name.");
            etName.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            etEmail.setError("Please enter your email address.");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address.");
            etEmail.requestFocus();
            return;
        }

        if (ADMIN_EMAIL.equalsIgnoreCase(email)) {
            showErrorDialog(
                    "Registration Not Allowed",
                    "This email is reserved for the administrator. " +
                            "Please use the separate admin login."
            );
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Please enter a password.");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must contain at least 6 characters.");
            etPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password.");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match.");
            etConfirmPassword.requestFocus();
            return;
        }

        registrationInProgress = true;
        btnRegister.setEnabled(false);
        btnRegister.setText("CREATING ACCOUNT...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        if (firebaseUser == null) {
                            registrationInProgress = false;
                            enableRegistrationButton();

                            showErrorDialog(
                                    "Registration Failed",
                                    "The account was created, but its user session " +
                                            "could not be obtained. Please try logging in."
                            );
                            return;
                        }

                        saveRegistrationData(
                                firebaseUser,
                                name,
                                email
                        );

                    } else {
                        registrationInProgress = false;
                        enableRegistrationButton();

                        showErrorDialog(
                                "Registration Failed",
                                getFriendlyFirebaseError(task.getException())
                        );
                    }
                });
    }

    private String getFriendlyFirebaseError(Exception exception) {
        if (exception instanceof FirebaseAuthException) {
            String errorCode =
                    ((FirebaseAuthException) exception).getErrorCode();

            switch (errorCode) {
                case "ERROR_INVALID_EMAIL":
                    return "Please enter a valid email address.";

                case "ERROR_EMAIL_ALREADY_IN_USE":
                    return "This email is already registered. Please log in instead.";

                case "ERROR_WEAK_PASSWORD":
                    return "Please choose a stronger password.";

                case "ERROR_NETWORK_REQUEST_FAILED":
                    return "Network error. Check your internet connection and try again.";

                case "ERROR_TOO_MANY_REQUESTS":
                    return "Too many attempts. Please try again later.";

                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "Email and password registration is not enabled in Firebase Authentication.";

                default:
                    Log.e(TAG, "Firebase registration error: " + errorCode,
                            exception);
                    return "Unable to create your account. Please check your details " +
                            "and Firebase Authentication settings, then try again.";
            }
        }

        Log.e(TAG, "Firebase registration failed.", exception);

        String message = exception != null
                ? exception.getMessage()
                : null;

        if (message != null &&
                message.toLowerCase(java.util.Locale.ROOT).contains("network")) {
            return "Network error. Check your internet connection and try again.";
        }

        return "Registration failed. Please check your internet connection and try again.";
    }

    private void saveRegistrationData(
            FirebaseUser firebaseUser,
            String name,
            String email
    ) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String supabaseUrl = getString(R.string.supabase_url).trim();
                String publishableKey =
                        getString(R.string.supabase_publishable_key).trim();

                if (supabaseUrl.isEmpty() || publishableKey.isEmpty()) {
                    throw new Exception(
                            "Supabase URL or publishable key is missing from Android resources."
                    );
                }

                JSONObject json = new JSONObject();
                json.put("firebase_uid", firebaseUser.getUid());
                json.put("name", name);
                json.put("email", email);

                if (hasRegistrationLocation) {
                    json.put("latitude", registrationLatitude);
                    json.put("longitude", registrationLongitude);
                    json.put("alert_location_set", true);
                } else {
                    json.put("alert_location_set", false);
                }

                URL url = new URL(
                        supabaseUrl + "/rest/v1/users"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);

                connection.setRequestProperty("apikey", publishableKey);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );
                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );
                connection.setRequestProperty(
                        "Prefer",
                        "return=representation"
                );

                byte[] requestBody =
                        json.toString().getBytes(StandardCharsets.UTF_8);

                try (OutputStream outputStream =
                             connection.getOutputStream()) {
                    outputStream.write(requestBody);
                    outputStream.flush();
                }

                int responseCode = connection.getResponseCode();
                String responseBody = readResponse(connection, responseCode);

                if (responseCode >= 200 && responseCode < 300) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        SharedPreferences preferences =
                                getSharedPreferences(
                                        "ScamHeatPrefs",
                                        MODE_PRIVATE
                                );

                        preferences.edit()
                                .putString("name", name)
                                .putString("email", email)
                                .apply();

                        saveFcmToken(firebaseUser.getUid());

                        registrationInProgress = false;

                        Toast.makeText(
                                Register.this,
                                "Registration successful!",
                                Toast.LENGTH_SHORT
                        ).show();

                        startActivity(
                                new Intent(Register.this, MainActivity.class)
                        );
                        finish();
                    });

                } else {
                    String errorMessage =
                            "Supabase could not save your profile.\n\n" +
                                    "HTTP Code: " + responseCode +
                                    "\n\nResponse:\n" + responseBody;

                    Log.e(TAG, errorMessage);

                    rollbackNewFirebaseAccount(firebaseUser, errorMessage);
                }

            } catch (Exception exception) {
                Log.e(TAG, "Failed to save registration data to Supabase.",
                        exception);

                String errorMessage =
                        "Could not save your profile to Supabase.\n\n" +
                                exception.getMessage() +
                                "\n\nPlease check your internet connection, " +
                                "Supabase URL, publishable key, and users-table " +
                                "permissions.";

                rollbackNewFirebaseAccount(firebaseUser, errorMessage);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /*
     * If the profile insert fails, attempt to delete the just-created
     * Firebase account so the user can retry registration.
     */
    private void rollbackNewFirebaseAccount(
            FirebaseUser firebaseUser,
            String errorMessage
    ) {
        firebaseUser.delete().addOnCompleteListener(task -> {
            mAuth.signOut();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                registrationInProgress = false;
                enableRegistrationButton();

                String finalMessage = errorMessage;

                if (!task.isSuccessful()) {
                    finalMessage +=
                            "\n\nFirebase account cleanup also failed. " +
                                    "If this email is reported as already registered " +
                                    "on your next attempt, try logging in or remove " +
                                    "the incomplete account from Firebase Authentication.";
                } else {
                    finalMessage +=
                            "\n\nThe incomplete Firebase account was removed. " +
                                    "You can correct the problem and try again.";
                }

                showErrorDialog("Registration Could Not Be Completed",
                        finalMessage);
            });
        });
    }

    private String readResponse(
            HttpURLConnection connection,
            int responseCode
    ) {
        InputStream inputStream = null;

        try {
            inputStream = responseCode >= 200 && responseCode < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (inputStream == null) {
                return "";
            }

            StringBuilder result = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            inputStream,
                            StandardCharsets.UTF_8
                    ))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line).append('\n');
                }
            }

            return result.toString().trim();

        } catch (Exception exception) {
            Log.w(TAG, "Could not read server response.", exception);
            return "Unable to read the server response.";
        }
    }

    private void saveFcmToken(String firebaseUid) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().isEmpty()) {
                        Log.w(TAG, "FCM returned an empty token.");
                        return;
                    }

                    executor.execute(() ->
                            saveFcmTokenToSupabase(firebaseUid, token)
                    );
                })
                .addOnFailureListener(error ->
                        Log.e(TAG, "Unable to obtain FCM token.", error)
                );
    }

    private void saveFcmTokenToSupabase(
            String firebaseUid,
            String token
    ) {
        HttpURLConnection connection = null;

        try {
            String supabaseUrl = getString(R.string.supabase_url).trim();
            String publishableKey =
                    getString(R.string.supabase_publishable_key).trim();

            String encodedUid = java.net.URLEncoder.encode(
                    firebaseUid,
                    StandardCharsets.UTF_8.name()
            );

            URL url = new URL(
                    supabaseUrl +
                            "/rest/v1/users?firebase_uid=eq." +
                            encodedUid
            );

            JSONObject json = new JSONObject();
            json.put("fcm_token", token);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);

            connection.setRequestProperty("apikey", publishableKey);
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );
            connection.setRequestProperty(
                    "Prefer",
                    "return=minimal"
            );

            byte[] requestBody =
                    json.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream outputStream =
                         connection.getOutputStream()) {
                outputStream.write(requestBody);
                outputStream.flush();
            }

            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                Log.d(TAG, "FCM token saved to Supabase.");
            } else {
                String response = readResponse(connection, responseCode);

                Log.e(TAG,
                        "FCM token save failed. HTTP " +
                                responseCode + ": " + response);
            }

        } catch (Exception exception) {
            Log.e(TAG, "Error saving FCM token to Supabase.", exception);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String getText(TextInputEditText editText) {
        if (editText.getText() == null) {
            return "";
        }

        // Do not trim passwords; spaces can be part of a password.
        return editText.getText().toString();
    }

    private void openLogin() {
        startActivity(new Intent(Register.this, Login.class));
        finish();
    }

    private void showErrorDialog(String title, String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setNeutralButton("COPY ERROR", (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(
                                    Context.CLIPBOARD_SERVICE
                            );

                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                        "ScamHeat error",
                                        message
                                )
                        );

                        Toast.makeText(
                                Register.this,
                                "Error copied to clipboard.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Do not interrupt a running registration/network operation here.
        // The executor is a single background worker for this screen.
    }
}
