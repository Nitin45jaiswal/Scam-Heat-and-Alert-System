
package com.example.scamheat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserManagement extends AppCompatActivity {

    private static final int BG = Color.rgb(8, 13, 26);
    private static final int SURFACE = Color.rgb(17, 26, 43);
    private static final int CARD = Color.rgb(21, 31, 51);
    private static final int BORDER = Color.rgb(41, 53, 77);
    private static final int PRIMARY = Color.rgb(245, 247, 255);
    private static final int SECONDARY = Color.rgb(155, 169, 195);
    private static final int RED = Color.rgb(255, 49, 85);
    private static final int BLUE = Color.rgb(57, 167, 255);
    private static final int GREEN = Color.rgb(54, 224, 161);

    private TextView tvTotalUsers, tvUserCount;
    private TextInputEditText etSearchUser;
    private LinearLayout userContainer, emptyState;
    private View rootView;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final ArrayList<JSONObject> allUsers = new ArrayList<>();

    private String baseUrl, apiKey;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        baseUrl = getString(R.string.supabase_url).trim();
        apiKey = getString(R.string.supabase_publishable_key).trim();

        setContentView(R.layout.user_management);

        rootView = findViewById(android.R.id.content);
        rootView.setBackgroundColor(BG);

        tvTotalUsers = findViewById(R.id.tvTotalUsers);
        tvUserCount = findViewById(R.id.tvUserCount);
        etSearchUser = findViewById(R.id.etSearchUser);
        userContainer = findViewById(R.id.userContainer);
        emptyState = findViewById(R.id.emptyState);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setColorFilter(PRIMARY);
        btnBack.setOnClickListener(v -> finish());

        styleLayout();
        setupSearch();

        loadUsers();
    }

    private void styleLayout() {
        // Header title and subtitle
        ScrollView scroll = (ScrollView) findViewById(
                R.id.tvTotalUsers
        ).getParent().getParent().getParent().getParent();

        scroll.setBackgroundColor(BG);

        styleAllText((LinearLayout) scroll.getChildAt(0));

        tvTotalUsers.setTextColor(RED);
        tvUserCount.setTextColor(SECONDARY);

        // Search field
        View searchLayout = findViewById(R.id.inputSearchUser);
        if (searchLayout instanceof com.google.android.material.textfield.TextInputLayout) {
            com.google.android.material.textfield.TextInputLayout input =
                    (com.google.android.material.textfield.TextInputLayout) searchLayout;

            input.setBoxBackgroundColor(SURFACE);
            input.setBoxStrokeColor(BLUE);
            input.setHintTextColor(
                    android.content.res.ColorStateList.valueOf(SECONDARY)
            );
            input.setDefaultHintTextColor(
                    android.content.res.ColorStateList.valueOf(SECONDARY)
            );
            input.setStartIconTintList(
                    android.content.res.ColorStateList.valueOf(SECONDARY)
            );
        }

        etSearchUser.setTextColor(PRIMARY);
        etSearchUser.setHintTextColor(SECONDARY);

        styleEmptyState();
    }

    private void styleAllText(LinearLayout layout) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);

            if (child instanceof TextView) {
                TextView text = (TextView) child;
                text.setTextColor(PRIMARY);

            } else if (child instanceof LinearLayout) {
                styleAllText((LinearLayout) child);

            } else if (child instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) child;
                card.setCardBackgroundColor(SURFACE);
                card.setStrokeColor(BORDER);
                card.setStrokeWidth(dp(1));
                card.setCardElevation(0);

                if (card.getChildAt(0) instanceof LinearLayout) {
                    styleAllText((LinearLayout) card.getChildAt(0));
                }
            }
        }
    }

    private void styleEmptyState() {
        emptyState.setBackground(makeBackground(SURFACE, BORDER, 18));

        for (int i = 0; i < emptyState.getChildCount(); i++) {
            View child = emptyState.getChildAt(i);

            if (child instanceof TextView) {
                TextView text = (TextView) child;
                text.setTextColor(
                        i == 0 ? PRIMARY : SECONDARY
                );
            }
        }
    }

    private void setupSearch() {
        etSearchUser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                filterUsers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadUsers() {
        if (loading) return;

        loading = true;
        tvUserCount.setText("Loading users...");
        userContainer.removeAllViews();
        emptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String endpoint = baseUrl.replaceAll("/+$", "")
                        + "/rest/v1/users"
                        + "?select=name,email,phone,created_at"
                        + "&order=created_at.desc&limit=500";

                connection = (HttpURLConnection)
                        new URL(endpoint).openConnection();

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

                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "HTTP " + code + ": " + response
                    );
                }

                JSONArray result = new JSONArray(response.toString());
                ArrayList<JSONObject> fetched = new ArrayList<>();

                for (int i = 0; i < result.length(); i++) {
                    JSONObject user = result.optJSONObject(i);
                    if (user != null) fetched.add(user);
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    allUsers.clear();
                    allUsers.addAll(fetched);

                    tvTotalUsers.setText(
                            String.valueOf(allUsers.size())
                    );

                    filterUsers(
                            etSearchUser.getText() == null
                                    ? "" : etSearchUser.getText().toString()
                    );

                    loading = false;
                });

            } catch (Exception e) {
                String message = e.getMessage() == null
                        ? "Unknown error" : e.getMessage();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    tvTotalUsers.setText("!");
                    tvUserCount.setText("Unable to load users");
                    userContainer.removeAllViews();
                    emptyState.setVisibility(View.VISIBLE);

                    Toast.makeText(
                            UserManagement.this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();

                    loading = false;
                });

            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void filterUsers(String query) {
        if (allUsers.isEmpty()) {
            tvUserCount.setText("0 users found");
            userContainer.removeAllViews();
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        String q = query == null
                ? "" : query.trim().toLowerCase(Locale.ROOT);

        userContainer.removeAllViews();
        int visibleCount = 0;

        for (int i = 0; i < allUsers.size(); i++) {
            JSONObject user = allUsers.get(i);

            String name = user.optString("name", "");
            String email = user.optString("email", "");
            String phone = user.optString("phone", "");

            String combined = (name + " " + email + " " + phone)
                    .toLowerCase(Locale.ROOT);

            if (combined.contains(q)) {
                addUserCard(user, i + 1);
                visibleCount++;
            }
        }

        tvUserCount.setText(
                visibleCount + (visibleCount == 1
                        ? " user found" : " users found")
        );

        emptyState.setVisibility(
                visibleCount == 0 ? View.VISIBLE : View.GONE
        );

        if (visibleCount == 0) {
            TextView heading = (TextView) emptyState.getChildAt(1);
            if (heading != null) heading.setText("No matching users");

            TextView description =
                    (TextView) emptyState.getChildAt(2);
            if (description != null) {
                description.setText("Try a different name, email or phone.");
            }
        } else {
            TextView heading = (TextView) emptyState.getChildAt(1);
            if (heading != null) heading.setText("No users found");

            TextView description =
                    (TextView) emptyState.getChildAt(2);
            if (description != null) {
                description.setText(
                        "Registered users will appear here."
                );
            }
        }
    }

    private void addUserCard(JSONObject user, int number) {
        String name = clean(user.optString("name", ""), "Unnamed user");
        String email = clean(user.optString("email", ""), "Not provided");
        String phone = clean(user.optString("phone", ""), "Not provided");
        String created = user.optString("created_at", "");

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(CARD);
        card.setStrokeColor(BORDER);
        card.setStrokeWidth(dp(1));
        card.setCardElevation(0);
        card.setRadius(dp(17));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(12));
        userContainer.addView(card, cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.addView(content);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView avatar = makeText(
                initials(name), 17, PRIMARY, true
        );
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(makeBackground(
                0xFF263A5A, BLUE, 40
        ));

        header.addView(avatar, new LinearLayout.LayoutParams(
                dp(48), dp(48)
        ));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(12), 0, 0, 0);

        identity.addView(makeText(name, 16, PRIMARY, true));
        identity.addView(makeText(
                "USER #" + number, 11, BLUE, true
        ));

        header.addView(identity, new LinearLayout.LayoutParams(
                0, -2, 1
        ));

        TextView dot = makeText("●", 12, GREEN, true);
        header.addView(dot);

        content.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(14), 0, dp(8));
        content.addView(divider, dividerParams);

        addDetail(content, "EMAIL ADDRESS", email);
        addDetail(content, "PHONE NUMBER", phone);

        if (!created.isEmpty() && !created.equals("null")) {
            addDetail(content, "REGISTERED ON", formatDate(created));
        }
    }

    private void addDetail(
            LinearLayout parent, String label, String value
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        row.addView(makeText(label, 10, SECONDARY, true));
        row.addView(makeText(value, 13, PRIMARY, false));

        parent.addView(row);
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()
                || value.equals("null")) {
            return fallback;
        }
        return value.trim();
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "U";

        String[] parts = name.trim().split("\\s+");

        if (parts.length > 1) {
            return (parts[0].substring(0, 1)
                    + parts[parts.length - 1].substring(0, 1))
                    .toUpperCase(Locale.ROOT);
        }

        return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String formatDate(String value) {
        try {
            String normalized = value;

            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(
                        0, normalized.length() - 1
                ) + "+0000";
            }

            if (normalized.length() >= 6
                    && normalized.charAt(normalized.length() - 3) == ':') {
                normalized = normalized.substring(
                        0, normalized.length() - 3
                ) + normalized.substring(normalized.length() - 2);
            }

            SimpleDateFormat input = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    Locale.US
            );
            input.setLenient(true);

            Date date = input.parse(normalized);
            if (date == null) return value;

            return new SimpleDateFormat(
                    "dd MMM yyyy, hh:mm a",
                    Locale.getDefault()
            ).format(date);

        } catch (Exception e) {
            return value;
        }
    }

    private TextView makeText(
            String value, int size, int color, boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(
                Typeface.create("sans-serif",
                        bold ? Typeface.BOLD : Typeface.NORMAL)
        );
        return view;
    }

    private GradientDrawable makeBackground(
            int fill, int stroke, int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
