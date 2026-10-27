package com.example.scamheat;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditProfile extends AppCompatActivity {

    private TextInputEditText etEditName;
    private TextInputEditText etEditEmail;
    private TextInputEditText etEditPhone;

    private MaterialButton btnSaveProfile;
    private MaterialButton btnCancelEdit;
    private MaterialButton btnBack;
    private TextView tvEditAvatar;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private String supabaseUrl;
    private String supabaseKey;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private boolean isSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_profile);

        bindViews();

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        supabaseUrl = getString(R.string.supabase_url);
        supabaseKey = getString(R.string.supabase_publishable_key);

        if (currentUser == null) {
            Toast.makeText(this,
                    "Please log in again.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupListeners();
        loadProfile();
    }

    private void bindViews() {
        etEditName = findViewById(R.id.etEditName);
        etEditEmail = findViewById(R.id.etEditEmail);
        etEditPhone = findViewById(R.id.etEditPhone);

        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
        btnBack = findViewById(R.id.btnBack);
        tvEditAvatar = findViewById(R.id.tvEditAvatar);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnCancelEdit.setOnClickListener(v -> finish());
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        etEditName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                String name = s.toString().trim();

                if (!name.isEmpty()) {
                    tvEditAvatar.setText(
                            name.substring(0, 1).toUpperCase());
                } else {
                    tvEditAvatar.setText("SH");
                }
            }

            @Override
            public void afterTextChanged(
                    android.text.Editable s) {
            }
        });
    }

    private void loadProfile() {
        btnSaveProfile.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String uid = currentUser.getUid();

                String endpoint = supabaseUrl
                        + "/rest/v1/users?firebase_uid=eq."
                        + URLEncoder.encode(uid, "UTF-8")
                        + "&select=name,email,phone&limit=1";

                connection = (HttpURLConnection)
                        new URL(endpoint).openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                connection.setRequestProperty(
                        "apikey", supabaseKey);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + supabaseKey);
                connection.setRequestProperty(
                        "Accept", "application/json");

                int responseCode = connection.getResponseCode();
                String response = readResponse(connection, responseCode);

                if (responseCode >= 200 && responseCode < 300) {
                    JSONArray array = new JSONArray(response);

                    if (array.length() == 0) {
                        runOnUiThread(() -> {
                            btnSaveProfile.setEnabled(true);
                            Toast.makeText(this,
                                    "Profile not found in Supabase.",
                                    Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    JSONObject profile = array.getJSONObject(0);

                    String name = profile.optString("name", "");
                    String email = profile.optString("email", "");
                    String phone = profile.optString("phone", "");

                    runOnUiThread(() -> {
                        etEditName.setText(name);
                        etEditEmail.setText(email);
                        etEditPhone.setText(phone);

                        if (!name.trim().isEmpty()) {
                            tvEditAvatar.setText(
                                    name.trim().substring(0, 1)
                                            .toUpperCase());
                        }

                        btnSaveProfile.setEnabled(true);
                    });

                } else {
                    showLoadError("Could not load profile. HTTP "
                            + responseCode + ": " + response);
                }

            } catch (Exception e) {
                showLoadError("Profile loading failed: "
                        + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void saveProfile() {
        if (isSaving) {
            return;
        }

        String name = getText(etEditName);
        String email = getText(etEditEmail);
        String phone = getText(etEditPhone);

        if (TextUtils.isEmpty(name)) {
            etEditName.setError("Enter your full name");
            etEditName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)
                || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEditEmail.setError("Enter a valid email address");
            etEditEmail.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(phone)
                && !phone.matches("^[0-9+() -]{7,15}$")) {
            etEditPhone.setError("Enter a valid phone number");
            etEditPhone.requestFocus();
            return;
        }

        isSaving = true;
        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setText("Saving...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String uid = currentUser.getUid();

                String endpoint = supabaseUrl
                        + "/rest/v1/users?firebase_uid=eq."
                        + URLEncoder.encode(uid, "UTF-8");

                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("email", email);
                body.put("phone",
                        TextUtils.isEmpty(phone) ? JSONObject.NULL : phone);

                connection = (HttpURLConnection)
                        new URL(endpoint).openConnection();

                connection.setRequestMethod("PATCH");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "apikey", supabaseKey);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + supabaseKey);
                connection.setRequestProperty(
                        "Content-Type", "application/json");
                connection.setRequestProperty(
                        "Prefer", "return=representation");

                byte[] data = body.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                    output.flush();
                }

                int responseCode = connection.getResponseCode();
                String response = readResponse(connection, responseCode);

                if (responseCode >= 200 && responseCode < 300) {
                    JSONArray result = new JSONArray(response);

                    if (result.length() == 0) {
                        showSaveError(
                                "No profile was updated. Check your "
                                        + "Supabase row and RLS policies.");
                        return;
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "Profile updated successfully.",
                                Toast.LENGTH_SHORT).show();

                        setResult(RESULT_OK);
                        finish();
                    });

                } else {
                    showSaveError("Save failed. HTTP "
                            + responseCode + ": " + response);
                }

            } catch (Exception e) {
                showSaveError("Could not save profile: "
                        + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                runOnUiThread(() -> {
                    isSaving = false;
                    if (!isFinishing()) {
                        btnSaveProfile.setEnabled(true);
                        btnSaveProfile.setText("Save Changes");
                    }
                });
            }
        });
    }

    private String getText(TextInputEditText field) {
        if (field.getText() == null) {
            return "";
        }
        return field.getText().toString().trim();
    }

    private String readResponse(
            HttpURLConnection connection, int responseCode)
            throws Exception {

        InputStream stream = responseCode >= 200 && responseCode < 400
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }

    private void showLoadError(String message) {
        runOnUiThread(() -> {
            btnSaveProfile.setEnabled(true);
            Toast.makeText(this, message,
                    Toast.LENGTH_LONG).show();
        });
    }

    private void showSaveError(String message) {
        runOnUiThread(() -> Toast.makeText(
                this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
