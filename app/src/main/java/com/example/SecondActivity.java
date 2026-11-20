package com.example;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.ads.AdHelper;

/**
 * Secondary screen opened via {@code AdHelper.showInterstitial(this, ...)} to demonstrate
 * a natural transition and policy-safe back navigation (no surprise interstitial on back press).
 */
public class SecondActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        );
        setContentView(R.layout.activity_second);

        applySystemWindowInsets();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReturnMain).setOnClickListener(v -> finish());

        // 1-line Adaptive Banner with automatic lifecycle cleanup
        AdHelper.showBanner(this, findViewById(R.id.secondBannerContainer));
    }

    private void applySystemWindowInsets() {
        View header = findViewById(R.id.secondHeader);
        View bannerBar = findViewById(R.id.secondBannerSection);
        final int headerTopBase = header.getPaddingTop();
        final int bannerBottomBase = bannerBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.secondRoot), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(
                    header.getPaddingLeft(),
                    headerTopBase + systemBars.top,
                    header.getPaddingRight(),
                    header.getPaddingBottom()
            );
            bannerBar.setPadding(
                    bannerBar.getPaddingLeft(),
                    bannerBar.getPaddingTop(),
                    bannerBar.getPaddingRight(),
                    bannerBottomBase + systemBars.bottom
            );
            return windowInsets;
        });
    }
}
