
package com.example.scamheat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private static final double HOTSPOT_RADIUS_METERS = 5000.0;
    private static final int HOTSPOT_MIN_REPORTS = 10;

    private static final String ALL_CATEGORIES = "All scam categories";

    private GoogleMap googleMap;

    private TextView tvMapStatus;
    private Spinner categorySpinner;
    private MaterialButton refreshButton;
    private MaterialButton locationButton;

    private FusedLocationProviderClient locationClient;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final List<ScamReport> allReports = new ArrayList<>();

    private boolean categoriesReady = false;
    private boolean firstCameraMove = true;
    private boolean isLoading = false;

    private static class ScamReport {
        String id;
        String status;
        String scamType;
        String description;
        String suspiciousInfo;

        double latitude;
        double longitude;

        LatLng position() {
            return new LatLng(latitude, longitude);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        tvMapStatus = findViewById(R.id.tvMapStatus);
        categorySpinner = findViewById(R.id.categorySpinner);
        refreshButton = findViewById(R.id.btnRefreshMap);
        locationButton = findViewById(R.id.btnMyLocation);

        locationClient =
                LocationServices.getFusedLocationProviderClient(this);

        setupCategorySpinner();

        refreshButton.setOnClickListener(v -> loadReports());

        locationButton.setOnClickListener(v -> requestLocation());

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.googleMap);

        if (mapFragment == null) {
            tvMapStatus.setText("Map could not be initialized.");
            return;
        }

        mapFragment.getMapAsync(this);
    }

    private void setupCategorySpinner() {
        List<String> initialCategories = new ArrayList<>();
        initialCategories.add(ALL_CATEGORIES);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                initialCategories
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        categorySpinner.setAdapter(adapter);

        categorySpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        if (categoriesReady) {
                            displayReports();
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        googleMap.setInfoWindowAdapter(
                new GoogleMap.InfoWindowAdapter() {

                    @Override
                    public View getInfoWindow(Marker marker) {
                        return null;
                    }

                    @Override
                    public View getInfoContents(Marker marker) {
                        android.widget.LinearLayout layout =
                                new android.widget.LinearLayout(
                                        MapActivity.this);

                        layout.setOrientation(
                                android.widget.LinearLayout.VERTICAL);

                        layout.setPadding(24, 12, 24, 12);

                        TextView title = new TextView(MapActivity.this);
                        title.setText(marker.getTitle());
                        title.setTextColor(Color.BLACK);
                        title.setTextSize(16);
                        title.setTypeface(
                                null,
                                android.graphics.Typeface.BOLD);

                        TextView details = new TextView(MapActivity.this);
                        details.setText(marker.getSnippet());
                        details.setTextColor(Color.DKGRAY);
                        details.setTextSize(14);

                        layout.addView(title);
                        layout.addView(details);

                        return layout;
                    }
                });

        googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                        new LatLng(20.5937, 78.9629),
                        4.5f
                )
        );

        loadReports();
    }

    private void loadReports() {
        if (isLoading) {
            return;
        }

        isLoading = true;

        tvMapStatus.setText("Loading scam reports...");

        refreshButton.setEnabled(false);
        refreshButton.setAlpha(0.5f);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String supabaseUrl =
                        getString(R.string.supabase_url).trim();

                String apiKey =
                        getString(R.string.supabase_publishable_key).trim();

                while (supabaseUrl.endsWith("/")) {
                    supabaseUrl = supabaseUrl.substring(
                            0, supabaseUrl.length() - 1);
                }

                String endpoint = supabaseUrl
                        + "/rest/v1/reports"
                        + "?select=id,status,scam_type,description,"
                        + "latitude,longitude,suspicious_info"
                        + "&latitude=not.is.null"
                        + "&longitude=not.is.null"
                        + "&order=created_at.desc"
                        + "&limit=1000";

                connection = (HttpURLConnection)
                        new URL(endpoint).openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                connection.setRequestProperty("apikey", apiKey);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + apiKey);
                connection.setRequestProperty(
                        "Accept", "application/json");

                int responseCode = connection.getResponseCode();

                InputStream stream = responseCode >= 200
                        && responseCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                if (stream == null) {
                    throw new Exception(
                            "Supabase HTTP " + responseCode);
                }

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                stream, StandardCharsets.UTF_8))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception(
                            "Supabase HTTP " + responseCode
                                    + ": " + response);
                }

                JSONArray jsonArray =
                        new JSONArray(response.toString());

                List<ScamReport> fetchedReports =
                        new ArrayList<>();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject object = jsonArray.getJSONObject(i);

                    if (object.isNull("latitude")
                            || object.isNull("longitude")) {
                        continue;
                    }

                    ScamReport report = new ScamReport();

                    report.id = object.optString("id", "");
                    report.status = object.optString(
                            "status", "Pending");
                    report.scamType = object.optString(
                            "scam_type", "Unknown Scam");
                    report.description = object.optString(
                            "description", "");
                    report.suspiciousInfo = object.optString(
                            "suspicious_info", "");

                    report.latitude = object.getDouble("latitude");
                    report.longitude = object.getDouble("longitude");

                    if (Double.isFinite(report.latitude)
                            && Double.isFinite(report.longitude)
                            && report.latitude >= -90
                            && report.latitude <= 90
                            && report.longitude >= -180
                            && report.longitude <= 180) {

                        fetchedReports.add(report);
                    }
                }

                runOnUiThread(() -> {
                    allReports.clear();
                    allReports.addAll(fetchedReports);

                    updateCategories();
                    displayReports();

                    finishLoading();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvMapStatus.setText("Failed to load reports.");

                    Toast.makeText(
                            MapActivity.this,
                            e.getMessage() == null
                                    ? "Could not load scam reports."
                                    : e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    finishLoading();
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void finishLoading() {
        isLoading = false;

        refreshButton.setEnabled(true);
        refreshButton.setAlpha(1.0f);
    }

    private void updateCategories() {
        TreeSet<String> sortedCategories = new TreeSet<>(
                String.CASE_INSENSITIVE_ORDER);

        for (ScamReport report : allReports) {
            if (report.scamType != null
                    && !report.scamType.trim().isEmpty()) {
                sortedCategories.add(report.scamType.trim());
            }
        }

        String previousSelection = ALL_CATEGORIES;

        if (categorySpinner.getSelectedItem() != null) {
            previousSelection =
                    categorySpinner.getSelectedItem().toString();
        }

        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        categories.addAll(sortedCategories);

        categoriesReady = false;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                categories
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        categorySpinner.setAdapter(adapter);

        int selectedIndex = categories.indexOf(previousSelection);

        categorySpinner.setSelection(
                selectedIndex >= 0 ? selectedIndex : 0,
                false
        );

        categoriesReady = true;
    }

    private List<ScamReport> getFilteredReports() {
        List<ScamReport> filtered = new ArrayList<>();

        String selected = ALL_CATEGORIES;

        if (categorySpinner.getSelectedItem() != null) {
            selected = categorySpinner.getSelectedItem().toString();
        }

        for (ScamReport report : allReports) {
            if (ALL_CATEGORIES.equals(selected)
                    || selected.equals(report.scamType)) {
                filtered.add(report);
            }
        }

        return filtered;
    }


    private void displayReports() {
        if (googleMap == null) {
            return;
        }

        googleMap.clear();

        // Apply the selected category filter first.
        List<ScamReport> reports = getFilteredReports();

        // Show one red marker for every filtered report.
        for (ScamReport report : reports) {
            String title = report.scamType == null
                    ? "Unknown Scam"
                    : report.scamType;

            String snippet = "Status: " + report.status
                    + "\nDetails: " + report.description
                    + "\nSuspicious information: "
                    + report.suspiciousInfo;

            googleMap.addMarker(
                    new MarkerOptions()
                            .position(report.position())
                            .title(title)
                            .snippet(snippet)
            );
        }

        // Find hotspot candidates.
        // A candidate qualifies if at least 10 reports are within 5 km.
        List<Integer> candidates = new ArrayList<>();
        List<Integer> nearbyCounts = new ArrayList<>();

        for (int i = 0; i < reports.size(); i++) {
            ScamReport center = reports.get(i);
            int nearbyCount = 0;

            for (ScamReport candidate : reports) {
                float[] distance = new float[1];

                Location.distanceBetween(
                        center.latitude,
                        center.longitude,
                        candidate.latitude,
                        candidate.longitude,
                        distance
                );

                if (distance[0] <= HOTSPOT_RADIUS_METERS) {
                    nearbyCount++;
                }
            }

            if (nearbyCount >= HOTSPOT_MIN_REPORTS) {
                candidates.add(i);
                nearbyCounts.add(nearbyCount);
            }
        }

        // Sort candidates by density, highest first.
        // This lets the densest candidate represent nearby overlaps.
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                if (nearbyCounts.get(j) > nearbyCounts.get(i)) {
                    int tempIndex = candidates.get(i);
                    candidates.set(i, candidates.get(j));
                    candidates.set(j, tempIndex);

                    int tempCount = nearbyCounts.get(i);
                    nearbyCounts.set(i, nearbyCounts.get(j));
                    nearbyCounts.set(j, tempCount);
                }
            }
        }

        List<LatLng> drawnHotspots = new ArrayList<>();

        for (int index : candidates) {
            ScamReport center = reports.get(index);
            LatLng centerPosition = center.position();

            boolean overlapsExistingHotspot = false;

            // Suppress redundant circles when their centers are within
            // 5 km of a hotspot already drawn.
            for (LatLng existingCenter : drawnHotspots) {
                float[] distance = new float[1];

                Location.distanceBetween(
                        centerPosition.latitude,
                        centerPosition.longitude,
                        existingCenter.latitude,
                        existingCenter.longitude,
                        distance
                );

                if (distance[0] <= HOTSPOT_RADIUS_METERS) {
                    overlapsExistingHotspot = true;
                    break;
                }
            }

            if (!overlapsExistingHotspot) {
                googleMap.addCircle(
                        new CircleOptions()
                                .center(centerPosition)
                                .radius(HOTSPOT_RADIUS_METERS)
                                .strokeColor(Color.RED)
                                .fillColor(0x22FF0000)
                                .strokeWidth(3)
                                .zIndex(1)
                );

                drawnHotspots.add(centerPosition);
            }
        }

        // Display the count after category filtering.
        tvMapStatus.setText(
                "Reports on map\n"
                        + reports.size() + " reports • "
                        + drawnHotspots.size() + " hotspots"
        );

        // Preserve the initial camera positioning behavior.
        if (firstCameraMove) {
            if (!reports.isEmpty()) {
                googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                                reports.get(0).position(),
                                10f
                        )
                );
            } else {
                googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                                new LatLng(20.5937, 78.9629),
                                4.5f
                        )
                );
            }

            firstCameraMove = false;
        }
    }

    private void requestLocation() {
        boolean fineGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            showMyLocation();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = false;

            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                showMyLocation();
            } else {
                Toast.makeText(
                        this,
                        "Location permission denied.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void showMyLocation() {
        if (googleMap == null) {
            return;
        }

        boolean fineGranted = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            return;
        }

        try {
            googleMap.setMyLocationEnabled(true);

            locationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng current = new LatLng(
                                    location.getLatitude(),
                                    location.getLongitude());

                            googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                            current, 15f));

                        } else {
                            Toast.makeText(
                                    MapActivity.this,
                                    "Location unavailable. Enable device location and try again.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    })
                    .addOnFailureListener(this, error ->
                            Toast.makeText(
                                    MapActivity.this,
                                    "Could not get your location.",
                                    Toast.LENGTH_SHORT
                            ).show());

        } catch (SecurityException e) {
            Toast.makeText(
                    this,
                    "Location permission is required.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
