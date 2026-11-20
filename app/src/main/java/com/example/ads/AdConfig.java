package com.example.ads;

import com.example.BuildConfig;

/**
 * Centralized AdMob Unit IDs & Policy Safeguard Configuration.
 *
 * <p>POLICY RULE (Invalid Traffic Protection):
 * When running a DEBUG build ({@link BuildConfig#DEBUG} == true), official Google Test Ad Unit IDs
 * are automatically selected so developers never accidentally load or click live production ads.
 *
 * <p>BEFORE PUBLISHING TO GOOGLE PLAY:
 * 1. Replace the {@code PROD_*} constants below with your real Ad Unit IDs from AdMob Console.
 * 2. Replace {@code com.google.android.gms.ads.APPLICATION_ID} in AndroidManifest.xml with your
 *    real AdMob App ID.
 */
public final class AdConfig {

    private AdConfig() {
        // Utility config class
    }

    // =========================================================================
    // 1. Official Google Test Ad Unit IDs (Automatically used in DEBUG builds)
    // =========================================================================
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    private static final String TEST_REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379";
    private static final String TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110";
    private static final String TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";

    // =========================================================================
    // 2. Production Ad Unit IDs (Replace with your real AdMob IDs before release)
    // =========================================================================
    private static final String PROD_BANNER = "ca-app-pub-XXXXXXXXXXXXXXXX/1111111111";
    private static final String PROD_INTERSTITIAL = "ca-app-pub-XXXXXXXXXXXXXXXX/2222222222";
    private static final String PROD_REWARDED = "ca-app-pub-XXXXXXXXXXXXXXXX/3333333333";
    private static final String PROD_REWARDED_INTERSTITIAL = "ca-app-pub-XXXXXXXXXXXXXXXX/4444444444";
    private static final String PROD_NATIVE = "ca-app-pub-XXXXXXXXXXXXXXXX/5555555555";
    private static final String PROD_APP_OPEN = "ca-app-pub-XXXXXXXXXXXXXXXX/6666666666";

    // =========================================================================
    // 3. Policy Safeguard Defaults
    // =========================================================================
    /** Minimum wait time (seconds) between two Interstitial impressions. */
    public static final int DEFAULT_INTERSTITIAL_COOLDOWN_SECONDS = 45;

    /** Absolute minimum floor so callers cannot accidentally set 0s spam intervals. */
    public static final int MIN_SAFE_INTERSTITIAL_COOLDOWN_SECONDS = 20;

    /** Minimum wait time (seconds) between two App Open impressions. */
    public static final int DEFAULT_APP_OPEN_COOLDOWN_SECONDS = 60;

    /** Google AdMob Policy: Cached App Open ads expire after 4 hours. */
    public static final long APP_OPEN_MAX_CACHE_AGE_MS = 4L * 60L * 60L * 1000L;

    /** Maximum consecutive retry attempts per ad format to prevent request spamming. */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    public static boolean isTestMode() {
        return BuildConfig.DEBUG;
    }

    public static String getBannerId() {
        return isTestMode() ? TEST_BANNER : PROD_BANNER;
    }

    public static String getInterstitialId() {
        return isTestMode() ? TEST_INTERSTITIAL : PROD_INTERSTITIAL;
    }

    public static String getRewardedId() {
        return isTestMode() ? TEST_REWARDED : PROD_REWARDED;
    }

    public static String getRewardedInterstitialId() {
        return isTestMode() ? TEST_REWARDED_INTERSTITIAL : PROD_REWARDED_INTERSTITIAL;
    }

    public static String getNativeId() {
        return isTestMode() ? TEST_NATIVE : PROD_NATIVE;
    }

    public static String getAppOpenId() {
        return isTestMode() ? TEST_APP_OPEN : PROD_APP_OPEN;
    }
}
