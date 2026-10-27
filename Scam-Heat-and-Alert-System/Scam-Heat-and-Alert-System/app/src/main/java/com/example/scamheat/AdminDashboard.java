
package com.example.scamheat;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminDashboard extends AppCompatActivity {

    private TextView tvTotalReports;
    private TextView tvPendingReports;
    private TextView tvVerifiedReports;
    private TextView tvRejectedReports;
    private TextView tvChartTotal;
    private TextView tvAdminEmail;
    private TextView tvStatus;

    private AdminDonutView adminDonutView;
    private LinearLayout root;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private String supabaseUrl;
    private String supabaseKey;

    // ScamHeat theme
    private static final int BG = Color.rgb(8, 13, 26);
    private static final int BG_DARK = Color.rgb(5, 9, 20);
    private static final int SURFACE = Color.rgb(17, 26, 43);
    private static final int CARD = Color.rgb(21, 31, 51);
    private static final int HEADER = Color.rgb(13, 21, 38);
    private static final int BORDER = Color.rgb(41, 53, 77);

    private static final int RED = Color.rgb(255, 49, 85);
    private static final int ORANGE = Color.rgb(255, 138, 61);
    private static final int GREEN = Color.rgb(54, 224, 161);
    private static final int BLUE = Color.rgb(57, 167, 255);

    private static final int WHITE = Color.rgb(245, 247, 255);
    private static final int SECONDARY = Color.rgb(155, 169, 195);
    private static final int MUTED = Color.rgb(101, 115, 140);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supabaseUrl = getString(R.string.supabase_url);
        supabaseKey = getString(R.string.supabase_publishable_key);

        buildDashboard();
        loadReportCounts();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tvStatus != null) {
            loadReportCounts();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // =========================================================
    // DASHBOARD UI
    // =========================================================

    private void buildDashboard() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(20), dp(18), dp(24));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(22), dp(20), dp(22));
        header.setBackground(panelBackground(RED, 22, RED));

        TextView brand = text("SCAMHEAT", 12, WHITE, true);
        brand.setLetterSpacing(0.18f);
        header.addView(brand);

        addSpace(header, 9);

        header.addView(text(
                "Admin Dashboard", 27, WHITE, true));

        addSpace(header, 5);

        header.addView(text(
                "Scam Heat and Alert System",
                14, WHITE, false));

        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        String email = "Administrator account";

        if (currentUser != null && currentUser.getEmail() != null) {
            email = currentUser.getEmail();
        }

        tvAdminEmail = text(email, 12, WHITE, false);
        addSpace(header, 15);
        header.addView(tvAdminEmail);

        content.addView(header);
        addSpace(content, 25);

        addSectionTitle(content, "Dashboard Overview",
                "A live snapshot of submitted reports");

        LinearLayout row1 = horizontalRow();

        row1.addView(makeMetricCard(
                "TOTAL REPORTS", "0", 0), weightedParams());

        addSpaceHorizontal(row1, 10);

        row1.addView(makeMetricCard(
                "PENDING", "0", 1), weightedParams());

        content.addView(row1);
        addSpace(content, 10);

        LinearLayout row2 = horizontalRow();

        row2.addView(makeMetricCard(
                "VERIFIED", "0", 2), weightedParams());

        addSpaceHorizontal(row2, 10);

        row2.addView(makeMetricCard(
                "REJECTED", "0", 3), weightedParams());

        content.addView(row2);
        addSpace(content, 22);

        // Chart card
        LinearLayout chartCard = card();

        chartCard.addView(text(
                "Report Status", 19, WHITE, true));

        addSpace(chartCard, 6);

        chartCard.addView(text(
                "Distribution of submitted scam reports",
                13, SECONDARY, false));

        addSpace(chartCard, 18);

        LinearLayout chartRow = horizontalRow();
        chartRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout chartContainer = new FrameLayout(this);

        adminDonutView = new AdminDonutView(this, null);

        FrameLayout.LayoutParams donutParams =
                new FrameLayout.LayoutParams(
                        dp(145), dp(145), Gravity.CENTER);

        chartContainer.addView(adminDonutView, donutParams);

        tvChartTotal = text("0", 24, WHITE, true);
        tvChartTotal.setGravity(Gravity.CENTER);

        chartContainer.addView(tvChartTotal,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        chartRow.addView(chartContainer,
                new LinearLayout.LayoutParams(dp(145), dp(145)));

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setPadding(dp(12), 0, 0, 0);

        addLegend(legend, "Pending", ORANGE);
        addLegend(legend, "Verified", GREEN);
        addLegend(legend, "Rejected", MUTED);

        chartRow.addView(legend,
                new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        chartCard.addView(chartRow);
        addSpace(chartCard, 16);

        tvStatus = text(
                "Loading report data...",
                12, SECONDARY, false);

        chartCard.addView(tvStatus);

        content.addView(chartCard);
        addSpace(content, 25);

        addSectionTitle(content, "Management",
                "Tools for reviewing activity and reports");

        addMenuCard(content,
                "AI Scam Intelligence",
                "Analyze report trends and recurring scam patterns",
                "AI", RED,
                () -> runAdminAi("scam-ai-intelligence"));

        addMenuCard(content,
                "Report Verification",
                "Review submitted reports and their evidence",
                "✓", ORANGE,
                () -> openScreen(ReportVerification.class));

        addMenuCard(content,
                "Scam Statistics",
                "Explore report trends and scam categories",
                "▥", BLUE,
                () -> openScreen(ScamStatistics.class));

        addMenuCard(content,
                "User & Report Management",
                "Access user and report management tools",
                "♙", GREEN,
                () -> openScreen(UserManagement.class));

        addSpace(content, 12);

        TextView refresh = text(
                "↻   Refresh Dashboard", 15, WHITE, true);

        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(dp(14), dp(16), dp(14), dp(16));
        refresh.setBackground(panelBackground(SURFACE, 14, BORDER));
        refresh.setOnClickListener(v -> loadReportCounts());

        content.addView(refresh);

        pageScroll.addView(content);

        root.addView(pageScroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1));

        // Bottom navigation
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(5), dp(10), dp(5), dp(10));
        bottomNav.setBackground(panelBackground(
                HEADER, 0, BORDER));

        addNavButton(bottomNav, "Dashboard", RED,
                this::loadReportCounts);

        addNavButton(bottomNav, "Reports", SECONDARY,
                () -> openScreen(ReportVerification.class));

        addNavButton(bottomNav, "Statistics", SECONDARY,
                () -> openScreen(ScamStatistics.class));

        addNavButton(bottomNav, "Users", SECONDARY,
                () -> openScreen(UserManagement.class));

        root.addView(bottomNav);
        setContentView(root);

        tvTotalReports = root.findViewWithTag("total");
        tvPendingReports = root.findViewWithTag("pending");
        tvVerifiedReports = root.findViewWithTag("verified");
        tvRejectedReports = root.findViewWithTag("rejected");
    }

    private void addSectionTitle(
            LinearLayout parent, String title, String subtitle) {

        parent.addView(text(title, 21, WHITE, true));
        addSpace(parent, 5);
        parent.addView(text(subtitle, 12, SECONDARY, false));
        addSpace(parent, 14);
    }

    private View makeMetricCard(
            String label, String initial, int type) {

        int color;
        String tag;

        switch (type) {
            case 0:
                color = RED;
                tag = "total";
                break;
            case 1:
                color = ORANGE;
                tag = "pending";
                break;
            case 2:
                color = GREEN;
                tag = "verified";
                break;
            default:
                color = MUTED;
                tag = "rejected";
                break;
        }

        LinearLayout box = card();
        box.setMinimumHeight(dp(112));

        View accent = new View(this);
        accent.setBackground(panelBackground(color, 3, color));

        box.addView(accent,
                new LinearLayout.LayoutParams(
                        dp(34), dp(4)));

        addSpace(box, 13);

        TextView number = text(initial, 29, color, true);
        number.setTag(tag);
        box.addView(number);

        addSpace(box, 5);
        box.addView(text(label, 11, SECONDARY, true));

        return box;
    }

    private void addMenuCard(
            LinearLayout parent,
            String title,
            String description,
            String symbol,
            int iconColor,
            Runnable action) {

        LinearLayout box = card();
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setClickable(true);
        box.setFocusable(true);

        TextView icon = text(symbol, 22, iconColor, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(panelBackground(
                SURFACE, 13, BORDER));

        box.addView(icon,
                new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(13), 0, dp(5), 0);

        words.addView(text(title, 15, WHITE, true));
        addSpace(words, 5);

        TextView descriptionView =
                text(description, 12, SECONDARY, false);

        descriptionView.setLineSpacing(dp(2), 1f);
        words.addView(descriptionView);

        box.addView(words,
                new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        box.addView(text("›", 28, MUTED, true));

        box.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

        params.bottomMargin = dp(11);
        parent.addView(box, params);
    }

    private void addNavButton(
            LinearLayout parent,
            String label,
            int color,
            Runnable action) {

        TextView button = text(label, 11, color, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), dp(9), dp(4), dp(9));
        button.setOnClickListener(v -> action.run());

        parent.addView(button,
                new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    }

    private void addLegend(
            LinearLayout parent, String label, int color) {

        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(panelBackground(color, 50, color));

        row.addView(dot,
                new LinearLayout.LayoutParams(dp(9), dp(9)));

        TextView legendText = text(label, 12, SECONDARY, false);
        legendText.setPadding(dp(9), dp(8), 0, dp(8));

        row.addView(legendText);
        parent.addView(row);
    }

    // =========================================================
    // AI SCAM INTELLIGENCE
    // =========================================================

    private void runAdminAi(String functionName) {
        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Toast.makeText(this,
                    "Please sign in first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog progress = createStyledDialog(
                "AI SCAM INTELLIGENCE",
                "Analyzing recent scam reports...\nPlease wait.");

        progress.setCancelable(false);
        progress.show();
        styleDialogButtons(progress);

        user.getIdToken(false).addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) {
                if (progress.isShowing()) progress.dismiss();
                return;
            }

            if (!task.isSuccessful()
                    || task.getResult() == null
                    || task.getResult().getToken() == null) {

                progress.dismiss();

                showError("AI Analysis Failed",
                        "Could not obtain your Firebase login token.");
                return;
            }

            String firebaseToken = task.getResult().getToken();

            executor.execute(() -> {
                HttpURLConnection connection = null;

                try {
                    URL url = new URL(
                            supabaseUrl + "/functions/v1/" + functionName);

                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(60000);
                    connection.setDoOutput(true);

                    connection.setRequestProperty("apikey", supabaseKey);
                    connection.setRequestProperty(
                            "Authorization", "Bearer " + firebaseToken);
                    connection.setRequestProperty(
                            "Content-Type", "application/json");
                    connection.setRequestProperty(
                            "Accept", "application/json");

                    try (OutputStream output = connection.getOutputStream()) {
                        output.write("{}".getBytes(StandardCharsets.UTF_8));
                    }

                    int code = connection.getResponseCode();

                    InputStream stream = code >= 200 && code < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

                    String response = readStream(stream);

                    if (code < 200 || code >= 300) {
                        throw new Exception(
                                "HTTP " + code + "\n" + response);
                    }

                    JSONObject result = new JSONObject(response);

                    if (!result.optBoolean("success", true)) {
                        throw new Exception(result.optString(
                                "error", "AI analysis failed."));
                    }

                    String displayText = formatAiIntelligence(result);

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        if (progress.isShowing()) progress.dismiss();
                        showAiResults(displayText);
                    });

                } catch (Exception e) {
                    String error = e.getMessage() == null
                            ? "Unknown error" : e.getMessage();

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        if (progress.isShowing()) progress.dismiss();

                        showError("AI Analysis Failed", error);
                    });

                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        });
    }

    private void showAiResults(String displayText) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(8), dp(18), dp(10));
        container.setBackgroundColor(BG);

        TextView intro = text(
                "AI-GENERATED REPORT INSIGHTS",
                11, RED, true);
        intro.setLetterSpacing(0.08f);

        container.addView(intro);
        addSpace(container, 12);

        TextView message = text(displayText, 14, WHITE, false);
        message.setMovementMethod(new ScrollingMovementMethod());
        message.setTextIsSelectable(true);
        message.setLineSpacing(dp(4), 1.05f);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BG);
        scroll.addView(message);

        int maxHeight = (int) (
                getResources().getDisplayMetrics().heightPixels * 0.58f);

        container.addView(scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        maxHeight));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(makeDialogTitle("AI SCAM INTELLIGENCE"))
                .setView(container)
                .setPositiveButton("Close", null)
                .setNeutralButton("Refresh Counts",
                        (d, which) -> loadReportCounts())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(
                    panelBackground(BG, 22, BORDER));
            styleDialogButtons(dialog);
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    panelBackground(BG, 22, BORDER));
        }

        styleDialogButtons(dialog);
    }

    private TextView makeDialogTitle(String title) {
        TextView titleView = text(title, 18, RED, true);
        titleView.setPadding(dp(22), dp(22), dp(22), dp(12));
        titleView.setBackgroundColor(BG);
        return titleView;
    }

    private AlertDialog createStyledDialog(
            String title, String message) {

        TextView messageView = text(message, 14, SECONDARY, false);
        messageView.setPadding(dp(22), dp(12), dp(22), dp(20));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(makeDialogTitle(title))
                .setView(messageView)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        panelBackground(BG, 22, BORDER));
            }
        });

        return dialog;
    }

    private void styleDialogButtons(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    panelBackground(BG, 22, BORDER));
        }

        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(RED);
        }

        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(SECONDARY);
        }

        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(BLUE);
        }
    }

    private void showError(String title, String message) {
        if (isFinishing() || isDestroyed()) return;

        TextView messageView = text(message, 14, WHITE, false);
        messageView.setPadding(dp(22), dp(12), dp(22), dp(20));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(makeDialogTitle(title))
                .setView(messageView)
                .setPositiveButton("OK", null)
                .create();

        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
        styleDialogButtons(dialog);
    }

    // =========================================================
    // AI RESPONSE FORMATTING
    // =========================================================

    private String formatAiIntelligence(JSONObject result) {
        StringBuilder output = new StringBuilder();
        output.append("REPORT OVERVIEW\n\n");

        JSONObject stats = result.optJSONObject("statistics");

        if (stats == null) {
            JSONObject analysis = result.optJSONObject("analysis");

            if (analysis == null) {
                analysis = result.optJSONObject("ai_analysis");
            }

            if (analysis != null) {
                appendField(output, "AI Summary", analysis.opt("summary"));
                appendField(output, "Key Findings",
                        analysis.opt("key_findings"));
                appendField(output, "Emerging Trends",
                        analysis.opt("emerging_trends"));
                appendField(output, "Recommendations",
                        analysis.opt("recommendations"));
            }

            appendField(output, "Summary", result.opt("summary"));
            appendField(output, "Report Count", result.opt("report_count"));
            appendField(output, "Last 7 Days", result.opt("last_7_days"));
            appendField(output, "Last 30 Days", result.opt("last_30_days"));

            if (output.toString().trim().equals("REPORT OVERVIEW")) {
                output.append("No recognized statistics were returned.\n\n")
                        .append(result.toString());
            }

            appendDisclaimer(output);
            return output.toString();
        }

        appendField(output, "Reports Analyzed",
                stats.opt("reports_in_sample"));
        appendField(output, "Reports in Last 7 Days",
                stats.opt("recent_7_day_reports"));
        appendField(output, "Previous 7-Day Reports",
                stats.opt("previous_7_day_reports_in_sample"));
        appendField(output, "Change vs Previous 7 Days",
                formatPercent(
                        stats.opt("change_percent_vs_previous_7_days")));
        appendField(output, "Reports in Last 30 Days",
                stats.opt("recent_30_day_reports"));

        output.append("\n\nSCAM CATEGORY DISTRIBUTION\n\n");
        appendNamedCounts(output,
                stats.optJSONArray("categories_all_sample"));

        output.append("\nRECENT 30-DAY CATEGORIES\n\n");
        appendNamedCounts(output,
                stats.optJSONArray("categories_recent_30_days"));

        output.append("\nREPORT STATUS DISTRIBUTION\n\n");
        appendStatusCounts(output, stats.opt("statuses_all_sample"));

        output.append("\nREPEATED SCAM PATTERNS\n\n");
        appendRepeatedIdentifiers(output,
                stats.optJSONArray(
                        "repeated_identifiers_without_values"));

        appendField(output, "Generated At", result.opt("generated_at"));
        appendDisclaimer(output);

        return output.toString();
    }

    private String formatPercent(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "Not available";
        }

        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();

            if (number == Math.floor(number)) {
                return String.format(Locale.US, "%.0f%%", number);
            }

            return String.format(Locale.US, "%.2f%%", number);
        }

        return String.valueOf(value) + "%";
    }

    private void appendField(
            StringBuilder output, String label, Object value) {

        if (value == null || value == JSONObject.NULL) return;

        String formatted;

        if (value instanceof JSONArray) {
            formatted = formatArray((JSONArray) value);
        } else if (value instanceof JSONObject) {
            formatted = formatObject((JSONObject) value);
        } else {
            formatted = String.valueOf(value);
        }

        if (formatted.trim().isEmpty()) return;

        output.append(label)
                .append(": ")
                .append(formatted)
                .append("\n");
    }

    private void appendNamedCounts(
            StringBuilder output, JSONArray items) {

        if (items == null || items.length() == 0) {
            output.append("No category data available.\n");
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            output.append("• ")
                    .append(item.optString("name", "Unknown"))
                    .append(": ")
                    .append(item.optInt("count", 0))
                    .append(" reports\n");
        }
    }

    private void appendStatusCounts(
            StringBuilder output, Object statuses) {

        if (statuses instanceof JSONArray) {
            appendNamedCounts(output, (JSONArray) statuses);
            return;
        }

        if (statuses instanceof JSONObject) {
            JSONObject object = (JSONObject) statuses;

            if (object.has("name") || object.has("count")) {
                output.append("• ")
                        .append(object.optString("name", "Unknown"))
                        .append(": ")
                        .append(object.optInt("count", 0))
                        .append("\n");
                return;
            }

            JSONArray names = object.names();

            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i);

                    output.append("• ")
                            .append(name)
                            .append(": ")
                            .append(object.opt(name))
                            .append("\n");
                }
                return;
            }
        }

        output.append("No status data available.\n");
    }

    private void appendRepeatedIdentifiers(
            StringBuilder output, JSONArray items) {

        if (items == null || items.length() == 0) {
            output.append("No repeated patterns reported.\n");
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            output.append("Scam Type: ")
                    .append(item.optString("scam_type", "Unknown"))
                    .append("\n");

            output.append("Report Count: ")
                    .append(item.optInt("report_count", 0))
                    .append("\n");

            JSONArray statuses = item.optJSONArray("statuses");

            if (statuses != null && statuses.length() > 0) {
                output.append("Statuses: ");

                for (int j = 0; j < statuses.length(); j++) {
                    if (j > 0) output.append(", ");
                    output.append(statuses.optString(j, ""));
                }

                output.append("\n");
            }

            output.append("\n");
        }
    }

    private String formatArray(JSONArray array) {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);

            if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                String name = object.optString("name", "");
                Object count = object.opt("count");

                if (output.length() > 0) output.append("\n");

                if (!name.isEmpty()) {
                    output.append("• ").append(name);

                    if (count != null && count != JSONObject.NULL) {
                        output.append(": ").append(count);
                    }
                } else {
                    output.append(object);
                }
            } else {
                if (output.length() > 0) output.append(", ");
                output.append(String.valueOf(item));
            }
        }

        return output.toString();
    }

    private String formatObject(JSONObject object) {
        StringBuilder output = new StringBuilder();
        JSONArray names = object.names();

        if (names == null) return object.toString();

        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);

            if (output.length() > 0) output.append("\n");

            output.append("• ")
                    .append(key)
                    .append(": ")
                    .append(String.valueOf(object.opt(key)));
        }

        return output.toString();
    }

    private void appendDisclaimer(StringBuilder output) {
        output.append("\n\nIMPORTANT\n")
                .append("These figures describe the available report sample. ")
                .append("Repeated patterns or AI recommendations are not proof ")
                .append("that a report is fraudulent. Review original reports ")
                .append("and evidence before making moderation decisions.");
    }

    // =========================================================
    // SUPABASE REPORT COUNTS
    // =========================================================

    private void loadReportCounts() {
        if (tvStatus == null
                || tvTotalReports == null
                || tvPendingReports == null
                || tvVerifiedReports == null
                || tvRejectedReports == null) {
            return;
        }

        tvStatus.setText("Syncing report data...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                        supabaseUrl + "/rest/v1/reports?select=status");

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                connection.setRequestProperty("apikey", supabaseKey);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + supabaseKey);
                connection.setRequestProperty(
                        "Accept", "application/json");

                int code = connection.getResponseCode();

                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String response = readStream(stream);

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "HTTP " + code + ": " + response);
                }

                JSONArray reports = new JSONArray(response);

                int pending = 0;
                int verified = 0;
                int rejected = 0;

                for (int i = 0; i < reports.length(); i++) {
                    String status = reports.getJSONObject(i)
                            .optString("status", "").trim();

                    if (status.equalsIgnoreCase("Pending")) {
                        pending++;
                    } else if (status.equalsIgnoreCase("Verified")) {
                        verified++;
                    } else if (status.equalsIgnoreCase("Rejected")) {
                        rejected++;
                    }
                }

                final int total = reports.length();
                final int p = pending;
                final int v = verified;
                final int r = rejected;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    tvTotalReports.setText(String.valueOf(total));
                    tvPendingReports.setText(String.valueOf(p));
                    tvVerifiedReports.setText(String.valueOf(v));
                    tvRejectedReports.setText(String.valueOf(r));
                    tvChartTotal.setText(String.valueOf(total));

                    adminDonutView.setCounts(p, v, r);
                    tvStatus.setText("Report data updated successfully");
                });

            } catch (Exception e) {
                String error = e.getMessage() == null
                        ? "Unknown error" : e.getMessage();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    tvStatus.setText("Unable to load report data");

                    Toast.makeText(this,
                            "Dashboard error: " + error,
                            Toast.LENGTH_LONG).show();
                });

            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";

        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line).append('\n');
            }
        }

        return response.toString().trim();
    }

    // =========================================================
    // NAVIGATION AND UI HELPERS
    // =========================================================

    private void openScreen(Class<?> screen) {
        try {
            startActivity(new Intent(this, screen));
        } catch (Exception e) {
            Toast.makeText(this,
                    "Could not open " + screen.getSimpleName()
                            + ". Check its manifest entry.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(panelBackground(CARD, 18, BORDER));
        return layout;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private TextView text(
            String value, int size, int color, boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private GradientDrawable panelBackground(
            int color, int radius, int strokeColor) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);

        parent.addView(space,
                new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void addSpaceHorizontal(
            LinearLayout parent, int width) {

        View space = new View(this);

        parent.addView(space,
                new LinearLayout.LayoutParams(dp(width), 1));
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
