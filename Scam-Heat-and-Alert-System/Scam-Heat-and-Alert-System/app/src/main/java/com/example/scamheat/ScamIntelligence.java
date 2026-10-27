
package com.example.scamheat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScamIntelligence extends AppCompatActivity {

    // UI
    private TextView tvTotalReports;
    private TextView tvThisWeek;
    private TextView tvVerifiedReports;
    private TextView tvPendingReports;

    private LinearLayout categoryContainer;
    private LinearLayout trendingContainer;
    private LinearLayout recentReportsContainer;
    private LinearLayout locationContainer;

    // Supabase
    private String supabaseUrl;
    private String supabaseKey;

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    private final int RED = Color.rgb(198, 40, 40);
    private final int GREEN = Color.rgb(46, 125, 50);
    private final int GREY = Color.rgb(117, 117, 117);
    private final int BLUE = Color.rgb(63, 81, 181);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scam_intelligence);

        tvTotalReports = findViewById(R.id.tvTotalReports);
        tvThisWeek = findViewById(R.id.tvThisWeek);
        tvVerifiedReports = findViewById(R.id.tvVerifiedReports);
        tvPendingReports = findViewById(R.id.tvPendingReports);

        categoryContainer = findViewById(R.id.categoryContainer);
        trendingContainer = findViewById(R.id.trendingContainer);
        recentReportsContainer = findViewById(R.id.recentReportsContainer);
        locationContainer = findViewById(R.id.locationContainer);

        supabaseUrl = getString(R.string.supabase_url).trim();
        supabaseKey =
                getString(R.string.supabase_publishable_key).trim();

        if (supabaseKey.startsWith("\"") &&
                supabaseKey.endsWith("\"") &&
                supabaseKey.length() >= 2) {
            supabaseKey = supabaseKey.substring(
                    1, supabaseKey.length() - 1
            );
        }

        if (supabaseUrl.endsWith("/")) {
            supabaseUrl =
                    supabaseUrl.substring(0, supabaseUrl.length() - 1);
        }

        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            Toast.makeText(
                    this,
                    "Supabase URL or key is missing",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        loadStatistics();
        loadCategories();
        loadTrendingScams();
        loadRecentReports();
        loadLocationStatistics();
    }

    // =========================================================
    // 1. STATISTICS
    // =========================================================

    private void loadStatistics() {
        executorService.execute(() -> {
            String endpoint = supabaseUrl
                    + "/rest/v1/scam_report_statistics?select=*";

            try {
                String response = sendGetRequest(endpoint);
                JSONArray array = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (array.length() == 0) {
                        tvTotalReports.setText("0");
                        tvThisWeek.setText("0");
                        tvVerifiedReports.setText("0");
                        tvPendingReports.setText("0");
                        return;
                    }

                    JSONObject data = array.optJSONObject(0);
                    if (data == null) return;

                    tvTotalReports.setText(String.valueOf(
                            data.optInt("total_reports", 0)
                    ));

                    tvThisWeek.setText(String.valueOf(
                            data.optInt("reports_this_week", 0)
                    ));

                    tvVerifiedReports.setText(String.valueOf(
                            data.optInt("verified_reports", 0)
                    ));

                    tvPendingReports.setText(String.valueOf(
                            data.optInt("pending_reports", 0)
                    ));
                });
            } catch (Exception e) {
                showError("Statistics failed\n" + e.getMessage());
            }
        });
    }

    // =========================================================
    // 2. CATEGORY STATISTICS WITH COMPARABLE BARS
    // =========================================================

    private void loadCategories() {
        executorService.execute(() -> {
            try {
                String endpoint = supabaseUrl
                        + "/rest/v1/scam_reports_by_type"
                        + "?select=*"
                        + "&order=report_count.desc";

                String response = sendGetRequest(endpoint);
                JSONArray array = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    categoryContainer.removeAllViews();

                    if (array.length() == 0) {
                        addMessage(
                                categoryContainer,
                                "No scam reports available."
                        );
                        return;
                    }

                    int maxCount = 0;

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item != null) {
                            maxCount = Math.max(
                                    maxCount,
                                    item.optInt("report_count", 0)
                            );
                        }
                    }

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) continue;

                        String scamType =
                                item.optString("scam_type", "Unknown");

                        int count = item.optInt("report_count", 0);

                        addCategoryRow(scamType, count, maxCount);
                    }
                });
            } catch (Exception e) {
                showError("Category failed\n" + e.getMessage());
            }
        });
    }

    private void addCategoryRow(
            String scamType,
            int count,
            int maxCount
    ) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dp(10), 0, dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(scamType);
        name.setTextSize(14);
        name.setTextColor(Color.DKGRAY);

        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        TextView number = new TextView(this);
        number.setText(String.valueOf(count));
        number.setTextSize(14);
        number.setTypeface(null, Typeface.BOLD);
        number.setTextColor(Color.BLACK);

        header.addView(name, nameParams);
        header.addView(number);
        item.addView(header);

        ProgressBar bar = createReportBar(
                maxCount,
                count,
                BLUE
        );

        LinearLayout.LayoutParams barParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(6)
                );
        barParams.topMargin = dp(8);

        item.addView(bar, barParams);
        categoryContainer.addView(item);
    }

    // =========================================================
    // 3. TRENDING SCAMS WITH COMPARABLE BARS
    // =========================================================
    private void loadTrendingScams() {
        executorService.execute(() -> {
            try {
                String endpoint = supabaseUrl
                        + "/rest/v1/scam_trending"
                        + "?select=scam_type,current_count,previous_count,percentage_change"
                        + "&order=percentage_change.desc";

                String response = sendGetRequest(endpoint);
                JSONArray array = new JSONArray(response);

                runOnUiThread(() -> {
                    trendingContainer.removeAllViews();

                    if (array.length() == 0) {
                        addMessage(
                                trendingContainer,
                                "No trending scam data available."
                        );
                        return;
                    }

                    // Find the highest current report count first.
                    int maxCount = 1;

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);

                        if (item != null) {
                            maxCount = Math.max(
                                    maxCount,
                                    item.optInt("current_count", 0)
                            );
                        }
                    }

                    // Display every trending scam using the same bar scale.
                    for (int i = 0; i < array.length(); i++) {
                        try {
                            JSONObject item = array.getJSONObject(i);

                            String scamType = item.optString(
                                    "scam_type", "Unknown"
                            );

                            int currentCount = item.optInt(
                                    "current_count", 0
                            );

                            int previousCount = item.optInt(
                                    "previous_count", 0
                            );

                            int percentage = item.optInt(
                                    "percentage_change", 0
                            );

                            addTrendingRow(
                                    i + 1,
                                    scamType,
                                    currentCount,
                                    previousCount,
                                    percentage,
                                    maxCount
                            );

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

            } catch (Exception e) {
                showError("Trending failed\n" + e.getMessage());
            }
        });
    }
    private void addTrendingRow(
            int position,
            String scamType,
            int currentCount,
            int previousCount,
            int percentage,
            int maxCount
    ) {
        int density = (int) getResources()
                .getDisplayMetrics().density;

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, 8 * density, 0, 8 * density);

        // Scam name and percentage
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(position + ". " + scamType);
        name.setTextSize(14);
        name.setTextColor(Color.rgb(45, 45, 45));

        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        TextView change = new TextView(this);
        change.setTextSize(13);
        change.setTypeface(null, android.graphics.Typeface.BOLD);

        if (percentage > 0) {
            change.setText("↑ " + percentage + "%");
            change.setTextColor(Color.rgb(198, 40, 40));
        } else if (percentage < 0) {
            change.setText("↓ " + Math.abs(percentage) + "%");
            change.setTextColor(Color.rgb(46, 125, 50));
        } else {
            change.setText("→ 0%");
            change.setTextColor(Color.rgb(117, 117, 117));
        }

        header.addView(name, nameParams);
        header.addView(change);
        item.addView(header);

        // Current and previous counts
        TextView counts = new TextView(this);
        counts.setText(
                "Current: " + currentCount
                        + "  |  Previous: " + previousCount
        );
        counts.setTextSize(12);
        counts.setTextColor(Color.rgb(117, 117, 117));
        counts.setPadding(0, 4 * density, 0, 8 * density);
        item.addView(counts);

        // All bars share the same maximum count.
        android.widget.ProgressBar bar =
                new android.widget.ProgressBar(
                        this,
                        null,
                        android.R.attr.progressBarStyleHorizontal
                );

        bar.setMax(Math.max(1, maxCount));
        bar.setProgress(Math.max(0, currentCount));

        if (percentage > 0) {
            bar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.rgb(198, 40, 40)
                    )
            );
        } else if (percentage < 0) {
            bar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.rgb(46, 125, 50)
                    )
            );
        } else {
            bar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.rgb(117, 117, 117)
                    )
            );
        }

        bar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(238, 238, 238)
                )
        );

        item.addView(
                bar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.max(4, 5 * density)
                )
        );

        trendingContainer.addView(item);

        // Divider
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(Color.rgb(238, 238, 238));

        trendingContainer.addView(
                divider,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.max(1, density)
                )
        );
    }
    // =========================================================
    // SHARED REPORT BAR
    // =========================================================

    private ProgressBar createReportBar(
            int maxCount,
            int count,
            int progressColor
    ) {
        ProgressBar bar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        int safeMax = Math.max(1, maxCount);
        int safeProgress = Math.max(0, Math.min(count, safeMax));

        bar.setMax(safeMax);
        bar.setProgress(safeProgress);
        bar.setIndeterminate(false);

        // Visible light-grey track
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(Color.rgb(235, 235, 235));
        background.setCornerRadius(dp(4));

        // Colored progress portion
        GradientDrawable foreground = new GradientDrawable();
        foreground.setShape(GradientDrawable.RECTANGLE);
        foreground.setColor(progressColor);
        foreground.setCornerRadius(dp(4));

        ClipDrawable clippedProgress = new ClipDrawable(
                foreground,
                Gravity.START,
                ClipDrawable.HORIZONTAL
        );

        LayerDrawable layers = new LayerDrawable(
                new android.graphics.drawable.Drawable[]{
                        background,
                        clippedProgress
                }
        );

        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);

        bar.setProgressDrawable(layers);
        bar.setProgressTintList(ColorStateList.valueOf(progressColor));
        bar.setMinimumHeight(dp(8));
        bar.setPadding(0, 0, 0, 0);

        bar.setVisibility(View.VISIBLE);

        return bar;
    }


    // =========================================================
    // 4. RECENT REPORTS
    // =========================================================

    private void loadRecentReports() {
        executorService.execute(() -> {
            String endpoint = supabaseUrl
                    + "/rest/v1/scam_recent_reports"
                    + "?select=*"
                    + "&order=created_at.desc";

            try {
                String response = sendGetRequest(endpoint);
                JSONArray array = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    recentReportsContainer.removeAllViews();

                    if (array.length() == 0) {
                        addMessage(
                                recentReportsContainer,
                                "No recent reports."
                        );
                        return;
                    }

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) continue;

                        String scamType =
                                item.optString("scam_type", "Unknown");

                        String status =
                                item.optString("status", "Unknown");

                        String date =
                                item.optString("created_at", "");

                        addRecentReport(scamType, status, date);
                    }
                });
            } catch (Exception e) {
                showError("Recent reports failed\n" + e.getMessage());
            }
        });
    }

    private void addRecentReport(
            String scamType,
            String status,
            String date
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView scam = new TextView(this);
        scam.setText(scamType);
        scam.setTextSize(16);
        scam.setTypeface(null, Typeface.BOLD);
        scam.setTextColor(Color.BLACK);

        TextView statusView = new TextView(this);
        statusView.setText("Status: " + status);
        statusView.setTextSize(14);
        statusView.setTextColor(Color.DKGRAY);
        statusView.setPadding(0, dp(4), 0, 0);

        TextView dateView = new TextView(this);
        dateView.setText(formatDate(date));
        dateView.setTextSize(12);
        dateView.setTextColor(GREY);
        dateView.setPadding(0, dp(4), 0, 0);

        card.addView(scam);
        card.addView(statusView);
        card.addView(dateView);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(10);

        recentReportsContainer.addView(card, params);
    }

    // =========================================================
    // 5. LOCATION STATISTICS
    // =========================================================

    private void loadLocationStatistics() {
        executorService.execute(() -> {
            String endpoint = supabaseUrl
                    + "/rest/v1/scam_location_statistics"
                    + "?select=*"
                    + "&order=report_count.desc";

            try {
                String response = sendGetRequest(endpoint);
                JSONArray array = new JSONArray(response);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    locationContainer.removeAllViews();

                    if (array.length() == 0) {
                        addMessage(
                                locationContainer,
                                "No location data available."
                        );
                        return;
                    }

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) continue;

                        String latitude =
                                item.optString("latitude", "-");

                        String longitude =
                                item.optString("longitude", "-");

                        int count =
                                item.optInt("report_count", 0);

                        TextView location = new TextView(this);
                        location.setText(
                                latitude + "°, " + longitude
                                        + "°    " + count + " reports"
                        );
                        location.setTextSize(14);
                        location.setTextColor(Color.DKGRAY);
                        location.setPadding(0, dp(10), 0, dp(10));

                        locationContainer.addView(location);
                    }
                });
            } catch (Exception e) {
                showError("Location failed\n" + e.getMessage());
            }
        });
    }

    // =========================================================
    // SUPABASE GET REQUEST
    // =========================================================

    private String sendGetRequest(String endpoint) throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            connection.setRequestProperty("apikey", supabaseKey);
            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + supabaseKey
            );
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            int responseCode = connection.getResponseCode();

            java.io.InputStream stream =
                    responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (stream == null) {
                throw new Exception("HTTP " + responseCode);
            }

            StringBuilder response = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            if (responseCode < 200 || responseCode >= 300) {
                throw new Exception(
                        "HTTP " + responseCode + ": " + response
                );
            }

            return response.toString();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // =========================================================
    // DATE FORMATTING
    // =========================================================

    private String formatDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return "Date unavailable";
        }

        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat inputFormat =
                        new SimpleDateFormat(format, Locale.US);

                Date date = inputFormat.parse(dateString);

                if (date != null) {
                    SimpleDateFormat outputFormat =
                            new SimpleDateFormat(
                                    "dd MMM yyyy, hh:mm a",
                                    Locale.getDefault()
                            );

                    return outputFormat.format(date);
                }
            } catch (Exception ignored) {
                // Try the next supported timestamp format.
            }
        }

        return dateString;
    }

    // =========================================================
    // EMPTY-STATE MESSAGE
    // =========================================================

    private void addMessage(
            LinearLayout container,
            String message
    ) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextSize(14);
        text.setTextColor(GREY);
        text.setPadding(0, dp(10), 0, dp(10));
        container.addView(text);
    }

    // =========================================================
    // ERROR MESSAGE
    // =========================================================

    private void showError(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(
                        ScamIntelligence.this,
                        message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    // =========================================================
    // DP CONVERSION
    // =========================================================

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }
}
