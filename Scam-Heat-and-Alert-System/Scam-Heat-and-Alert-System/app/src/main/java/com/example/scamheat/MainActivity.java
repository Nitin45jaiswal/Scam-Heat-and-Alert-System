package com.example.scamheat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
import android.content.Intent;



public class MainActivity extends AppCompatActivity {

    // =====================================================
    // CONSTANTS
    // =====================================================

    private static final int LOCATION_PERMISSION_REQUEST = 1002;


    // =====================================================
    // FIREBASE
    // =====================================================

    private FirebaseAuth mAuth;


    // =====================================================
    // LOCATION
    // =====================================================

    private FusedLocationProviderClient fusedLocationClient;


    // =====================================================
    // EXISTING UI
    // =====================================================

    private TextView tvGreeting;
    private TextView tvNotification;


    // =====================================================
    // HOME INTELLIGENCE UI
    // =====================================================

    private TextView tvHomeTotalReports;
    private TextView tvHomeThisWeek;
    private TextView tvHomeVerified;
    private TextView tvHomePending;

    private LinearLayout homeTrendingContainer;


    // =====================================================
    // SUPABASE
    // =====================================================

    private String supabaseUrl;
    private String supabaseKey;


    // =====================================================
    // BACKGROUND THREAD
    // =====================================================

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();


    // =====================================================
    // ON CREATE
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // =================================================
        // NOTIFICATION PERMISSION - ANDROID 13+
        // =================================================



        // =================================================
        // FIREBASE
        // =================================================

        mAuth = FirebaseAuth.getInstance();


