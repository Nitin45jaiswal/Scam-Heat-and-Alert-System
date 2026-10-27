
package com.example.scamheat;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScamCheck extends AppCompatActivity {

    private AutoCompleteTextView actCheckType;
    private TextInputEditText etCheckValue;
    private TextInputLayout inputCheckValue;
    private MaterialButton btnCheck;
    private MaterialCardView cardResult;
    private TextView tvResultIcon, tvResultTitle;
    private TextView tvResultMessage, tvReportCount;

    private String supabaseUrl;
    private String supabaseKey;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final String[] checkTypes = {
            "Phone Number", "UPI ID", "URL", "Email"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scam_check);

        supabaseUrl = getString(R.string.supabase_url)
                .replaceAll("/+$", "");
        supabaseKey = getString(R.string.supabase_publishable_key);

        actCheckType = findViewById(R.id.actCheckType);
        etCheckValue = findViewById(R.id.etCheckValue);
        inputCheckValue = findViewById(R.id.inputCheckValue);
        btnCheck = findViewById(R.id.btnCheck);
        cardResult = findViewById(R.id.cardResult);
        tvResultIcon = findViewById(R.id.tvResultIcon);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultMessage = findViewById(R.id.tvResultMessage);
        tvReportCount = findViewById(R.id.tvReportCount);

        setupCheckTypeDropdown();

        updateHint();
        btnCheck.setOnClickListener(v -> checkScam());
    }

    /**
     * Creates a dropdown with white text and a dark background.
     */
    private void setupCheckTypeDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                ScamCheck.this,
                android.R.layout.simple_dropdown_item_1line,
                checkTypes
        ) {
            @Override
            public View getView(
                    int position,
                    View convertView,
                    ViewGroup parent
            ) {
                TextView item = (TextView) super.getView(
                        position, convertView, parent
                );

                item.setTextColor(Color.WHITE);
                return item;
            }

            @Override
            public View getDropDownView(
                    int position,
                    View convertView,
                    ViewGroup parent
            ) {
                TextView item = (TextView) super.getDropDownView(
                        position, convertView, parent
                );

                item.setTextColor(Color.WHITE);
                item.setBackgroundColor(Color.rgb(21, 31, 51));
                item.setTextSize(15);

                int horizontal = dp(16);
                int vertical = dp(14);

                item.setPadding(
                        horizontal,
                        vertical,
                        horizontal,
                        vertical
                );

                return item;
            }
        };

        actCheckType.setAdapter(adapter);
        actCheckType.setText(checkTypes[0], false);

        actCheckType.setOnClickListener(
                v -> actCheckType.showDropDown()
        );

        actCheckType.setOnItemClickListener(
                (parent, view, position, id) -> {
                    actCheckType.setText(checkTypes[position], false);
                    updateHint();
                }
        );
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private void updateHint() {
        String type = actCheckType.getText().toString().trim();

        switch (type) {
            case "Phone Number":
                inputCheckValue.setHint("Enter phone number");
                etCheckValue.setInputType(InputType.TYPE_CLASS_PHONE);
                break;

            case "UPI ID":
                inputCheckValue.setHint("Enter UPI ID");
                etCheckValue.setInputType(InputType.TYPE_CLASS_TEXT);
                break;

            case "URL":
                inputCheckValue.setHint("Enter suspicious URL");
                etCheckValue.setInputType(
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_VARIATION_URI
                );
                break;

            case "Email":
                inputCheckValue.setHint("Enter email address");
                etCheckValue.setInputType(
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                );
                break;
        }
    }

    private boolean validate(String type, String value) {
        switch (type) {
            case "Phone Number": {
                String digits = value.replaceAll("[^0-9]", "");

                if (digits.length() < 10 || digits.length() > 12) {
                    etCheckValue.setError("Enter a valid phone number");
                    return false;
                }
                break;
            }

            case "UPI ID":
                if (!value.matches("^[^\\s@]+@[^\\s@]+$")) {
                    etCheckValue.setError("Enter a valid UPI ID");
                    return false;
                }
                break;

            case "Email":
                if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                    etCheckValue.setError("Enter a valid email address");
                    return false;
                }
                break;

            case "URL": {
                String url = value.matches("(?i)^https?://.*")
                        ? value : "https://" + value;

                if (!Patterns.WEB_URL.matcher(url).matches()) {
                    etCheckValue.setError("Enter a valid URL");
                    return false;
                }
                break;
            }

            default:
                return false;
        }

        return true;
    }

    private void checkScam() {
        String value = etCheckValue.getText() == null
                ? ""
                : etCheckValue.getText().toString().trim();

        String selected = actCheckType.getText().toString().trim();

        if (value.isEmpty()) {
            etCheckValue.setError("Please enter details");
            return;
        }

        if (!validate(selected, value)) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            showError("Please sign in before using ScamCheck AI.");
            return;
        }

        String apiType;

        switch (selected) {
            case "Phone Number":
                apiType = "phone";
                break;

            case "UPI ID":
                apiType = "upi";
                break;

            case "URL":
                apiType = "url";
                break;

            case "Email":
                apiType = "email";
                break;

            default:
                showError("Select a valid check type.");
                return;
        }

        btnCheck.setEnabled(false);
        btnCheck.setText("CHECKING...");
        cardResult.setVisibility(View.GONE);

        final String requestType = apiType;
        final String requestValue = value;

        user.getIdToken(false)
                .addOnSuccessListener(tokenResult -> {
                    String token = tokenResult.getToken();

                    if (token == null || token.isEmpty()) {
                        showError(
                                "Unable to get your login token. Sign in again."
                        );
                        resetButton();
                        return;
                    }

                    executor.execute(() ->
                            callCheckFunction(
                                    requestType,
                                    requestValue,
                                    token
                            )
                    );
                })
                .addOnFailureListener(e -> {
                    showError(
                            "Authentication failed. Please sign in again."
                    );
                    resetButton();
                });
    }

    private void callCheckFunction(
            String type,
            String value,
            String firebaseToken
    ) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                    supabaseUrl + "/functions/v1/scam-ai-check"
            );

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setDoOutput(true);

            connection.setRequestProperty("apikey", supabaseKey);
            connection.setRequestProperty(
                    "Authorization", "Bearer " + firebaseToken
            );
            connection.setRequestProperty(
                    "Content-Type", "application/json; charset=UTF-8"
            );
            connection.setRequestProperty("Accept", "application/json");

            JSONObject request = new JSONObject();
            request.put("type", type);
            request.put("value", value);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(
                        request.toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            int code = connection.getResponseCode();
            String body = readResponse(connection, code);

            if (code < 200 || code >= 300) {
                String message = "AI check failed (HTTP " + code + ").";

                try {
                    JSONObject error = new JSONObject(body);
                    String detail = error.optString(
                            "error",
                            error.optString("message", "")
                    );

                    if (!detail.isEmpty()) {
                        message += "\n" + detail;
                    }
                } catch (Exception ignored) {
                    // Use the HTTP error message if the body is not JSON.
                }

                final String errorMessage = message;
                runOnUiThread(() -> showError(errorMessage));
                return;
            }

            JSONObject result = new JSONObject(body);

            if (!result.optBoolean("success", false)) {
                runOnUiThread(() -> showError(
                        "The AI function returned an unsuccessful result."
                ));
                return;
            }

            JSONArray reports =
                    result.optJSONArray("matching_reports");

            int count = result.optInt(
                    "exact_match_count",
                    reports == null ? 0 : reports.length()
            );

            Object aiObject = result.opt("ai_analysis");
            String aiText = "";

            if (aiObject instanceof String) {
                aiText = (String) aiObject;
            } else if (aiObject instanceof JSONObject
                    || aiObject instanceof JSONArray) {
                aiText = aiObject.toString();
            }

            final String analysis = aiText;
            final JSONArray matches = reports;
            final int reportCount = count;

            runOnUiThread(() ->
                    showResult(type, reportCount, analysis, matches)
            );

        } catch (Exception e) {
            runOnUiThread(() -> showError(
                    "Connection error. Check your internet connection."
            ));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }

            runOnUiThread(this::resetButton);
        }
    }

    private String readResponse(
            HttpURLConnection connection,
            int code
    ) throws Exception {
        InputStream stream = code >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();

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

    private void showResult(
            String type,
            int count,
            String analysis,
            JSONArray reports
    ) {
        cardResult.setVisibility(View.VISIBLE);

        tvResultIcon.setText(count > 0 ? "🔴" : "🔎");
        tvResultTitle.setText(
                count > 0
                        ? "Matching Reports Found"
                        : "No Exact Match Found"
        );

        StringBuilder message = new StringBuilder();

        message.append("Identifier type: ")
                .append(type)
                .append("\n\n");

        message.append("Exact matching reports: ")
                .append(count)
                .append("\n\n");

        if (reports != null && reports.length() > 0) {
            message.append("Matching reports:\n");

            for (int i = 0; i < reports.length(); i++) {
                JSONObject report = reports.optJSONObject(i);

                if (report == null) {
                    continue;
                }

                message.append("\n• Category: ")
                        .append(report.optString(
                                "scam_type", "Not specified"
                        ))
                        .append("\n  Status: ")
                        .append(report.optString("status", "Unknown"));

                String description =
                        report.optString("description", "");

                if (!description.isEmpty()) {
                    message.append("\n  Details: ")
                            .append(description.replace("**", ""));
                }

                message.append("\n");
            }

            message.append("\n");
        }

        if (analysis != null && !analysis.trim().isEmpty()) {
            message.append("AI Analysis:\n")
                    .append(analysis.trim().replace("**", ""));
        } else {
            message.append("No AI explanation was returned.");
        }

        message.append(
                "\n\nAI results are advisory. No match does not prove safety."
        );

        tvResultMessage.setText(message.toString());
        tvReportCount.setText("Matching reports: " + count);
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            cardResult.setVisibility(View.VISIBLE);
            tvResultIcon.setText("⚠️");
            tvResultTitle.setText("Check Failed");
            tvResultMessage.setText(message);
            tvReportCount.setText("");
        });
    }

    private void resetButton() {
        runOnUiThread(() -> {
            btnCheck.setEnabled(true);
            btnCheck.setText("CHECK WITH AI");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
