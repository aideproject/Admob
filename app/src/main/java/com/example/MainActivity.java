package com.example;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.ads.AdConfig;
import com.example.ads.AdHelper;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/**
 * Demo Activity demonstrating one-line AdMob calls through {@link AdHelper}.
 */
public class MainActivity extends AppCompatActivity implements AdHelper.AdStatusListener {

    private static final int MAX_LOG_LINES = 14;
    private final Deque<String> logLines = new ArrayDeque<>();
    private final Handler cooldownTickerHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private int coinBalance = 100;

    // Views
    private TextView tvBuildModeBadge;
    private TextView tvCoinBalance;
    private TextView tvSdkStatus;
    private TextView tvCooldownStatus;
    private TextView pillInterstitial;
    private TextView pillRewarded;
    private TextView pillRewardedInt;
    private TextView pillAppOpen;
    private TextView tvEventLog;
    private FrameLayout bannerContainer;
    private LinearLayout bannerSection;
    private FrameLayout nativeAdContainer;
    private MaterialSwitch switchRemoveAds;

    private final Runnable cooldownTicker = new Runnable() {
        @Override
        public void run() {
            updateCooldownLabel();
            cooldownTickerHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        );
        setContentView(R.layout.activity_main);

        applySystemWindowInsets();
        bindViews();
        setupListeners();
        updateCoinUi();
        updateStatusDashboard();

        // Register optional live status/log listener
        AdHelper.setStatusListener(this);

        // =====================================================================
        // ONE-LINE INITIALIZATION (UMP GDPR Consent + MobileAds SDK + Preload)
        // =====================================================================
        AdHelper.init(this, () -> {
            AdHelper.showBanner(this, bannerContainer);
            AdHelper.showNativeAd(this, nativeAdContainer);
            updateStatusDashboard();
        });
    }

    private void applySystemWindowInsets() {
        View headerContainer = findViewById(R.id.headerContainer);
        View bannerBar = findViewById(R.id.bannerSection);
        final int headerTopBase = headerContainer.getPaddingTop();
        final int bannerBottomBase = bannerBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootContainer), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            headerContainer.setPadding(
                    headerContainer.getPaddingLeft(),
                    headerTopBase + systemBars.top,
                    headerContainer.getPaddingRight(),
                    headerContainer.getPaddingBottom()
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

    private void bindViews() {
        tvBuildModeBadge = findViewById(R.id.tvBuildModeBadge);
        tvCoinBalance = findViewById(R.id.tvCoinBalance);
        tvSdkStatus = findViewById(R.id.tvSdkStatus);
        tvCooldownStatus = findViewById(R.id.tvCooldownStatus);
        pillInterstitial = findViewById(R.id.pillInterstitial);
        pillRewarded = findViewById(R.id.pillRewarded);
        pillRewardedInt = findViewById(R.id.pillRewardedInt);
        pillAppOpen = findViewById(R.id.pillAppOpen);
        tvEventLog = findViewById(R.id.tvEventLog);
        bannerContainer = findViewById(R.id.bannerContainer);
        bannerSection = findViewById(R.id.bannerSection);
        nativeAdContainer = findViewById(R.id.nativeAdContainer);
        switchRemoveAds = findViewById(R.id.switchRemoveAds);

        tvBuildModeBadge.setText(AdConfig.isTestMode()
                ? R.string.badge_test_mode
                : R.string.badge_live_mode);
    }

    private void setupListeners() {
        // 1. ONE-LINE INTERSTITIAL AD (Safe screen transition + cooldown protection)
        findViewById(R.id.cardInterstitial).setOnClickListener(v ->
                AdHelper.showInterstitial(this, () ->
                        startActivity(new Intent(MainActivity.this, SecondActivity.class)))
        );

        // 2. ONE-LINE REWARDED AD (+50 coins fallback or actual AdMob reward amount)
        findViewById(R.id.cardRewarded).setOnClickListener(v -> {
            if (AdHelper.isAdsRemoved()) {
                Toast.makeText(this, R.string.msg_ads_disabled, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AdHelper.isRewardedReady()) {
                Toast.makeText(this, R.string.msg_ad_not_ready, Toast.LENGTH_SHORT).show();
            }
            AdHelper.showRewarded(this, amount -> addCoins(amount > 0 ? amount : 50));
        });

        // 3. ONE-LINE REWARDED INTERSTITIAL AD
        findViewById(R.id.cardRewardedInterstitial).setOnClickListener(v -> {
            if (AdHelper.isAdsRemoved()) {
                Toast.makeText(this, R.string.msg_ads_disabled, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AdHelper.isRewardedInterstitialReady()) {
                Toast.makeText(this, R.string.msg_ad_not_ready, Toast.LENGTH_SHORT).show();
            }
            AdHelper.showRewardedInterstitial(this, amount -> addCoins(amount > 0 ? amount : 25));
        });

        // 4. ONE-LINE APP OPEN AD (Also runs automatically on foreground resume)
        findViewById(R.id.cardAppOpen).setOnClickListener(v -> {
            if (AdHelper.isAdsRemoved()) {
                Toast.makeText(this, R.string.msg_ads_disabled, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AdHelper.isAppOpenReady()) {
                Toast.makeText(this, R.string.msg_ad_not_ready, Toast.LENGTH_SHORT).show();
            }
            AdHelper.showAppOpen(this);
        });

        // 5. ONE-LINE NATIVE AD RELOAD
        findViewById(R.id.btnReloadNative).setOnClickListener(v -> {
            if (AdHelper.isAdsRemoved()) {
                Toast.makeText(this, R.string.msg_ads_disabled, Toast.LENGTH_SHORT).show();
                return;
            }
            AdHelper.showNativeAd(this, nativeAdContainer);
        });

        // 6. ONE-LINE GDPR PRIVACY OPTIONS
        findViewById(R.id.btnPrivacyOptions).setOnClickListener(v -> {
            if (!AdHelper.isPrivacyOptionsRequired()) {
                Toast.makeText(this, R.string.msg_privacy_not_required, Toast.LENGTH_SHORT).show();
            }
            AdHelper.showPrivacyOptions(this);
        });

        // 7. ONE-LINE REMOVE ADS / PREMIUM TOGGLE
        switchRemoveAds.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AdHelper.setAdsRemoved(isChecked);
            if (isChecked) {
                bannerSection.setVisibility(View.GONE);
                bannerContainer.removeAllViews();
                showNativePlaceholder(getString(R.string.native_ads_removed_placeholder));
            } else {
                bannerSection.setVisibility(View.VISIBLE);
                showNativePlaceholder(getString(R.string.native_loading_placeholder));
                AdHelper.init(this, () -> {
                    AdHelper.showBanner(this, bannerContainer);
                    AdHelper.showNativeAd(this, nativeAdContainer);
                });
            }
            updateStatusDashboard();
        });

        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            logLines.clear();
            tvEventLog.setText(R.string.log_initial);
        });
    }

