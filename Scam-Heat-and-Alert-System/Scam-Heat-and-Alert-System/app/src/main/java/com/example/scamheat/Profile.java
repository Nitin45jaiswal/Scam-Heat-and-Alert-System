package com.example.scamheat;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Profile extends AppCompatActivity {

    private TextView tvProfileAvatar;
    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private TextView tvPersonalName;
    private TextView tvPersonalEmail;
    private TextView tvPersonalPhone;

    private SwitchMaterial switchNotifications;
    private SwitchMaterial switchLocationAlerts;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private String supabaseUrl;
    private String supabaseKey;
    private String firebaseUid;

    private String currentName = "";
    private String currentEmail = "";
    private String currentPhone = "";

    private boolean loadingPreferences = false;

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            goToLogin();
            return;
        }

        firebaseUid = currentUser.getUid();

        supabaseUrl = getString(R.string.supabase_url).trim();
        supabaseKey = getString(
                R.string.supabase_publishable_key
        ).trim();

        if (supabaseUrl.endsWith("/")) {
            supabaseUrl = supabaseUrl.substring(
                    0, supabaseUrl.length() - 1
            );
        }

        if (supabaseKey.startsWith("\"")
                && supabaseKey.endsWith("\"")) {
            supabaseKey = supabaseKey.substring(
                    1, supabaseKey.length() - 1
            );
        }

        bindViews();
        setInitialUserDetails();
        setupListeners();
        loadProfile();
    }

    private void bindViews() {
        tvProfileAvatar = findViewById(R.id.tvProfileAvatar);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);

        tvPersonalName = findViewById(R.id.tvPersonalName);
        tvPersonalEmail = findViewById(R.id.tvPersonalEmail);
        tvPersonalPhone = findViewById(R.id.tvPersonalPhone);

        switchNotifications =
                findViewById(R.id.switchNotifications);

        switchLocationAlerts =
                findViewById(R.id.switchLocationAlerts);
    }

    private void setInitialUserDetails() {
        String email = currentUser.getEmail();

        currentEmail = email == null ? "" : email;
        currentName = currentUser.getDisplayName() == null
                ? ""
                : currentUser.getDisplayName();

        if (currentName.isEmpty()) {
            currentName = currentEmail.isEmpty()
                    ? "User"
                    : currentEmail.substring(
                    0, currentEmail.indexOf("@")
            );
        }

        updateProfileViews();
    }

    private void setupListeners() {
        MaterialButton btnEditProfile =
                findViewById(R.id.btnEditProfile);

        MaterialButton btnChangePassword =
                findViewById(R.id.btnChangePassword);

        MaterialButton btnLogout =
                findViewById(R.id.btnLogout);

        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Profile.this, EditProfile.class);
            startActivity(intent);
        });

        btnChangePassword.setOnClickListener(v ->
                sendPasswordReset()
        );

        btnLogout.setOnClickListener(v ->
                confirmLogout()
        );

        switchNotifications.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    if (!loadingPreferences) {
                        savePreference(
                                "notification_alerts_enabled",
                                isChecked
                        );
                    }
                }
        );

        switchLocationAlerts.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    if (!loadingPreferences) {
                        savePreference(
                                "location_alerts_enabled",
                                isChecked
                        );
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // LOAD PROFILE FROM SUPABASE
    // ---------------------------------------------------------

    private void loadProfile() {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String uid = URLEncoder.encode(
                        firebaseUid,
                        StandardCharsets.UTF_8.toString()
                );

                String endpoint = supabaseUrl
                        + "/rest/v1/users"
                        + "?firebase_uid=eq." + uid
                        + "&select=name,email,phone,"
                        + "notification_alerts_enabled,"
                        + "location_alerts_enabled";

                connection = openConnection(endpoint, "GET");

                int responseCode = connection.getResponseCode();
                String response = readResponse(
                        connection, responseCode
                );

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception(
                            "HTTP " + responseCode + ": " + response
                    );
                }

                JSONArray array = new JSONArray(response);

                if (array.length() == 0) {
                    runOnUiThread(() -> {
                        updateProfileViews();
                        Toast.makeText(
                                Profile.this,
                                "Profile row not found in Supabase. "
                                        + "Showing Firebase details.",
                                Toast.LENGTH_LONG
                        ).show();
                    });
                    return;
                }

                JSONObject data = array.getJSONObject(0);

                String name = data.optString("name", "");
                String email = data.optString(
                        "email", currentEmail
                );
                String phone = data.optString("phone", "");

                boolean notificationsEnabled =
                        data.optBoolean(
                                "notification_alerts_enabled", true
                        );

                boolean locationEnabled =
                        data.optBoolean(
                                "location_alerts_enabled", true
                        );

                runOnUiThread(() -> {
                    currentName = name.isEmpty()
                            ? currentName : name;

                    currentEmail = email.isEmpty()
                            ? currentEmail : email;

                    currentPhone = phone.equals("null")
                            ? "" : phone;

                    updateProfileViews();

                    loadingPreferences = true;

                    switchNotifications.setChecked(
                            notificationsEnabled
                    );

                    switchLocationAlerts.setChecked(
                            locationEnabled
                    );

                    loadingPreferences = false;
                });

            } catch (Exception e) {
                showToast("Profile load failed: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void updateProfileViews() {
        tvProfileName.setText(currentName);
        tvProfileEmail.setText(currentEmail);

        tvPersonalName.setText(
                currentName.isEmpty() ? "Not provided" : currentName
        );

        tvPersonalEmail.setText(
                currentEmail.isEmpty() ? "Not provided" : currentEmail
        );

        tvPersonalPhone.setText(
                currentPhone.isEmpty() ? "Not provided" : currentPhone
        );

        String initial = currentName.trim().isEmpty()
                ? "U"
                : currentName.trim().substring(0, 1).toUpperCase();

        tvProfileAvatar.setText(initial);
    }

    // ---------------------------------------------------------
    // EDIT PROFILE
    // ---------------------------------------------------------

    private void showEditProfileDialog() {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Full name");
        nameInput.setText(currentName);
        nameInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        EditText emailInput = new EditText(this);
        emailInput.setSingleLine(true);
        emailInput.setHint("Email address");
        emailInput.setText(currentEmail);
        emailInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        EditText phoneInput = new EditText(this);
        phoneInput.setSingleLine(true);
        phoneInput.setHint("Mobile number");
        phoneInput.setText(currentPhone);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);

        android.widget.LinearLayout layout =
                new android.widget.LinearLayout(this);

        layout.setOrientation(
                android.widget.LinearLayout.VERTICAL
        );

        int padding = (int) (20 * getResources()
                .getDisplayMetrics().density);

        layout.setPadding(padding, 0, padding, 0);

        layout.addView(nameInput);
        layout.addView(emailInput);
        layout.addView(phoneInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Profile")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String name = nameInput.getText()
                                    .toString().trim();

                            String email = emailInput.getText()
                                    .toString().trim();

                            String phone = phoneInput.getText()
                                    .toString().trim();

                            if (name.isEmpty()) {
                                nameInput.setError(
                                        "Enter your full name"
                                );
                                return;
                            }

                            if (!Patterns.EMAIL_ADDRESS
                                    .matcher(email).matches()) {
                                emailInput.setError(
                                        "Enter a valid email address"
                                );
                                return;
                            }

                            if (!phone.isEmpty()
                                    && !phone.matches("[0-9+() -]{7,20}")) {
                                phoneInput.setError(
                                        "Enter a valid mobile number"
                                );
                                return;
                            }

                            saveProfile(name, email, phone, dialog);
                        })
        );

        dialog.show();
    }

    private void saveProfile(
            String name,
            String email,
            String phone,
            AlertDialog dialog
    ) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String uid = URLEncoder.encode(
                        firebaseUid,
                        StandardCharsets.UTF_8.toString()
                );

                String endpoint = supabaseUrl
                        + "/rest/v1/users"
                        + "?firebase_uid=eq." + uid;

                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("email", email);
                body.put("phone", phone);

                connection = openConnection(endpoint, "PATCH");
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json"
                );
                connection.setRequestProperty(
                        "Prefer", "return=representation"
                );

                try (OutputStream output =
                             connection.getOutputStream()) {
                    output.write(body.toString().getBytes(
                            StandardCharsets.UTF_8
                    ));
                }

                int code = connection.getResponseCode();
                String response = readResponse(connection, code);

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "HTTP " + code + ": " + response
                    );
                }

                JSONArray updated = new JSONArray(response);

                if (updated.length() == 0) {
                    throw new Exception(
                            "No profile row was updated. "
                                    + "Check the users table and RLS policies."
                    );
                }

                // Supabase profile email is updated here.
                // Firebase Auth email is intentionally not changed.
                runOnUiThread(() -> {
                    currentName = name;
                    currentEmail = email;
                    currentPhone = phone;
                    updateProfileViews();
                    dialog.dismiss();

                    Toast.makeText(
                            Profile.this,
                            "Profile updated",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception e) {
                showToast("Profile update failed: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // ---------------------------------------------------------
    // SAVE ALERT PREFERENCE
    // ---------------------------------------------------------

    private void savePreference(
            String column,
            boolean enabled
    ) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String uid = URLEncoder.encode(
                        firebaseUid,
                        StandardCharsets.UTF_8.toString()
                );

                String endpoint = supabaseUrl
                        + "/rest/v1/users"
                        + "?firebase_uid=eq." + uid;

                JSONObject body = new JSONObject();
                body.put(column, enabled);

                connection = openConnection(endpoint, "PATCH");
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json"
                );
                connection.setRequestProperty(
                        "Prefer", "return=representation"
                );

                try (OutputStream output =
                             connection.getOutputStream()) {
                    output.write(body.toString().getBytes(
                            StandardCharsets.UTF_8
                    ));
                }

                int code = connection.getResponseCode();
                String response = readResponse(connection, code);

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "HTTP " + code + ": " + response
                    );
                }

                JSONArray updated = new JSONArray(response);

                if (updated.length() == 0) {
                    throw new Exception(
                            "No user row updated. Check your Supabase "
                                    + "RLS update policy."
                    );
                }

                runOnUiThread(() -> Toast.makeText(
                        Profile.this,
                        enabled ? "Setting enabled" : "Setting disabled",
                        Toast.LENGTH_SHORT
                ).show());

            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingPreferences = true;

                    if (column.equals("notification_alerts_enabled")) {
                        switchNotifications.setChecked(!enabled);
                    } else {
                        switchLocationAlerts.setChecked(!enabled);
                    }

                    loadingPreferences = false;

                    Toast.makeText(
                            Profile.this,
                            "Could not save setting: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // ---------------------------------------------------------
    // PASSWORD RESET
    // ---------------------------------------------------------

    private void sendPasswordReset() {
        String email = currentUser.getEmail();

        if (email == null || email.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Your Firebase account has no email address",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setMessage(
                        "Send a password reset link to:\n" + email + "?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send Link", (dialog, which) ->
                        mAuth.sendPasswordResetEmail(email)
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(
                                                Profile.this,
                                                "Password reset email sent",
                                                Toast.LENGTH_LONG
                                        ).show()
                                )
                                .addOnFailureListener(e ->
                                        Toast.makeText(
                                                Profile.this,
                                                "Could not send reset email: "
                                                        + e.getMessage(),
                                                Toast.LENGTH_LONG
                                        ).show()
                                )
                )
                .show();
    }

    // ---------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log Out", (dialog, which) -> {
                    mAuth.signOut();
                    goToLogin();
                })
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    // ---------------------------------------------------------
    // HTTP HELPERS
    // ---------------------------------------------------------

    private HttpURLConnection openConnection(
            String endpoint,
            String method
    ) throws Exception {
        URL url = new URL(endpoint);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        connection.setRequestProperty("apikey", supabaseKey);
        connection.setRequestProperty(
                "Authorization", "Bearer " + supabaseKey
        );
        connection.setRequestProperty(
                "Accept", "application/json"
        );

        return connection;
    }

    private String readResponse(
            HttpURLConnection connection,
            int responseCode
    ) throws Exception {
        java.io.InputStream stream =
                responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        if (stream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        stream, StandardCharsets.UTF_8
                ))) {
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    private void showToast(String message) {
        runOnUiThread(() -> {
            if (!isFinishing()) {
                Toast.makeText(
                        Profile.this,
                        message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }
}
