
package com.example.scamheat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportScam extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

    private AutoCompleteTextView spinnerScamType;
    private TextInputEditText etSuspiciousInfo;
    private TextInputEditText etDescription;
    private MaterialButton btnEvidence;
    private MaterialButton btnSubmitReport;
    private MaterialButton btnAiAnalyze;
    private MaterialButton btnAiDuplicates;
    private TextView tvLocation;
    private MaterialCardView cardAiResult;
    private TextView tvAiTitle;
    private TextView tvAiResult;

    private FusedLocationProviderClient fusedLocationClient;

    private double latitude = 0.0;
    private double longitude = 0.0;

    private Uri selectedEvidenceUri;
    private String selectedEvidenceName;
    private String selectedEvidenceMimeType;

    private String supabaseUrl;
    private String supabaseKey;

    private final ExecutorService executorService =
            Executors.newFixedThreadPool(3);

    private final ActivityResultLauncher<String[]> evidencePicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null) return;

                        String mimeType = getContentResolver().getType(uri);

                        if (mimeType == null ||
                                (!mimeType.equals("image/jpeg")
                                        && !mimeType.equals("image/png")
                                        && !mimeType.equals("application/pdf"))) {
                            Toast.makeText(this,
                                    "Choose a JPG, PNG, or PDF file.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        selectedEvidenceUri = uri;
                        selectedEvidenceName = getFileName(uri);
                        selectedEvidenceMimeType = mimeType;

                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) { }

                        btnEvidence.setText("✓ Evidence Selected");
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.report_scam);

        spinnerScamType = findViewById(R.id.spinnerScamType);
        etSuspiciousInfo = findViewById(R.id.etSuspiciousInfo);
        etDescription = findViewById(R.id.etDescription);
        btnEvidence = findViewById(R.id.btnEvidence);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        tvLocation = findViewById(R.id.tvLocation);

        btnAiAnalyze = findViewById(R.id.btnAiAnalyze);
        btnAiDuplicates = findViewById(R.id.btnAiDuplicates);
        cardAiResult = findViewById(R.id.cardAiResult);
        tvAiTitle = findViewById(R.id.tvAiTitle);
        tvAiResult = findViewById(R.id.tvAiResult);

        supabaseUrl = getString(R.string.supabase_url)
                .replaceAll("/+$", "");
        supabaseKey = getString(R.string.supabase_publishable_key);

        String[] scamTypes = {
                "UPI Fraud", "Fake Job Offer", "Phishing",
                "Banking Scam", "Courier Scam", "Digital Arrest",
                "Investment Scam", "Online Shopping Scam",
                "Social Media Scam", "Loan Scam", "Lottery Scam", "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, scamTypes);
        spinnerScamType.setAdapter(adapter);
        spinnerScamType.setOnClickListener(v -> spinnerScamType.showDropDown());

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);
        getCurrentLocation();

        btnEvidence.setOnClickListener(v ->
                evidencePicker.launch(new String[]{
                        "image/jpeg", "image/png", "application/pdf"
                }));

        btnSubmitReport.setOnClickListener(v -> submitReport());
        btnAiAnalyze.setOnClickListener(v -> runAiFunction(false));
        btnAiDuplicates.setOnClickListener(v -> runAiFunction(true));
    }

    private String getFileName(Uri uri) {
        String result = null;

        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) result = cursor.getString(index);
            }
        } catch (Exception ignored) { }

        return result == null || result.trim().isEmpty()
                ? "evidence_file" : result;
    }

    private void getCurrentLocation() {
        boolean fine = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        boolean coarse = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST);
            tvLocation.setText("Location permission required");
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                            tvLocation.setText("Latitude: " + latitude
                                    + "\nLongitude: " + longitude);
                        } else {
                            tvLocation.setText("Location unavailable");
                        }
                    })
                    .addOnFailureListener(e ->
                            tvLocation.setText("Unable to get location"));
        } catch (SecurityException e) {
            tvLocation.setText("Location permission unavailable");
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this,
                        "Location permission was not granted",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void runAiFunction(boolean checkDuplicates) {
        String scamType = spinnerScamType.getText().toString().trim();
        String suspiciousInfo = etSuspiciousInfo.getText() == null
                ? "" : etSuspiciousInfo.getText().toString().trim();
        String description = etDescription.getText() == null
                ? "" : etDescription.getText().toString().trim();

        if (scamType.isEmpty()) {
            spinnerScamType.setError("Select scam type");
            return;
        }
        if (suspiciousInfo.isEmpty()) {
            etSuspiciousInfo.setError("Enter suspicious information");
            return;
        }
        if (description.isEmpty()) {
            etDescription.setError("Describe the scam first");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showAiResult("Authentication required",
                    "Please sign in before using AI.");
            return;
        }

        MaterialButton button = checkDuplicates
                ? btnAiDuplicates : btnAiAnalyze;
        button.setEnabled(false);
        button.setText("PLEASE WAIT...");

        user.getIdToken(false)
                .addOnSuccessListener(tokenResult -> {
                    String token = tokenResult.getToken();
                    if (token == null || token.isEmpty()) {
                        showAiResult("Authentication failed",
                                "Sign in again and retry.");
                        resetAiButton(button, checkDuplicates);
                        return;
                    }

                    executorService.execute(() -> {
                        try {
                            String endpoint = checkDuplicates
                                    ? "scam-ai-duplicates"
                                    : "scam-ai-analysis";

                            JSONObject payload = new JSONObject();
                            if (checkDuplicates) {
                                payload.put("scam_type", scamType);
                                payload.put("suspicious_info", suspiciousInfo);
                                payload.put("description", description);
                            } else {
                                payload.put("category", scamType);
                                payload.put("message",
                                        "Suspicious information: "
                                                + suspiciousInfo
                                                + "\nDescription: " + description);
                            }

                            JSONObject result = postEdgeFunction(
                                    endpoint, payload, token);

                            if (checkDuplicates) {
                                displayDuplicates(result);
                            } else {
                                displayAnalysis(result);
                            }
                        } catch (Exception e) {
                            showAiResult("AI request failed",
                                    e.getMessage() == null
                                            ? "Check the connection and function logs."
                                            : e.getMessage());
                        } finally {
                            runOnUiThread(() ->
                                    resetAiButton(button, checkDuplicates));
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    showAiResult("Authentication failed",
                            "Could not obtain your Firebase ID token.");
                    resetAiButton(button, checkDuplicates);
                });
    }

    private JSONObject postEdgeFunction(
            String functionName, JSONObject payload, String token)
            throws Exception {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(supabaseUrl
                    + "/functions/v1/" + functionName);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("apikey", supabaseKey);
            connection.setRequestProperty(
                    "Authorization", "Bearer " + token);
            connection.setRequestProperty(
                    "Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload.toString()
                        .getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            String response = readResponse(connection, code);

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + response);
            }

            JSONObject json = new JSONObject(response);
            if (!json.optBoolean("success", true)) {
                throw new Exception(json.optString(
                        "error", "The AI function reported failure."));
            }
            return json;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readResponse(
            HttpURLConnection connection, int code) throws Exception {
        InputStream stream = code >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();

        if (stream == null) return "";

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private void displayAnalysis(JSONObject result) {
        StringBuilder text = new StringBuilder();

        text.append("Risk level: ")
                .append(result.optString("risk_level", "Unclear"))
                .append("\nRisk score: ")
                .append(result.optInt("risk_score", -1) >= 0
                        ? result.optInt("risk_score") + "/100"
                        : "Not provided")
                .append("\n\n")
                .append(result.optString("summary",
                        "No summary was returned."));

        appendJsonValue(text, "\n\nWarning signs", result.opt("warning_signs"));
        appendJsonValue(text, "\n\nRecommended actions",
                result.opt("recommended_actions"));
        appendJsonValue(text, "\n\nLimitations", result.opt("limitations"));

        showAiResult("AI Scam Analysis", text.toString());
    }

    private void displayDuplicates(JSONObject result) {
        StringBuilder text = new StringBuilder();
        JSONArray duplicates = result.optJSONArray("possible_duplicates");

        if (duplicates == null || duplicates.length() == 0) {
            text.append("No possible duplicates were returned by the AI service.");
        } else {
            text.append("Possible duplicate reports: ")
                    .append(duplicates.length()).append("\n");

            for (int i = 0; i < duplicates.length(); i++) {
                JSONObject item = duplicates.optJSONObject(i);
                if (item == null) continue;

                text.append("\nReport ID: ")
                        .append(item.optString("report_id", "Not supplied"))
                        .append("\nConfidence: ")
                        .append(item.optString("confidence", "Not provided"))
                        .append("\nReason: ")
                        .append(item.optString("reason", "Not provided"))
                        .append("\n");
            }
        }

        text.append("\n\nReview possible matches before deciding whether to submit.");
        showAiResult("Duplicate Check", text.toString());
    }

    private void appendJsonValue(
            StringBuilder text, String title, Object value) {
        if (value == null || value == JSONObject.NULL) return;
        text.append(title).append(":\n");

        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                text.append("• ").append(array.opt(i)).append("\n");
            }
        } else {
            text.append(value.toString());
        }
    }

    private void showAiResult(String title, String message) {
        runOnUiThread(() -> {
            cardAiResult.setVisibility(View.VISIBLE);
            tvAiTitle.setText(title);
            tvAiResult.setText(message);
        });
    }

    private void resetAiButton(MaterialButton button, boolean duplicates) {
        runOnUiThread(() -> {
            button.setEnabled(true);
            button.setText(duplicates
                    ? "CHECK DUPLICATE REPORTS"
                    : "ANALYZE SCAM WITH AI");
        });
    }

    private void submitReport() {
        String scamType = spinnerScamType.getText().toString().trim();
        String suspiciousInfo = etSuspiciousInfo.getText() == null
                ? "" : etSuspiciousInfo.getText().toString().trim();
        String description = etDescription.getText() == null
                ? "" : etDescription.getText().toString().trim();

        if (scamType.isEmpty()) {
            spinnerScamType.setError("Select scam type");
            return;
        }
        if (suspiciousInfo.isEmpty()) {
            etSuspiciousInfo.setError("Enter phone, UPI ID, URL or email");
            return;
        }
        if (description.isEmpty()) {
            etDescription.setError("Please describe the scam");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first",
                    Toast.LENGTH_LONG).show();
            return;
        }

        btnSubmitReport.setEnabled(false);
        btnSubmitReport.setText("SUBMITTING...");

        user.getIdToken(false).addOnSuccessListener(tokenResult -> {
            String token = tokenResult.getToken();

            if (token == null || token.isEmpty()) {
                resetSubmitButton();
                Toast.makeText(this, "Could not obtain login token",
                        Toast.LENGTH_LONG).show();
                return;
            }

            String uid = user.getUid();
            Uri evidenceUri = selectedEvidenceUri;
            String evidenceName = selectedEvidenceName;
            String evidenceMime = selectedEvidenceMimeType;

            executorService.execute(() -> {
                try {
                    String reportId = createReport(
                            token, uid, scamType,
                            suspiciousInfo, description);

                    if (evidenceUri != null) {
                        uploadEvidence(token, reportId, evidenceUri,
                                evidenceName, evidenceMime);
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                evidenceUri != null
                                        ? "Report and evidence uploaded successfully"
                                        : "Report submitted successfully",
                                Toast.LENGTH_LONG).show();
                        clearForm();
                    });
                } catch (Exception e) {
                    String message = e.getMessage() == null
                            ? "Unknown error" : e.getMessage();
                    runOnUiThread(() -> Toast.makeText(this,
                            "Submission failed: " + message,
                            Toast.LENGTH_LONG).show());
                } finally {
                    runOnUiThread(this::resetSubmitButton);
                }
            });
        }).addOnFailureListener(e -> {
            resetSubmitButton();
            Toast.makeText(this, "Could not authenticate",
                    Toast.LENGTH_LONG).show();
        });
    }

    private String createReport(
            String token, String uid, String scamType,
            String suspiciousInfo, String description) throws Exception {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(supabaseUrl
                    + "/rest/v1/reports?select=id,firebase_uid");

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);

            connection.setRequestProperty("apikey", supabaseKey);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty(
                    "Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Prefer", "return=representation");

            JSONObject report = new JSONObject();
            report.put("firebase_uid", uid);
            report.put("scam_type", scamType);
            report.put("suspicious_info", suspiciousInfo);
            report.put("description", description);
            report.put("latitude", latitude);
            report.put("longitude", longitude);
            report.put("status", "Pending");

            try (OutputStream output = connection.getOutputStream()) {
                output.write(report.toString()
                        .getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            String response = readResponse(connection, code);

            if (code < 200 || code >= 300) {
                throw new Exception("Report API HTTP "
                        + code + ": " + response);
            }

            JSONArray array = new JSONArray(response);
            if (array.length() == 0) {
                throw new Exception("No report ID returned.");
            }

            String reportId = array.getJSONObject(0).optString("id", "");
            if (reportId.isEmpty() || "null".equals(reportId)) {
                throw new Exception("Server did not return a report ID.");
            }
            return reportId;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void uploadEvidence(
            String token, String reportId, Uri fileUri,
            String fileName, String mimeType) throws Exception {

        if (mimeType == null ||
                (!mimeType.equals("image/jpeg")
                        && !mimeType.equals("image/png")
                        && !mimeType.equals("application/pdf"))) {
            throw new Exception("Only JPG, PNG and PDF files are allowed.");
        }

        byte[] bytes;
        try (InputStream input =
                     getContentResolver().openInputStream(fileUri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            if (input == null) {
                throw new Exception("Could not read selected file.");
            }

            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
                if (buffer.size() > MAX_FILE_SIZE) {
                    throw new Exception("File exceeds the 5 MB limit.");
                }
            }
            bytes = buffer.toByteArray();
        }

        if (bytes.length == 0) {
            throw new Exception("Selected file is empty.");
        }

        JSONObject payload = new JSONObject();
        payload.put("report_id", reportId);
        payload.put("file_name",
                fileName == null ? "evidence_file" : fileName);
        payload.put("mime_type", mimeType);
        payload.put("file_base64", Base64.encodeToString(
                bytes, Base64.NO_WRAP));

        HttpURLConnection connection = null;
        try {
            URL url = new URL(supabaseUrl + "/functions/v1/upload-evidence");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("apikey", supabaseKey);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty(
                    "Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload.toString()
                        .getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            String response = readResponse(connection, code);

            if (code < 200 || code >= 300) {
                throw new Exception("Evidence API HTTP "
                        + code + ": " + response);
            }

            JSONObject result = new JSONObject(response);
            if (!result.optBoolean("success", false)) {
                throw new Exception("Server did not confirm evidence upload.");
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void clearForm() {
        etSuspiciousInfo.setText("");
        etDescription.setText("");
        spinnerScamType.setText("", false);

        selectedEvidenceUri = null;
        selectedEvidenceName = null;
        selectedEvidenceMimeType = null;

        btnEvidence.setText("📎 Add Evidence");
        cardAiResult.setVisibility(View.GONE);
    }

    private void resetSubmitButton() {
        btnSubmitReport.setEnabled(true);
        btnSubmitReport.setText("SUBMIT REPORT");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
}