    private void showNativePlaceholder(@NonNull String message) {
        nativeAdContainer.setVisibility(View.VISIBLE);
        nativeAdContainer.removeAllViews();
        TextView placeholder = new TextView(this);
        placeholder.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        placeholder.setBackgroundResource(R.drawable.bg_native_ad_container);
        placeholder.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (28 * getResources().getDisplayMetrics().density);
        placeholder.setPadding(pad, pad, pad, pad);
        placeholder.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        placeholder.setTextSize(13f);
        placeholder.setText(message);
        nativeAdContainer.addView(placeholder);
    }

    private void addCoins(int amount) {
        coinBalance += amount;
        updateCoinUi();
        Toast.makeText(this, getString(R.string.msg_reward_earned, amount), Toast.LENGTH_SHORT).show();
    }

    private void updateCoinUi() {
        tvCoinBalance.setText(getString(R.string.label_coins_format, coinBalance));
    }

    private void updateStatusDashboard() {
        if (AdHelper.isAdsRemoved()) {
            tvSdkStatus.setText(R.string.status_disabled);
        } else if (AdHelper.isSdkInitialized() && AdHelper.canRequestAds()) {
            tvSdkStatus.setText(R.string.status_ready);
        } else {
            tvSdkStatus.setText(R.string.status_initializing);
        }

        updateCooldownLabel();
        stylePill(pillInterstitial, "INT", AdHelper.isInterstitialReady());
        stylePill(pillRewarded, "RWD", AdHelper.isRewardedReady());
        stylePill(pillRewardedInt, "R-INT", AdHelper.isRewardedInterstitialReady());
        stylePill(pillAppOpen, "OPEN", AdHelper.isAppOpenReady());
    }

    private void updateCooldownLabel() {
        long remaining = AdHelper.getRemainingInterstitialCooldownSeconds();
        if (AdHelper.isAdsRemoved()) {
            tvCooldownStatus.setText(R.string.status_disabled);
            tvCooldownStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        } else if (remaining > 0) {
            tvCooldownStatus.setText(getString(R.string.status_cooldown_wait, remaining));
            tvCooldownStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_tertiary));
        } else {
            tvCooldownStatus.setText(R.string.status_cooldown_ready);
            tvCooldownStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        }
    }

    private void stylePill(@NonNull TextView pill, @NonNull String prefix, boolean isReady) {
        int bgColor;
        int textColor;
        String suffix;
        if (AdHelper.isAdsRemoved()) {
            bgColor = ContextCompat.getColor(this, R.color.status_off_bg);
            textColor = ContextCompat.getColor(this, R.color.status_off_text);
            suffix = "OFF";
        } else if (isReady) {
            bgColor = ContextCompat.getColor(this, R.color.status_ready_bg);
            textColor = ContextCompat.getColor(this, R.color.status_ready_text);
            suffix = "OK";
        } else {
            bgColor = ContextCompat.getColor(this, R.color.status_wait_bg);
            textColor = ContextCompat.getColor(this, R.color.status_wait_text);
            suffix = "…";
        }
        pill.setText(prefix + ": " + suffix);
        pill.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        pill.setTextColor(textColor);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AdHelper.setStatusListener(this);
        updateStatusDashboard();
        cooldownTickerHandler.post(cooldownTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cooldownTickerHandler.removeCallbacks(cooldownTicker);
    }

    @Override
    protected void onDestroy() {
        AdHelper.setStatusListener(null);
        super.onDestroy();
    }

    @Override
    public void onAdEvent(@NonNull String message) {
        String stamped = "[" + timeFormat.format(new Date()) + "] " + message;
        logLines.addFirst(stamped);
        while (logLines.size() > MAX_LOG_LINES) {
            logLines.removeLast();
        }
        StringBuilder sb = new StringBuilder();
        for (String line : logLines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        tvEventLog.setText(sb.toString());
    }

    @Override
    public void onAdStateChanged() {
        updateStatusDashboard();
    }
}