        // =================================================
        // LOCATION CLIENT
        // =================================================

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);


        // =================================================
        // EXISTING VIEWS
        // =================================================

        tvGreeting =
                findViewById(R.id.tvGreeting);

        tvNotification =
                findViewById(R.id.tvNotification);


        // =================================================
        // HOME INTELLIGENCE VIEWS
        // =================================================

        tvHomeTotalReports =
                findViewById(R.id.tvHomeTotalReports);

        tvHomeThisWeek =
                findViewById(R.id.tvHomeThisWeek);

        tvHomeVerified =
                findViewById(R.id.tvHomeVerified);

        tvHomePending =
                findViewById(R.id.tvHomePending);

        homeTrendingContainer =
                findViewById(R.id.homeTrendingContainer);


        // =================================================
        // SUPABASE
        // =================================================

        supabaseUrl =
                getString(
                        R.string.supabase_url
                );

        supabaseKey =
                getString(
                        R.string.supabase_publishable_key
                );


        // =================================================
        // LOAD USER
        // =================================================

        loadUser();


        // =================================================
        // REQUEST / UPDATE LOCATION
        // =================================================

        requestUserLocation();


        // =================================================
        // LOAD HOME STATISTICS
        // =================================================

        loadHomeStatistics();


        // =================================================
        // LOAD HOME TRENDING
        // =================================================

        loadHomeTrending();




        // =================================================
        // REPORT SCAM
        // =================================================

        findViewById(
                R.id.cardReportScam
        ).setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            MainActivity.this,
                            ReportScam.class
                    );

            startActivity(intent);
        });


        // =================================================
        // SCAM CHECK
        // =================================================

        findViewById(
                R.id.cardScamCheck
        ).setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            MainActivity.this,
                            ScamCheck.class
                    );

            startActivity(intent);
        });


        // =================================================
        // HEATMAP
        // =================================================

        findViewById(R.id.cardHeatmap).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MapActivity.class
            );
            startActivity(intent);
        });


        // =================================================
        // SCAM INTELLIGENCE
        // =================================================

        findViewById(
                R.id.cardScamIntelligence
        ).setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            MainActivity.this,
                            ScamIntelligence.class
                    );

            startActivity(intent);
        });


        // =================================================
        // BOTTOM NAVIGATION
        // =================================================

        BottomNavigationView bottomNavigation =
                findViewById(
                        R.id.bottomNavigation
                );


        bottomNavigation.setOnItemSelectedListener(
                item -> {

                    int id =
                            item.getItemId();


                    if (id == R.id.nav_home) {

                        return true;


                    } else if (id == R.id.nav_map) {

                        Intent intent =
                                new Intent(
                                        MainActivity.this,
                                        MapActivity.class
                                );

                        startActivity(intent);

                        return true;


                    } else if (
                            id == R.id.nav_reportScam
                    ) {

                        Intent intent =
                                new Intent(
                                        MainActivity.this,
                                        ReportScam.class
                                );

                        startActivity(intent);

                        return true;


                    } else if (
                            id == R.id.nav_check
                    ) {

                        Intent intent =
                                new Intent(
                                        MainActivity.this,
                                        ScamCheck.class
                                );

                        startActivity(intent);

                        return true;


                    } else if (
                            id == R.id.nav_profile
                    ) {

                        Intent intent =
                                new Intent(
                                        MainActivity.this,
                                        Profile.class
                                );

                        startActivity(intent);

                        return true;
                    }

                    return false;
                }
        );
    }


    // =====================================================
    // LOAD USER
    // =====================================================

    private void loadUser() {

        FirebaseUser user =
                mAuth.getCurrentUser();


        if (user != null) {

            String email =
                    user.getEmail();


            if (email != null && !email.isEmpty()) {

                tvGreeting.setText(
                        "Hello, "
                                + email
                                + " 👋"
                );

            } else {

                tvGreeting.setText(
                        "Hello! 👋"
                );
            }

        } else {

            tvGreeting.setText(
                    "Hello, User 👋"
            );
        }
    }


    // =====================================================
    // LOCATION PERMISSION
    // =====================================================

    private void requestUserLocation() {

        if (
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                        &&
                        ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );

            return;
        }


        getCurrentLocation();
    }


    // =====================================================
    // GET CURRENT LOCATION
    // =====================================================

    private void getCurrentLocation() {

        if (
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                        &&
                        ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
        ) {

            return;
        }


        fusedLocationClient
                .getLastLocation()
                .addOnSuccessListener(location -> {

                    if (location != null) {

                        double latitude =
                                location.getLatitude();

                        double longitude =
                                location.getLongitude();


                        Log.d(
                                "LOCATION_ALERT",
                                "Latitude: "
                                        + latitude
                                        + " Longitude: "
                                        + longitude
                        );


                        saveUserLocation(
                                latitude,
                                longitude
                        );

                    } else {

                        Log.d(
                                "LOCATION_ALERT",
                                "No last location available"
                        );

                        Toast.makeText(
                                MainActivity.this,
                                "Unable to get current location",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "LOCATION_ALERT",
                            "Location error",
                            e
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Location error: "
                                    + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }


    // =====================================================
    // SAVE USER LOCATION TO SUPABASE
    // =====================================================


    private void saveUserLocation(double latitude, double longitude) {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            Log.e("LOCATION_ALERT", "Firebase user not logged in");
            return;
        }

        String firebaseUid = user.getUid();

        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encodedUid = URLEncoder.encode(
                        firebaseUid,
                        StandardCharsets.UTF_8.toString()
                );

                // Only save a location if the user has not saved one yet.
                String endpoint = supabaseUrl
                        + "/rest/v1/users"
                        + "?firebase_uid=eq." + encodedUid
                        + "&alert_location_set=eq.false";

                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PATCH");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                connection.setRequestProperty("apikey", supabaseKey);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + supabaseKey);
                connection.setRequestProperty(
                        "Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Prefer", "return=representation");

                JSONObject body = new JSONObject();
                body.put("latitude", latitude);
                body.put("longitude", longitude);
                body.put("alert_location_set", true);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                responseCode >= 200 && responseCode < 300
                                        ? connection.getInputStream()
                                        : connection.getErrorStream(),
                                StandardCharsets.UTF_8
                        )
                );

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (responseCode >= 200 && responseCode < 300) {
                    JSONArray updatedRows = new JSONArray(response.toString());

                    if (updatedRows.length() > 0) {
                        Log.d("LOCATION_ALERT",
                                "First alert location saved successfully");
                    } else {
                        Log.d("LOCATION_ALERT",
                                "Location already saved; keeping existing location");
                    }
                } else {
                    Log.e("LOCATION_ALERT",
                            "Save failed: HTTP " + responseCode + " " + response);
                }

            } catch (Exception e) {
                Log.e("LOCATION_ALERT", "Location save exception", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }



    // =====================================================
    // LOCATION PERMISSION RESULT
    // =====================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );


        if (
                requestCode
                        == LOCATION_PERMISSION_REQUEST
        ) {

            boolean granted = false;


            for (int result : grantResults) {

                if (
                        result
                                == PackageManager.PERMISSION_GRANTED
                ) {

                    granted = true;
                    break;
                }
            }


            if (granted) {

                getCurrentLocation();

            } else {

                Toast.makeText(
                        this,
                        "Location permission is required for nearby scam alerts",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }


    // =====================================================
    // LOAD HOME STATISTICS
    // =====================================================

    private void loadHomeStatistics() {

        executorService.execute(() -> {

            try {

                String endpoint =
                        supabaseUrl
                                + "/rest/v1/"
                                + "scam_report_statistics"
                                + "?select=*";


                String response =
                        sendGetRequest(
                                endpoint
                        );


                JSONArray array =
                        new JSONArray(
                                response
                        );


                if (array.length() == 0) {

                    return;
                }


                JSONObject data =
                        array.getJSONObject(0);


                int total =
                        data.optInt(
                                "total_reports",
                                0
                        );


                int thisWeek =
                        data.optInt(
                                "reports_this_week",
                                0
                        );


                int verified =
                        data.optInt(
                                "verified_reports",
                                0
                        );


                int pending =
                        data.optInt(
                                "pending_reports",
                                0
                        );


                runOnUiThread(() -> {

                    tvHomeTotalReports.setText(
                            String.valueOf(total)
                    );


                    tvHomeThisWeek.setText(
                            String.valueOf(thisWeek)
                    );


                    tvHomeVerified.setText(
                            String.valueOf(verified)
                    );


                    tvHomePending.setText(
                            String.valueOf(pending)
                    );

                });


            } catch (Exception e) {

                showError(
                        "Home statistics error: "
                                + e.getMessage()
                );
            }
        });
    }


    // =====================================================
    // LOAD HOME TRENDING
    // =====================================================


    private void loadHomeTrending() {
        executorService.execute(() -> {
            try {
                String endpoint = supabaseUrl
                        + "/rest/v1/scam_trending"
                        + "?select=scam_type,current_count,previous_count,percentage_change"
                        + "&order=percentage_change.desc";

                String response = sendGetRequest(endpoint);
                JSONArray array = new JSONArray(response);

                runOnUiThread(() -> {
                    homeTrendingContainer.removeAllViews();

                    if (array.length() == 0) {
                        addHomeMessage("No trending data available.");
                        return;
                    }

                    int limit = Math.min(array.length(), 3);
                    int maxCount = 1;

                    // Find one shared maximum for all displayed rows.
                    for (int i = 0; i < limit; i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item != null) {
                            maxCount = Math.max(
                                    maxCount,
                                    item.optInt("current_count", 0)
                            );
                        }
                    }

                    for (int i = 0; i < limit; i++) {
                        JSONObject item = array.optJSONObject(i);

                        if (item == null) {
                            continue;
                        }

                        String scamType =
                                item.optString("scam_type", "Unknown");

                        int currentCount =
                                item.optInt("current_count", 0);

                        int previousCount =
                                item.optInt("previous_count", 0);

                        int percentage =
                                item.optInt("percentage_change", 0);

                        addHomeTrendingRow(
                                i + 1,
                                scamType,
                                currentCount,
                                previousCount,
                                percentage,
                                maxCount
                        );
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        showError("Home trending error: " + e.getMessage())
                );
            }
        });
    }

    // =====================================================
    // HOME TRENDING ROW
    // =====================================================
    private int dpHome(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private void addHomeTrendingRow(
            int position,
            String scamType,
            int currentCount,
            int previousCount,
            int percentage,
            int maxCount
    ) {
        int padding = dpHome(8);
        int barHeight = dpHome(6);

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, padding, 0, padding);

        // Scam name and percentage
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(position + ". " + scamType);
        name.setTextSize(14);
        name.setTextColor(Color.rgb(51, 51, 51));

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
            change.setTextColor(Color.GRAY);
        }

        header.addView(name, nameParams);
        header.addView(change);
        item.addView(header);

        // Report counts
        TextView counts = new TextView(this);
        counts.setText(
                "Current: " + currentCount
                        + "  |  Previous: " + previousCount
        );
        counts.setTextSize(12);
        counts.setTextColor(Color.rgb(110, 110, 110));
        counts.setPadding(0, dpHome(4), 0, dpHome(8));
        item.addView(counts);

        // Bar track
        int safeMax = Math.max(1, maxCount);
        int safeCount = Math.max(0, Math.min(currentCount, safeMax));

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.graphics.drawable.GradientDrawable trackBackground =
                new android.graphics.drawable.GradientDrawable();
        trackBackground.setColor(Color.rgb(235, 235, 235));
        trackBackground.setCornerRadius(dpHome(4));
        track.setBackground(trackBackground);
        track.setClipToOutline(true);

        // Colored bar uses the current count as its weight.
        View fill = new View(this);

        int barColor;
        if (percentage > 0) {
            barColor = Color.rgb(198, 40, 40);
        } else if (percentage < 0) {
            barColor = Color.rgb(46, 125, 50);
        } else {
            barColor = Color.rgb(117, 117, 117);
        }

        android.graphics.drawable.GradientDrawable fillBackground =
                new android.graphics.drawable.GradientDrawable();
        fillBackground.setColor(barColor);
        fillBackground.setCornerRadius(dpHome(4));
        fill.setBackground(fillBackground);

        LinearLayout.LayoutParams fillParams =
                new LinearLayout.LayoutParams(
                        0,
                        barHeight,
                        safeCount
                );

        track.addView(fill, fillParams);

        // Remaining space stays gray.
        View remaining = new View(this);
        LinearLayout.LayoutParams remainingParams =
                new LinearLayout.LayoutParams(
                        0,
                        barHeight,
                        safeMax - safeCount
                );

        track.addView(remaining, remainingParams);

        item.addView(track, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                barHeight
        ));

        homeTrendingContainer.addView(item);
    }



    // =====================================================
    // HOME TRENDING EMPTY MESSAGE
    // =====================================================

    private void addHomeMessage(
            String message
    ) {

        TextView text =
                new TextView(this);


        text.setText(
                message
        );


        text.setTextSize(
                14
        );


        text.setTextColor(
                Color.GRAY
        );


        text.setPadding(
                0,
                10,
                0,
                10
        );


        homeTrendingContainer.addView(
                text
        );
    }


    // =====================================================
    // SUPABASE GET REQUEST
    // =====================================================

    private String sendGetRequest(
            String endpoint
    ) throws Exception {

        URL url =
                new URL(endpoint);


        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();


        connection.setRequestMethod(
                "GET"
        );


        connection.setRequestProperty(
                "apikey",
                supabaseKey
        );


        connection.setRequestProperty(
                "Authorization",
                "Bearer "
                        + supabaseKey
        );


        connection.setRequestProperty(
                "Accept",
                "application/json"
        );


        int responseCode =
                connection.getResponseCode();


        BufferedReader reader;


        if (
                responseCode >= 200
                        &&
                        responseCode < 300
        ) {

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection
                                            .getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

        } else {

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection
                                            .getErrorStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
        }


        StringBuilder response =
                new StringBuilder();


        String line;


        while (
                (line = reader.readLine())
                        != null
        ) {

            response.append(
                    line
            );
        }


        reader.close();

        connection.disconnect();


        if (
                responseCode < 200
                        ||
                        responseCode >= 300
        ) {

            throw new Exception(
                    "HTTP "
                            + responseCode
                            + ": "
                            + response
            );
        }


        return response.toString();
    }


    // =====================================================
    // ERROR
    // =====================================================

    private void showError(
            String message
    ) {

        runOnUiThread(() -> {

            Toast.makeText(
                    MainActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();

        });
    }


    // =====================================================
    // CLEANUP
    // =====================================================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        executorService.shutdown();
    }
}