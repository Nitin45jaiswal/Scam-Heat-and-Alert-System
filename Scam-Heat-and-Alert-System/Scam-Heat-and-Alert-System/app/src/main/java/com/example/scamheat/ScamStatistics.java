
package com.example.scamheat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScamStatistics extends AppCompatActivity {

    private static final int BG = Color.rgb(8, 13, 26);
    private static final int TEXT_PRIMARY = Color.rgb(245, 247, 255);
    private static final int TEXT_SECONDARY = Color.rgb(155, 169, 195);
    private static final int CRIMSON = Color.rgb(255, 49, 85);
    private static final int GREEN = Color.rgb(54, 224, 161);
    private static final int ORANGE = Color.rgb(255, 138, 61);
    private static final int BLUE = Color.rgb(57, 167, 255);

    private TextView tvTotalReports;
    private TextView tvPending;
    private TextView tvVerified;
    private TextView tvRejected;

    private TextView tvUpiCount;
    private TextView tvFakeJobCount;
    private TextView tvPhishingCount;
    private TextView tvBankingCount;
    private TextView tvCourierCount;
    private TextView tvDigitalArrestCount;

    private BarChart scamTypeBarChart;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private String baseUrl;
    private String apiKey;
    private volatile boolean activityDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        setContentView(R.layout.scam_statistics);

        baseUrl = getString(R.string.supabase_url);
        apiKey = getString(R.string.supabase_publishable_key);

        bindViews();
        setupChart();

        ImageButton backButton = findViewById(R.id.btnBack);
        if (backButton != null) {
            backButton.setOnClickListener(
                    v -> getOnBackPressedDispatcher().onBackPressed()
            );
        }

        loadStatistics();
    }

    private void bindViews() {
        tvTotalReports = findViewById(R.id.tvTotalReports);
        tvPending = findViewById(R.id.tvPending);
        tvVerified = findViewById(R.id.tvVerified);
        tvRejected = findViewById(R.id.tvRejected);

        tvUpiCount = findViewById(R.id.tvUpiCount);
        tvFakeJobCount = findViewById(R.id.tvFakeJobCount);
        tvPhishingCount = findViewById(R.id.tvPhishingCount);
        tvBankingCount = findViewById(R.id.tvBankingCount);
        tvCourierCount = findViewById(R.id.tvCourierCount);
        tvDigitalArrestCount =
                findViewById(R.id.tvDigitalArrestCount);

        scamTypeBarChart = findViewById(R.id.scamTypeBarChart);
    }

    private void setupChart() {
        if (scamTypeBarChart == null) return;

        scamTypeBarChart.setBackgroundColor(Color.TRANSPARENT);
        scamTypeBarChart.setDrawGridBackground(false);
        scamTypeBarChart.setDrawBorders(false);
        scamTypeBarChart.setNoDataText("No report data available");
        scamTypeBarChart.setNoDataTextColor(TEXT_SECONDARY);
        scamTypeBarChart.setTouchEnabled(true);
        scamTypeBarChart.setDragEnabled(true);
        scamTypeBarChart.setScaleEnabled(false);
        scamTypeBarChart.setPinchZoom(false);
        scamTypeBarChart.setFitBars(true);
        scamTypeBarChart.setExtraBottomOffset(10f);
        scamTypeBarChart.setExtraLeftOffset(4f);
        scamTypeBarChart.setExtraRightOffset(10f);

        Description description = new Description();
        description.setText("");
        scamTypeBarChart.setDescription(description);

        Legend legend = scamTypeBarChart.getLegend();
        legend.setEnabled(false);

        XAxis xAxis = scamTypeBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(TEXT_SECONDARY);
        xAxis.setTextSize(9f);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setLabelRotationAngle(-25f);

        YAxis leftAxis = scamTypeBarChart.getAxisLeft();
        leftAxis.setTextColor(TEXT_SECONDARY);
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setGridColor(0x3329354D);

        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        scamTypeBarChart.getAxisRight().setEnabled(false);
    }

    private void loadStatistics() {
        setLoadingState();

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint =
                        baseUrl.replaceAll("/+$", "")
                                + "/rest/v1/reports"
                                + "?select=status,scam_type"
                                + "&order=created_at.desc"
                                + "&limit=5000";

                URL url = new URL(endpoint);

                connection = (HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                connection.setRequestProperty("apikey", apiKey);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + apiKey
                );
                connection.setRequestProperty(
                        "Accept", "application/json"
                );

                int code = connection.getResponseCode();

                InputStream stream =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader =
                                 new BufferedReader(
                                         new InputStreamReader(
                                                 stream,
                                                 StandardCharsets.UTF_8
                                         ))) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "Supabase HTTP " + code + ": " + response
                    );
                }

                JSONArray rows =
                        new JSONArray(response.toString());

                int pending = 0;
                int verified = 0;
                int rejected = 0;

                Map<String, Integer> categories =
                        new LinkedHashMap<>();

                for (int i = 0; i < rows.length(); i++) {
                    JSONObject report = rows.optJSONObject(i);
                    if (report == null) continue;

                    String status = report
                            .optString("status", "Unknown")
                            .trim();

                    String type = report
                            .optString("scam_type", "Unspecified")
                            .trim();

                    if (type.isEmpty()
                            || type.equalsIgnoreCase("null")) {
                        type = "Unspecified";
                    }

                    if (status.equalsIgnoreCase("Pending")) {
                        pending++;
                    } else if (
                            status.equalsIgnoreCase("Verified")) {
                        verified++;
                    } else if (
                            status.equalsIgnoreCase("Rejected")) {
                        rejected++;
                    }

                    String category = normalizeCategory(type);

                    Integer previous = categories.get(category);
                    categories.put(
                            category,
                            previous == null ? 1 : previous + 1
                    );
                }

                final int total = rows.length();
                final int finalPending = pending;
                final int finalVerified = verified;
                final int finalRejected = rejected;

                final Map<String, Integer> finalCategories =
                        new LinkedHashMap<>(categories);

                runOnUiThread(() -> {
                    if (activityDestroyed || isFinishing()) return;

                    updateSummary(
                            total,
                            finalPending,
                            finalVerified,
                            finalRejected
                    );

                    updateCategoryCounts(finalCategories);
                    updateChart(finalCategories);
                });

            } catch (Exception e) {
                String message = e.getMessage() == null
                        ? "Unable to load statistics."
                        : e.getMessage();

                runOnUiThread(() -> {
                    if (activityDestroyed || isFinishing()) return;

                    setFailureState();

                    Toast.makeText(
                            ScamStatistics.this,
                            "Statistics could not be loaded: " + message,
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

    private void setLoadingState() {
        tvTotalReports.setText("…");
        tvPending.setText("…");
        tvVerified.setText("…");
        tvRejected.setText("…");

        tvUpiCount.setText("…");
        tvFakeJobCount.setText("…");
        tvPhishingCount.setText("…");
        tvBankingCount.setText("…");
        tvCourierCount.setText("…");
        tvDigitalArrestCount.setText("…");

        scamTypeBarChart.clear();
        scamTypeBarChart.setNoDataText("Loading statistics…");
        scamTypeBarChart.invalidate();
    }

    private void setFailureState() {
        tvTotalReports.setText("—");
        tvPending.setText("—");
        tvVerified.setText("—");
        tvRejected.setText("—");

        tvUpiCount.setText("—");
        tvFakeJobCount.setText("—");
        tvPhishingCount.setText("—");
        tvBankingCount.setText("—");
        tvCourierCount.setText("—");
        tvDigitalArrestCount.setText("—");

        scamTypeBarChart.clear();
        scamTypeBarChart.setNoDataText("Unable to load chart data");
        scamTypeBarChart.invalidate();
    }

    private void updateSummary(
            int total,
            int pending,
            int verified,
            int rejected
    ) {
        tvTotalReports.setText(String.valueOf(total));
        tvPending.setText(String.valueOf(pending));
        tvVerified.setText(String.valueOf(verified));
        tvRejected.setText(String.valueOf(rejected));
    }

    private void updateCategoryCounts(
            Map<String, Integer> categories
    ) {
        tvUpiCount.setText(
                String.valueOf(getCategoryCount(categories, "UPI Fraud"))
        );

        tvFakeJobCount.setText(
                String.valueOf(getCategoryCount(categories, "Fake Job"))
        );

        tvPhishingCount.setText(
                String.valueOf(getCategoryCount(categories, "Phishing"))
        );

        tvBankingCount.setText(
                String.valueOf(getCategoryCount(categories, "Banking Scam"))
        );

        tvCourierCount.setText(
                String.valueOf(getCategoryCount(categories, "Courier Scam"))
        );

        tvDigitalArrestCount.setText(
                String.valueOf(getCategoryCount(categories, "Digital Arrest"))
        );
    }

    private int getCategoryCount(
            Map<String, Integer> categories,
            String target
    ) {
        String normalizedTarget = normalizeCategory(target);
        int count = 0;

        for (Map.Entry<String, Integer> entry : categories.entrySet()) {
            String category = normalizeCategory(entry.getKey());

            if (category.equals(normalizedTarget)) {
                count += entry.getValue();
            } else if (
                    normalizedTarget.equals("fake job")
                            && category.contains("fake job")) {
                count += entry.getValue();
            } else if (
                    normalizedTarget.equals("upi fraud")
                            && category.contains("upi")) {
                count += entry.getValue();
            } else if (
                    normalizedTarget.equals("banking scam")
                            && category.contains("bank")) {
                count += entry.getValue();
            }
        }

        return count;
    }

    private void updateChart(Map<String, Integer> categories) {
        if (scamTypeBarChart == null) return;

        ArrayList<String> labels = new ArrayList<>();
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        int[] palette = {
                CRIMSON,
                ORANGE,
                BLUE,
                GREEN,
                Color.rgb(160, 120, 255),
                Color.rgb(255, 190, 75),
                Color.rgb(67, 208, 219),
                Color.rgb(255, 113, 139)
        };

        int index = 0;

        for (Map.Entry<String, Integer> entry : categories.entrySet()) {
            labels.add(shortLabel(entry.getKey()));
            entries.add(
                    new BarEntry(index, entry.getValue().floatValue())
            );
            colors.add(palette[index % palette.length]);
            index++;
        }

        if (entries.isEmpty()) {
            scamTypeBarChart.clear();
            scamTypeBarChart.setNoDataText("No report data available");
            scamTypeBarChart.invalidate();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Reports");
        dataSet.setColors(colors);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(TEXT_PRIMARY);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        );
        dataSet.setHighLightColor(Color.TRANSPARENT);

        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.58f);

        scamTypeBarChart.setData(data);

        XAxis xAxis = scamTypeBarChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(labels.size() - 0.5f);
        xAxis.setLabelCount(labels.size(), false);

        int maxValue = 1;

        for (Integer value : categories.values()) {
            if (value != null && value > maxValue) {
                maxValue = value;
            }
        }

        scamTypeBarChart.getAxisLeft().setAxisMinimum(0f);
        scamTypeBarChart.getAxisLeft().setAxisMaximum(
                Math.max(1f, maxValue * 1.2f)
        );

        scamTypeBarChart.animateY(650, Easing.EaseOutCubic);
        scamTypeBarChart.invalidate();
    }

    private String shortLabel(String label) {
        String value = label == null ? "Other" : label.trim();

        if (value.equalsIgnoreCase("Social Media Scam")) {
            return "Social Media";
        }

        if (value.equalsIgnoreCase("Fake Job Offer")) {
            return "Fake Job";
        }

        if (value.length() > 15) {
            return value.substring(0, 13) + "…";
        }

        return value;
    }

    private String normalizeCategory(String value) {
        if (value == null) return "unspecified";

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");

        if (normalized.contains("upi")) return "upi fraud";

        if (normalized.contains("fake job")
                || normalized.contains("job offer")) {
            return "fake job";
        }

        if (normalized.contains("phishing")) return "phishing";
        if (normalized.contains("bank")) return "banking scam";
        if (normalized.contains("courier")) return "courier scam";

        if (normalized.contains("digital arrest")) {
            return "digital arrest";
        }

        if (normalized.contains("social media")) {
            return "social media scam";
        }

        if (normalized.equals("other")) return "other";

        return normalized;
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
