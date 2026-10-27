
package com.example.scamheat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 2200;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable openNextScreen = () -> {
        // Change Login.class only if your existing login activity
        // uses a different Java class name.
        Intent intent = new Intent(SplashActivity.this, Login.class);
        startActivity(intent);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(android.graphics.Color.rgb(16, 24, 39));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(16, 24, 39));

        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.ivSplashLogo);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(1000);
        logo.startAnimation(fadeIn);

        handler.postDelayed(openNextScreen, SPLASH_DURATION);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openNextScreen);
        super.onDestroy();
    }
}
