package com.example.scamheat;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportVerification extends AppCompatActivity {

    private LinearLayout reportContainer;
    private EditText searchInput;
    private Spinner categorySpinner;
    private Button refreshButton;

    private JSONArray allReports = new JSONArray();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Set<String> updatingReportIds = new HashSet<>();

    private String supabaseUrl;
    private String supabaseKey;
    private boolean loadingReports = false;

    private final String[] categories = {
            "All Categories", "UPI Fraud", "Fake Job", "Phishing",
            "Banking Scam", "Courier Scam", "Digital Arrest",
            "Social Media Scam", "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        supabaseUrl = getString(R.string.supabase_url).replaceAll("/+$", "");
        supabaseKey = getString(R.string.supabase_publishable_key);
        createScreen();
        loadReports();
    }

    private void createScreen() {
        setContentView(R.layout.report_verification);

        reportContainer = findViewById(R.id.llVerificationReports);
        searchInput = findViewById(R.id.etVerificationSearch);
        categorySpinner = findViewById(R.id.spVerificationCategory);
        refreshButton = findViewById(R.id.btnRefreshVerification);

        // Keep the status/navigation bars consistent with the ScamHeat dark UI.
        getWindow().setStatusBarColor(Color.rgb(8, 13, 26));
        getWindow().setNavigationBarColor(Color.rgb(8, 13, 26));
        getWindow().getDecorView().setSystemUiVisibility(0);

        // Configure the category selector with readable dark-theme text.
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, categories) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.BLACK);
                view.setTextSize(14);
                view.setPadding(dp(2), 0, dp(2), 0);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.BLACK);
                view.setTextSize(14);
                view.setPadding(dp(14), dp(12), dp(14), dp(12));
                view.setBackgroundColor(Color.WHITE);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        refreshButton.setOnClickListener(v -> loadReports());

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) { }
        });

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void loadReports() {
        if (isFinishing() || isDestroyed() || loadingReports) return;
        loadingReports = true;
        refreshButton.setEnabled(false);
        refreshButton.setText("Loading...");
        reportContainer.removeAllViews();
        reportContainer.addView(makeText("Loading reports...", 15, Color.rgb(155, 169, 195)));

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(supabaseUrl
                        + "/rest/v1/reports?select=id,scam_type,suspicious_info,description,status,created_at"
                        + "&order=created_at.desc&limit=500");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                addSupabaseHeaders(connection);

                int code = connection.getResponseCode();
                String response = readResponse(connection);
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response);

                JSONArray result = new JSONArray(response);
                runOnUiThread(() -> {
                    loadingReports = false;
                    if (isFinishing() || isDestroyed()) return;
                    allReports = result;
                    refreshButton.setEnabled(true);
                    refreshButton.setText("Refresh Reports");
                    applyFilters();
                });
            } catch (Exception e) {
                String error = e.getMessage();
                runOnUiThread(() -> {
                    loadingReports = false;
                    if (isFinishing() || isDestroyed()) return;
                    refreshButton.setEnabled(true);
                    refreshButton.setText("Refresh Reports");
                    showError("Could not load reports:\n" + (error == null ? "Unknown error." : error));
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void applyFilters() {
        if (reportContainer == null) return;
        reportContainer.removeAllViews();
        String query = searchInput.getText().toString().trim().toLowerCase();
        String selectedCategory = categorySpinner.getSelectedItem() == null
                ? "All Categories" : categorySpinner.getSelectedItem().toString();
        int shown = 0;

        for (int i = 0; i < allReports.length(); i++) {
            JSONObject report = allReports.optJSONObject(i);
            if (report == null) continue;
            String type = report.optString("scam_type", "Other");
            String suspicious = report.optString("suspicious_info", "");
            String description = report.optString("description", "");
            String status = report.optString("status", "Pending");
            boolean categoryMatches = selectedCategory.equals("All Categories")
                    || type.equalsIgnoreCase(selectedCategory);
            String searchable = (type + " " + suspicious + " " + description + " " + status).toLowerCase();
            boolean searchMatches = query.isEmpty() || searchable.contains(query);
            if (categoryMatches && searchMatches) {
                addReportCard(report);
                shown++;
            }
        }
        if (shown == 0) {
            TextView empty = makeText("No reports match your search.", 15, Color.rgb(155, 169, 195));
            empty.setPadding(0, dp(18), 0, dp(18));
            reportContainer.addView(empty);
        }
    }

    private void addReportCard(JSONObject report) {
        String reportId = report.optString("id", "");
        String type = report.optString("scam_type", "Other");
        String suspicious = report.optString("suspicious_info", "");
        String description = report.optString("description", "");
        String status = report.optString("status", "Pending");
        String createdAt = report.optString("created_at", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(makeBackground(Color.rgb(21, 31, 51), Color.rgb(41, 53, 77), 14));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(14);
        reportContainer.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = makeText(type, 18, Color.rgb(245, 247, 255));
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView statusLabel = makeText(status, 12, getStatusColor(status));
        statusLabel.setTypeface(null, Typeface.BOLD);
        statusLabel.setPadding(dp(9), dp(5), dp(9), dp(5));
        statusLabel.setBackground(makeBackground(getStatusBackground(status), Color.TRANSPARENT, 20));
        header.addView(statusLabel);
        card.addView(header);

        addDetail(card, "Scam Type", type);
        addDetail(card, "Suspicious Information", emptyValue(suspicious));
        addDetail(card, "Description", emptyValue(description));
        if (!createdAt.isEmpty()) addDetail(card, "Reported At", createdAt);

        TextView evidenceHeading = makeText("Evidence", 15, Color.rgb(245, 247, 255));
        evidenceHeading.setTypeface(null, Typeface.BOLD);
        evidenceHeading.setPadding(0, dp(12), 0, dp(6));
        card.addView(evidenceHeading);

        ImageView evidenceImage = new ImageView(this);
        evidenceImage.setAdjustViewBounds(true);
        evidenceImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        evidenceImage.setBackground(makeBackground(Color.rgb(17, 26, 43), Color.rgb(41, 53, 77), 10));
        evidenceImage.setPadding(dp(5), dp(5), dp(5), dp(5));
        evidenceImage.setMinimumHeight(dp(100));
        evidenceImage.setContentDescription("Tap to enlarge evidence");
        card.addView(evidenceImage, new LinearLayout.LayoutParams(-1, dp(190)));

        TextView evidenceInfo = makeText("Loading evidence...", 13, Color.rgb(155, 169, 195));
        evidenceInfo.setPadding(0, dp(5), 0, dp(4));
        card.addView(evidenceInfo);

        Button viewEvidenceButton = makeButton("View Evidence Details", Color.rgb(80, 95, 120));
        LinearLayout.LayoutParams evidenceButtonParams = new LinearLayout.LayoutParams(-1, dp(42));
        evidenceButtonParams.topMargin = dp(6);
        card.addView(viewEvidenceButton, evidenceButtonParams);

        evidenceImage.setOnClickListener(v -> {
            if (evidenceImage.getDrawable() instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) evidenceImage.getDrawable()).getBitmap();
                showZoomableImage(bitmap, "Evidence Image");
            } else {
                Toast.makeText(this, "Evidence image is still loading.", Toast.LENGTH_SHORT).show();
            }
        });
        viewEvidenceButton.setOnClickListener(v -> loadEvidence(reportId, null, null, true));
        loadEvidence(reportId, evidenceImage, evidenceInfo, false);

        // AI review button added without changing the existing evidence workflow.
        Button aiReviewButton = makeButton("AI Review", Color.rgb(232, 238, 248));
        aiReviewButton.setTextColor(Color.BLACK);
        aiReviewButton.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams aiReviewParams = new LinearLayout.LayoutParams(-1, dp(44));
        aiReviewParams.topMargin = dp(8);
        card.addView(aiReviewButton, aiReviewParams);
        aiReviewButton.setOnClickListener(v -> requestAiReview(reportId, aiReviewButton));

        if (status.equalsIgnoreCase("Pending")) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button verifyButton = makeButton("Verify", Color.rgb(31, 145, 90));
            Button rejectButton = makeButton("Reject", Color.rgb(200, 55, 65));

            LinearLayout.LayoutParams verifyParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
            verifyParams.topMargin = dp(12);
            verifyParams.rightMargin = dp(5);
            actions.addView(verifyButton, verifyParams);

            LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
            rejectParams.topMargin = dp(12);
            rejectParams.leftMargin = dp(5);
            actions.addView(rejectButton, rejectParams);
            card.addView(actions);

            if (updatingReportIds.contains(reportId)) {
                verifyButton.setEnabled(false);
                rejectButton.setEnabled(false);
                verifyButton.setText("Updating...");
            }
            verifyButton.setOnClickListener(v -> confirmStatusChange(reportId, "Verified"));
            rejectButton.setOnClickListener(v -> confirmStatusChange(reportId, "Rejected"));
        }
    }

    private void addDetail(LinearLayout card, String label, String value) {
        TextView labelView = makeText(label, 13, Color.rgb(155, 169, 195));
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setPadding(0, dp(10), 0, dp(3));
        card.addView(labelView);
        TextView valueView = makeText(value, 14, Color.rgb(245, 247, 255));
        valueView.setTextIsSelectable(true);
        card.addView(valueView);
    }

    private String emptyValue(String value) {
        return value == null || value.trim().isEmpty() || value.equals("null")
                ? "Not provided" : value;
    }

    private void confirmStatusChange(String reportId, String newStatus) {
        if (updatingReportIds.contains(reportId)) return;
        String message = newStatus.equals("Verified")
                ? "Verify this report? This may trigger scam alerts for nearby users if notifications are configured."
                : "Reject this report?";
        new AlertDialog.Builder(this)
                .setTitle(newStatus.equals("Verified") ? "Verify Report" : "Reject Report")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (dialog, which) -> updateStatus(reportId, newStatus))
                .show();
    }

    private void updateStatus(String reportId, String newStatus) {
        if (updatingReportIds.contains(reportId)) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
            return;
        }

        updatingReportIds.add(reportId);
        applyFilters();

        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                updatingReportIds.remove(reportId);
                applyFilters();
                Toast.makeText(this, "Could not get authentication token.", Toast.LENGTH_LONG).show();
                return;
            }

            String firebaseToken = task.getResult().getToken();
            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(supabaseUrl + "/functions/v1/admin-update-report");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(20000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    addSupabaseHeaders(connection);

                    JSONObject body = new JSONObject();
                    body.put("report_id", reportId);
                    body.put("status", newStatus);
                    body.put("firebase_id_token", firebaseToken);
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }

                    int code = connection.getResponseCode();
                    String response = readResponse(connection);
                    if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response);
                    JSONObject result = new JSONObject(response);
                    if (!result.optBoolean("success", false)) {
                        throw new Exception(result.optString("error", "Status update was not confirmed."));
                    }

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        updatingReportIds.remove(reportId);
                        for (int i = 0; i < allReports.length(); i++) {
                            JSONObject item = allReports.optJSONObject(i);
                            if (item != null && reportId.equals(item.optString("id", ""))) {
                                item.remove("status");
                                try {
                                    item.put("status", newStatus);
                                } catch (Exception ignored) { }
                                break;
                            }
                        }
                        applyFilters();
                        Toast.makeText(this, "Report " + newStatus.toLowerCase() + " successfully.",
                                Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    String error = e.getMessage();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        updatingReportIds.remove(reportId);
                        applyFilters();
                        new AlertDialog.Builder(this).setTitle("Status Update Failed")
                                .setMessage(error == null ? "Unknown error." : error)
                                .setPositiveButton("OK", null).show();
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        });
    }

    // Calls the existing scam-ai-admin-review Supabase Edge Function.
    private void requestAiReview(String reportId, Button button) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
            return;
        }

        button.setEnabled(false);
        button.setText("Reviewing...");

        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                button.setEnabled(true);
                button.setText("AI Review");
                Toast.makeText(this, "Could not get authentication token.", Toast.LENGTH_LONG).show();
                return;
            }

            String firebaseToken = task.getResult().getToken();
            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(supabaseUrl + "/functions/v1/scam-ai-admin-review");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(60000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    addSupabaseHeaders(connection);
                    // AI review is authenticated with the signed-in Firebase user token.
                    // Override the default Supabase-key Authorization header for this request only.
                    connection.setRequestProperty("Authorization", "Bearer " + firebaseToken);

                    JSONObject body = new JSONObject();
                    body.put("report_id", reportId);
                    body.put("firebase_id_token", firebaseToken);
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }

                    int code = connection.getResponseCode();
                    String response = readResponse(connection);
                    if (code < 200 || code >= 300) {
                        throw new Exception("HTTP " + code + ": " + response);
                    }

                    JSONObject result = new JSONObject(response);
                    if (result.has("success") && !result.optBoolean("success", true)) {
                        throw new Exception(result.optString("error", "AI review failed."));
                    }
                    String readableReview = formatAiReview(result);

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        button.setEnabled(true);
                        button.setText("AI Review");
                        showAiReviewDialog(readableReview);
                    });
                } catch (Exception e) {
                    String error = e.getMessage();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        button.setEnabled(true);
                        button.setText("AI Human-Readable Review");
                        new AlertDialog.Builder(this)
                                .setTitle("AI Review Failed")
                                .setMessage(error == null ? "Unknown error." : error)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        });
    }

    // Converts the Edge Function response into readable sections instead of raw JSON.
    private String formatAiReview(JSONObject response) {
        JSONObject data = unwrapAiResponse(response);
        StringBuilder out = new StringBuilder();
        out.append("AI SCAM ASSESSMENT\n\n");

        appendAiField(out, "Risk Level", findAiValue(data,
                "risk_level", "riskLevel", "risk"));
        appendAiField(out, "Risk Score", findAiValue(data,
                "risk_score", "riskScore"));
        appendAiField(out, "Overall Assessment", findAiValue(data,
                "assessment", "summary", "overall_assessment", "overallAssessment",
                "explanation", "analysis_text"));
        appendAiField(out, "Recommendation", findAiValue(data,
                "recommendation", "recommended_action", "recommendedAction", "decision"));

        out.append("WARNING SIGNS\n");
        appendAiValue(out, findAiValue(data,
                "warning_signs", "warningSigns", "red_flags", "redFlags",
                "key_findings", "keyFindings", "indicators"));

        out.append("\nEVIDENCE REVIEW\n");
        appendAiValue(out, findAiValue(data,
                "evidence_review", "evidenceReview", "evidence_analysis",
                "evidenceAnalysis", "evidence_summary", "evidenceSummary"));

        out.append("\nRECOMMENDED NEXT STEPS\n");
        appendAiValue(out, findAiValue(data,
                "recommendations", "next_steps", "nextSteps", "actions", "suggested_actions"));

        if (!hasAiReviewContent(data)) {
            // Some models return their complete explanation in a single text field.
            Object text = findAiValue(data, "message", "content", "review_text", "reviewText",
                    "output_text", "text");
            if (text != null) {
                out.append("\nAI REVIEW\n");
                appendAiValue(out, text);
            } else {
                out.append("\nThe AI function returned a response, but its review fields were not recognised.\n")
                        .append("Please check the response structure from scam-ai-admin-review.\n");
            }
        }

        out.append("\nIMPORTANT\n")
                .append("This AI assessment is advisory, not proof of fraud. Review the original report and evidence before making the final decision.");
        return out.toString();
    }

    private JSONObject unwrapAiResponse(JSONObject response) {
        JSONObject current = response;
        String[] wrappers = {"data", "result", "review", "ai_review", "ai_analysis", "analysis"};
        for (int i = 0; i < 5; i++) {
            JSONObject next = null;
            for (String key : wrappers) {
                Object value = current.opt(key);
                if (value instanceof JSONObject) {
                    next = (JSONObject) value;
                    break;
                }
                if (value instanceof String) {
                    String candidate = ((String) value).trim();
                    if (candidate.startsWith("{") && candidate.endsWith("}")) {
                        try {
                            next = new JSONObject(candidate);
                            break;
                        } catch (Exception ignored) { }
                    }
                }
            }
            if (next == null) break;
            current = next;
        }
        return current;
    }

    private Object findAiValue(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value != null && value != JSONObject.NULL) {
                if (value instanceof String && ((String) value).trim().isEmpty()) continue;
                return value;
            }
        }
        return null;
    }

    private void appendAiField(StringBuilder out, String label, Object value) {
        if (value == null || value == JSONObject.NULL
                || value instanceof JSONObject || value instanceof JSONArray) return;
        out.append(label).append("\n")
                .append(humanizeAiText(String.valueOf(value))).append("\n\n");
    }

    private void appendAiValue(StringBuilder out, Object value) {
        if (value == null || value == JSONObject.NULL) {
            out.append("No details were provided by the AI.\n");
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() == 0) {
                out.append("No details were provided by the AI.\n");
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item == null || item == JSONObject.NULL) continue;
                if (item instanceof JSONObject) {
                    JSONObject itemObject = (JSONObject) item;
                    Object description = findAiValue(itemObject,
                            "description", "finding", "reason", "message", "text", "name", "detail");
                    out.append("• ").append(description != null
                            ? humanizeAiText(String.valueOf(description))
                            : formatAiObject(itemObject)).append("\n");
                } else {
                    out.append("• ").append(humanizeAiText(String.valueOf(item))).append("\n");
                }
            }
            return;
        }
        if (value instanceof JSONObject) {
            out.append(formatAiObject((JSONObject) value)).append("\n");
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                out.append(formatAiObject(new JSONObject(text))).append("\n");
                return;
            } catch (Exception ignored) { }
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                appendAiValue(out, new JSONArray(text));
                return;
            } catch (Exception ignored) { }
        }
        out.append("• ").append(humanizeAiText(text)).append("\n");
    }

    private String formatAiObject(JSONObject object) {
        StringBuilder out = new StringBuilder();
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            if (value instanceof JSONObject || value instanceof JSONArray) continue;
            if (out.length() > 0) out.append("\n");
            out.append(humanizeAiKey(key)).append(": ")
                    .append(humanizeAiText(String.valueOf(value)));
        }
        return out.length() == 0 ? "Additional details were returned." : out.toString();
    }

    private boolean hasAiReviewContent(JSONObject data) {
        String[] keys = {"risk_level", "riskLevel", "risk_score", "riskScore", "assessment",
                "summary", "overall_assessment", "overallAssessment", "explanation",
                "recommendation", "recommended_action", "decision", "warning_signs", "red_flags",
                "key_findings", "evidence_review", "evidence_analysis", "recommendations",
                "next_steps", "actions", "message", "content", "review_text", "reviewText",
                "output_text", "text"};
        for (String key : keys) {
            Object value = data.opt(key);
            if (value != null && value != JSONObject.NULL
                    && !(value instanceof String && ((String) value).trim().isEmpty())) return true;
        }
        return false;
    }

    private String humanizeAiKey(String key) {
        String label = key.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ').replace('-', ' ').trim();
        if (label.isEmpty()) return key;
        return label.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + label.substring(1);
    }

    private String humanizeAiText(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + value.substring(1);
    }


    private void showAiReviewDialog(String review) {
        int black = Color.BLACK;
        int white = Color.WHITE;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(16));
        content.setBackgroundColor(white);

        TextView heading = makeText(
                "AI SCAM ASSESSMENT", 17, black
        );
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(220, 225, 233));

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(12), 0, dp(12));
        content.addView(divider, dividerParams);

        TextView reviewText = makeText(review, 14, black);
        reviewText.setTextIsSelectable(true);
        reviewText.setLineSpacing(dp(3), 1.0f);
        content.addView(reviewText);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(white);
        scroll.setFillViewport(false);
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("AI Review")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(white)
                );
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(Color.rgb(201, 31, 67));

            int titleId = getResources().getIdentifier(
                    "alertTitle", "id", "android"
            );

            TextView titleView = dialog.findViewById(titleId);
            if (titleView != null) {
                titleView.setTextColor(black);
            }
        });

        dialog.show();
    }


    private void loadEvidence(String reportId, ImageView preview, TextView info, boolean showDialog) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (info != null) info.setText("Sign in to view evidence.");
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
            return;
        }

        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                if (info != null) info.setText("Could not authenticate to load evidence.");
                return;
            }
            String token = task.getResult().getToken();

            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(supabaseUrl + "/functions/v1/admin-get-report-evidence");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(30000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    addSupabaseHeaders(connection);

                    JSONObject body = new JSONObject();
                    body.put("report_id", reportId);
                    body.put("firebase_id_token", token);
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }

                    int code = connection.getResponseCode();
                    String response = readResponse(connection);
                    if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response);
                    JSONObject result = new JSONObject(response);
                    if (!result.optBoolean("success", false)) {
                        throw new Exception(result.optString("error", "Could not retrieve evidence."));
                    }
                    JSONArray evidence = result.optJSONArray("evidence");
                    if (evidence == null) evidence = new JSONArray();
                    JSONArray finalEvidence = evidence;

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (finalEvidence.length() == 0) {
                            if (info != null) info.setText("No evidence attached.");
                            if (showDialog) {
                                new AlertDialog.Builder(this).setTitle("Evidence")
                                        .setMessage("No evidence was attached to this report.")
                                        .setPositiveButton("OK", null).show();
                            }
                            return;
                        }

                        JSONObject first = finalEvidence.optJSONObject(0);
                        if (first == null) return;
                        String fileName = first.optString("file_name", "Evidence");
                        String mimeType = first.optString("mime_type", "application/octet-stream");
                        String fileData = first.optString("file_data", "");
                        byte[] bytes;
                        try {
                            bytes = decodeBytea(fileData);
                        } catch (Exception ex) {
                            if (info != null) info.setText("Could not decode evidence data.");
                            if (showDialog) showError("Could not decode evidence:\n" + ex.getMessage());
                            return;
                        }

                        if (info != null) info.setText(fileName + " • " + mimeType + " • " + bytes.length + " bytes");
                        if (mimeType.toLowerCase().startsWith("image/")) {
                            Bitmap bitmap = decodeImage(bytes, 2400);
                            if (bitmap != null && preview != null) {
                                preview.setImageBitmap(bitmap);
                                preview.setVisibility(View.VISIBLE);
                            } else if (preview != null) preview.setVisibility(View.GONE);
                        } else if (preview != null) {
                            preview.setVisibility(View.GONE);
                            if (info != null) info.setText(fileName + " • File is not an image");
                        }
                        if (showDialog) showEvidenceDialog(finalEvidence);
                    });
                } catch (Exception e) {
                    String message = e.getMessage();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (info != null) info.setText("Evidence could not be loaded.");
                        if (showDialog) {
                            new AlertDialog.Builder(this).setTitle("Evidence Load Failed")
                                    .setMessage(message == null ? "Unknown error." : message)
                                    .setPositiveButton("OK", null).show();
                        }
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        });
    }

    private void showEvidenceDialog(JSONArray evidence) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);

        for (int i = 0; i < evidence.length(); i++) {
            JSONObject item = evidence.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("file_name", "Evidence");
            String mime = item.optString("mime_type", "application/octet-stream");
            String data = item.optString("file_data", "");
            TextView metadata = makeText(name + "\n" + mime, 14, Color.rgb(155, 169, 195));
            metadata.setPadding(0, dp(10), 0, dp(8));
            layout.addView(metadata);

            try {
                byte[] bytes = decodeBytea(data);
                if (mime.toLowerCase().startsWith("image/")) {
                    Bitmap bitmap = decodeImage(bytes, 2400);
                    if (bitmap != null) {
                        ImageView image = new ImageView(this);
                        image.setImageBitmap(bitmap);
                        image.setAdjustViewBounds(true);
                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        image.setOnClickListener(v -> showZoomableImage(bitmap, name));
                        layout.addView(image, new LinearLayout.LayoutParams(-1, dp(220)));
                        layout.addView(makeText("Tap image to enlarge and zoom", 12, Color.rgb(155, 169, 195)));
                    } else layout.addView(makeText("Image preview unavailable.", 13, Color.rgb(155, 169, 195)));
                } else {
                    layout.addView(makeText("This file type cannot be previewed as an image.", 13, Color.rgb(155, 169, 195)));
                }
            } catch (Exception e) {
                layout.addView(makeText("Unable to display this evidence file.", 13, Color.RED));
            }
        }
        new AlertDialog.Builder(this).setTitle("Report Evidence")
                .setView(scroll).setPositiveButton("Close", null).show();
    }

    private void showZoomableImage(Bitmap bitmap, String title) {
        if (bitmap == null) {
            Toast.makeText(this, "Image unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        ZoomImageView zoomImage = new ZoomImageView(this);
        zoomImage.setImageBitmap(bitmap);
        zoomImage.setBackgroundColor(Color.BLACK);
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.BLACK);
        container.addView(zoomImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                .setView(container).setPositiveButton("Close", null).create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
            zoomImage.post(zoomImage::fitImage);
        });
        dialog.show();
    }

    private class ZoomImageView extends androidx.appcompat.widget.AppCompatImageView {
        private final Matrix matrix = new Matrix();
        private final float[] values = new float[9];
        private final ScaleGestureDetector scaleDetector;
        private float minimumScale = 1f, maximumScale = 5f;
        private float lastX, lastY;
        private boolean dragging;

        ZoomImageView(android.content.Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.MATRIX);
            setImageMatrix(matrix);
            setBackgroundColor(Color.BLACK);
            scaleDetector = new ScaleGestureDetector(context,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(ScaleGestureDetector detector) {
                            float currentScale = getCurrentScale();
                            float factor = detector.getScaleFactor();
                            float newScale = currentScale * factor;
                            if (newScale < minimumScale) factor = minimumScale / currentScale;
                            else if (newScale > maximumScale) factor = maximumScale / currentScale;
                            matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                            constrainTranslation();
                            setImageMatrix(matrix);
                            return true;
                        }
                    });
        }

        void fitImage() {
            Bitmap bitmap = getDrawable() instanceof BitmapDrawable
                    ? ((BitmapDrawable) getDrawable()).getBitmap() : null;
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
            float viewWidth = getWidth(), viewHeight = getHeight();
            float scale = Math.min(viewWidth / bitmap.getWidth(), viewHeight / bitmap.getHeight());
            minimumScale = scale;
            maximumScale = scale * 5f;
            float left = (viewWidth - bitmap.getWidth() * scale) / 2f;
            float top = (viewHeight - bitmap.getHeight() * scale) / 2f;
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(left, top);
            setImageMatrix(matrix);
        }

        private float getCurrentScale() {
            matrix.getValues(values);
            return (float) Math.sqrt(values[Matrix.MSCALE_X] * values[Matrix.MSCALE_X]
                    + values[Matrix.MSKEW_Y] * values[Matrix.MSKEW_Y]);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX(); lastY = event.getY(); dragging = true; return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    dragging = false; return true;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && dragging && event.getPointerCount() == 1) {
                        float dx = event.getX() - lastX, dy = event.getY() - lastY;
                        if (getCurrentScale() > minimumScale * 1.01f) {
                            matrix.postTranslate(dx, dy);
                            constrainTranslation();
                            setImageMatrix(matrix);
                        }
                        lastX = event.getX(); lastY = event.getY();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    dragging = false; return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false; performClick(); return true;
                default: return true;
            }
        }

        private void constrainTranslation() {
            Bitmap bitmap = getDrawable() instanceof BitmapDrawable
                    ? ((BitmapDrawable) getDrawable()).getBitmap() : null;
            if (bitmap == null) return;
            matrix.getValues(values);
            float scale = getCurrentScale();
            float imageWidth = bitmap.getWidth() * scale, imageHeight = bitmap.getHeight() * scale;
            float tx = values[Matrix.MTRANS_X], ty = values[Matrix.MTRANS_Y];
            float minX = Math.min(0, getWidth() - imageWidth);
            float minY = Math.min(0, getHeight() - imageHeight);
            float maxX = Math.max(0, (getWidth() - imageWidth) / 2f);
            float maxY = Math.max(0, (getHeight() - imageHeight) / 2f);
            tx = Math.max(minX, Math.min(maxX, tx));
            ty = Math.max(minY, Math.min(maxY, ty));
            matrix.getValues(values);
            values[Matrix.MTRANS_X] = tx;
            values[Matrix.MTRANS_Y] = ty;
            matrix.setValues(values);
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private byte[] decodeBytea(String value) throws Exception {
        if (value == null || value.isEmpty() || value.equals("null")) return new byte[0];
        if (value.startsWith("\\x") || value.startsWith("\\X")) {
            String hex = value.substring(2);
            if (hex.length() % 2 != 0) throw new Exception("Invalid hex evidence data.");
            byte[] result = new byte[hex.length() / 2];
            for (int i = 0; i < result.length; i++) {
                int high = Character.digit(hex.charAt(i * 2), 16);
                int low = Character.digit(hex.charAt(i * 2 + 1), 16);
                if (high < 0 || low < 0) throw new Exception("Invalid hex evidence data.");
                result[i] = (byte) ((high << 4) + low);
            }
            return result;
        }
        try {
            return Base64.decode(value, Base64.DEFAULT);
        } catch (Exception ignored) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Bitmap decodeImage(byte[] bytes, int maxDimension) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > maxDimension
                || bounds.outHeight / options.inSampleSize > maxDimension) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private void addSupabaseHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("apikey", supabaseKey);
        connection.setRequestProperty("Authorization", "Bearer " + supabaseKey);
        connection.setRequestProperty("Accept", "application/json");
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        InputStream stream;
        int code = connection.getResponseCode();
        stream = (code >= 200 && code < 400) ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "";
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        }
        return response.toString();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(this).setTitle("Error").setMessage(message)
                    .setPositiveButton("OK", null).show();
        });
    }

    private int getStatusColor(String status) {
        if ("Verified".equalsIgnoreCase(status)) return Color.rgb(25, 130, 75);
        if ("Rejected".equalsIgnoreCase(status)) return Color.rgb(190, 45, 55);
        return Color.rgb(185, 115, 15);
    }

    private int getStatusBackground(String status) {
        if ("Verified".equalsIgnoreCase(status)) return Color.rgb(225, 245, 233);
        if ("Rejected".equalsIgnoreCase(status)) return Color.rgb(255, 232, 233);
        return Color.rgb(255, 244, 218);
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(21, 31, 51));
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(makeBackground(color, Color.TRANSPARENT, 10));
        return button;
    }

    private TextView makeText(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable makeBackground(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
