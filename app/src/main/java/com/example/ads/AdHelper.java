package com.example.ads;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.example.R;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * All-in-one, Policy-Compliant Google AdMob Helper (Java + XML).
 *
 * <p>Designed so {@code MainActivity} or any other Activity can trigger any ad format in ONE LINE:
 * <pre>
 * AdHelper.init(this, () -> AdHelper.showBanner(this, findViewById(R.id.bannerContainer)));
 * AdHelper.showInterstitial(this, () -> openNextScreen());
 * AdHelper.showRewarded(this, amount -> addCoins(amount));
 * AdHelper.showRewardedInterstitial(this, amount -> addCoins(amount));
 * AdHelper.showNativeAd(this, findViewById(R.id.nativeAdContainer));
 * AdHelper.showAppOpen(this);
 * AdHelper.showPrivacyOptions(this);
 * </pre>
 */
public final class AdHelper {

    private static final String TAG = "AdHelper";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    // SDK & Consent State
    private static Context appContext;
    private static ConsentInformation consentInformation;
    private static final AtomicBoolean isMobileAdsInitCalled = new AtomicBoolean(false);
    private static volatile boolean isSdkInitialized = false;
    private static volatile boolean adsRemoved = false;
    private static volatile boolean isShowingFullScreenAd = false;

    // Ad Instances & Loading Locks
    private static InterstitialAd interstitialAd;
    private static boolean isInterstitialLoading = false;
    private static int interstitialRetryCount = 0;

    private static RewardedAd rewardedAd;
    private static boolean isRewardedLoading = false;
    private static int rewardedRetryCount = 0;

    private static RewardedInterstitialAd rewardedInterstitialAd;
    private static boolean isRewardedInterstitialLoading = false;
    private static int rewardedInterstitialRetryCount = 0;

    private static AppOpenAd appOpenAd;
    private static boolean isAppOpenLoading = false;
    private static long appOpenLoadTimeMs = 0L;
    private static int appOpenRetryCount = 0;

    // Policy Timers & Counters
    private static int interstitialCooldownSeconds = AdConfig.DEFAULT_INTERSTITIAL_COOLDOWN_SECONDS;
    private static long lastInterstitialShownMs = 0L;
    private static int interstitialClickCounter = 0;
    private static long lastAppOpenShownMs = 0L;
    private static boolean hasSkippedColdStartAppOpen = false;

    // App Open Activity Tracking
    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private static final Set<Class<? extends Activity>> excludedAppOpenActivities = new HashSet<>();
    private static boolean isAppLifecycleRegistered = false;

    // Optional Live Status Listener
    @Nullable
    private static AdStatusListener statusListener;

    private AdHelper() {
        // Static helper class
    }

    // =========================================================================
    // Callback Interfaces
    // =========================================================================

    public interface OnSimpleRewardListener {
        void onRewardEarned(int rewardAmount);
    }

    public interface OnRewardListener {
        void onRewardEarned(int rewardAmount, @NonNull String rewardType);

        void onAdNotReady();
    }

    public interface AdStatusListener {
        void onAdEvent(@NonNull String message);

        void onAdStateChanged();
    }

    // =========================================================================
    // 1. Application & SDK + UMP Consent Initialization (One-Line Calls)
    // =========================================================================

    /**
     * Call once inside {@link Application#onCreate()} to enable policy-safe automatic
     * App Open Ads when the user returns to the app from background.
     */
    public static void initApplication(@NonNull Application application) {
        appContext = application.getApplicationContext();
        if (isAppLifecycleRegistered) {
            return;
        }
        isAppLifecycleRegistered = true;

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (!isShowingFullScreenAd) {
                    currentActivityRef = new WeakReference<>(activity);
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (!isShowingFullScreenAd) {
                    currentActivityRef = new WeakReference<>(activity);
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                Activity tracked = currentActivityRef.get();
                if (tracked == activity) {
                    currentActivityRef.clear();
                }
            }
        });

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                // Policy Best Practice: Skip the very first cold start so users immediately see
                // app content; show App Open Ads only on genuine foreground returns.
                if (!hasSkippedColdStartAppOpen) {
                    hasSkippedColdStartAppOpen = true;
                    return;
                }
                Activity activity = currentActivityRef.get();
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    showAppOpenInternal(activity, false);
                }
            }
        });
    }

    private static final java.util.List<Runnable> pendingReadyCallbacks = new java.util.ArrayList<>();
    private static volatile boolean debugConsentFallbackGranted = false;

    /**
     * ONE-LINE INITIALIZATION: Runs Google UMP (GDPR/EEA/UK) consent check first,
     * initializes {@link MobileAds} only when consent permits, and preloads ads.
     */
    public static void init(@NonNull Activity activity, @Nullable Runnable onReady) {
        appContext = activity.getApplicationContext();
        if (adsRemoved) {
            notifyEvent("Premium mode active: AdMob initialization skipped.");
            runSafely(onReady);
            return;
        }

        if (isSdkInitialized && canRequestAds()) {
            runSafely(onReady);
            return;
        }

        if (onReady != null) {
            synchronized (pendingReadyCallbacks) {
                pendingReadyCallbacks.add(onReady);
            }
        }

        notifyEvent("Checking UMP GDPR consent…");
        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    if (formError != null) {
                        notifyEvent("UMP form info: " + formError.getMessage());
                    }
                    if (canRequestAds()) {
                        initializeMobileAdsSdk();
                    } else if (AdConfig.isTestMode() && formError != null) {
                        // Google's sample test App ID has no live UMP message; allow test ads in DEBUG.
                        debugConsentFallbackGranted = true;
                        notifyEvent("Debug test ID detected: enabling Google test ads.");
                        initializeMobileAdsSdk();
                    } else {
                        notifyEvent("Consent not granted; ads remain disabled.");
                        notifyStateChanged();
                        dispatchPendingReadyCallbacks();
                    }
                }),
                requestError -> {
                    notifyEvent("UMP update info: " + requestError.getMessage());
                    if (canRequestAds()) {
                        initializeMobileAdsSdk();
                    } else if (AdConfig.isTestMode()) {
                        // In DEBUG builds using Google's sample App ID, UMP may report unconfigured publisher.
                        debugConsentFallbackGranted = true;
                        notifyEvent("Debug test mode fallback: initializing test ads.");
                        initializeMobileAdsSdk();
                    } else {
                        notifyStateChanged();
                        dispatchPendingReadyCallbacks();
                    }
                }
        );

        // If user already consented in a previous session, start initializing immediately.
        if (canRequestAds()) {
            initializeMobileAdsSdk();
        }
    }

    private static void initializeMobileAdsSdk() {
        if (isSdkInitialized) {
            dispatchPendingReadyCallbacks();
            return;
        }
        if (isMobileAdsInitCalled.getAndSet(true)) {
            return;
        }
        if (appContext == null) {
            return;
        }

        notifyEvent("Initializing MobileAds SDK (" + (AdConfig.isTestMode() ? "TEST IDS" : "LIVE IDS") + ")…");
        MobileAds.initialize(appContext, initializationStatus -> {
            isSdkInitialized = true;
            notifyEvent("MobileAds SDK ready. Preloading ad formats…");
            preloadAllFullscreenAds();
            notifyStateChanged();
            dispatchPendingReadyCallbacks();
        });
    }

    private static void dispatchPendingReadyCallbacks() {
        java.util.List<Runnable> callbacksToRun;
        synchronized (pendingReadyCallbacks) {
            if (pendingReadyCallbacks.isEmpty()) {
                return;
            }
            callbacksToRun = new java.util.ArrayList<>(pendingReadyCallbacks);
            pendingReadyCallbacks.clear();
        }
        for (Runnable callback : callbacksToRun) {
            runSafely(callback);
        }
    }

    private static void preloadAllFullscreenAds() {
        loadInterstitial();
        loadRewarded();
        loadRewardedInterstitial();
        loadAppOpen();
    }

    // =========================================================================
    // 2. Adaptive Banner Ad (One-Line Call with Automatic Lifecycle Cleanup)
    // =========================================================================

    /**
     * ONE-LINE BANNER AD: Loads a Large Anchored Adaptive Banner into {@code container}
     * and automatically pauses, resumes, and destroys it with the Activity lifecycle.
     */
    public static void showBanner(@NonNull ComponentActivity activity, @NonNull ViewGroup container) {
        if (adsRemoved || !canRequestAds() || !isSdkInitialized) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);
        container.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed() || adsRemoved) {
                return;
            }
            int adWidthDp = computeBannerWidthDp(activity, container);
            AdSize adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, adWidthDp);

            AdView adView = new AdView(activity);
            adView.setAdUnitId(AdConfig.getBannerId());
            adView.setAdSize(adSize);
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    notifyEvent("Adaptive Banner loaded (" + adWidthDp + "dp).");
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    notifyEvent("Banner failed: " + loadAdError.getMessage());
                }
            });

            container.removeAllViews();
            container.addView(adView);
            adView.loadAd(buildAdRequest());

            // Automatic lifecycle cleanup prevents AdView memory leaks & WebView CPU drain.
            activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override
                public void onResume(@NonNull LifecycleOwner owner) {
                    adView.resume();
                }

                @Override
                public void onPause(@NonNull LifecycleOwner owner) {
                    adView.pause();
                }

                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    adView.destroy();
                    owner.getLifecycle().removeObserver(this);
                }
            });
        });
    }

    private static int computeBannerWidthDp(@NonNull Activity activity, @NonNull ViewGroup container) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int widthPx = container.getWidth();
        if (widthPx <= 0) {
            widthPx = metrics.widthPixels;
        }
        return Math.max(320, (int) (widthPx / metrics.density));
    }

    // =========================================================================
    // 3. Interstitial Ad (One-Line Call with Cooldown + Safe Navigation)
    // =========================================================================

    /**
     * ONE-LINE INTERSTITIAL AD: Shows a preloaded Interstitial only when cooldown has elapsed
     * and no other fullscreen ad is active. Always executes {@code onComplete} so user navigation
     * is never blocked.
     */
    public static void showInterstitial(@NonNull Activity activity, @Nullable Runnable onComplete) {
        if (adsRemoved || !canRequestAds() || isShowingFullScreenAd || isInvalidActivity(activity)) {
            runSafely(onComplete);
            return;
        }

        long remainingSec = getRemainingInterstitialCooldownSeconds();
        if (remainingSec > 0) {
            notifyEvent("Interstitial skipped by policy cooldown (" + remainingSec + "s left).");
            runSafely(onComplete);
            return;
        }

        if (interstitialAd == null) {
            notifyEvent("Interstitial not ready yet; continuing without blocking user.");
            loadInterstitial();
            runSafely(onComplete);
            return;
        }

        final InterstitialAd adToShow = interstitialAd;
        interstitialAd = null;
        isShowingFullScreenAd = true;
        notifyStateChanged();

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                lastInterstitialShownMs = System.currentTimeMillis();
                notifyEvent("Interstitial ad shown.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false;
                notifyEvent("Interstitial dismissed. Preloading next…");
                loadInterstitial();
                notifyStateChanged();
                runSafely(onComplete);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingFullScreenAd = false;
                notifyEvent("Interstitial failed to show: " + adError.getMessage());
                loadInterstitial();
                notifyStateChanged();
                runSafely(onComplete);
            }
        });

        adToShow.show(activity);
    }

    /**
     * ONE-LINE CLICK-CAPPED INTERSTITIAL: Shows an interstitial only once every {@code everyNClicks}
     * user actions AND respects the time cooldown.
     */
    public static void showInterstitialAfterClicks(
            @NonNull Activity activity,
            int everyNClicks,
            @Nullable Runnable onComplete
    ) {
        interstitialClickCounter++;
        int threshold = Math.max(1, everyNClicks);
        if (interstitialClickCounter >= threshold) {
            interstitialClickCounter = 0;
            showInterstitial(activity, onComplete);
        } else {
            notifyEvent("Interstitial click cap (" + interstitialClickCounter + "/" + threshold + ").");
            runSafely(onComplete);
        }
    }

    public static void loadInterstitial() {
        if (appContext == null || adsRemoved || !canRequestAds() || !isSdkInitialized
                || isInterstitialLoading || interstitialAd != null || !isNetworkAvailable()) {
            return;
        }

        isInterstitialLoading = true;
        notifyStateChanged();
        InterstitialAd.load(appContext, AdConfig.getInterstitialId(), buildAdRequest(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        isInterstitialLoading = false;
                        interstitialRetryCount = 0;
                        interstitialAd = ad;
                        notifyEvent("Interstitial preloaded & ready.");
                        notifyStateChanged();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        isInterstitialLoading = false;
                        interstitialAd = null;
                        notifyEvent("Interstitial load error: " + error.getMessage());
                        notifyStateChanged();
                        scheduleRetry(++interstitialRetryCount, AdHelper::loadInterstitial);
                    }
                });
    }

    // =========================================================================
    // 4. Rewarded Ad (One-Line Opt-In Call)
    // =========================================================================

    /** ONE-LINE REWARDED AD (simple lambda overload). */
    public static void showRewarded(@NonNull Activity activity, @NonNull OnSimpleRewardListener onReward) {
        showRewarded(activity, new OnRewardListener() {
            @Override
            public void onRewardEarned(int rewardAmount, @NonNull String rewardType) {
                onReward.onRewardEarned(rewardAmount);
            }

            @Override
            public void onAdNotReady() {
                notifyEvent("Rewarded ad not ready yet; preloading in background.");
            }
        });
    }

    /** Full callback overload for Rewarded Ad with explicit not-ready handling. */
    public static void showRewarded(@NonNull Activity activity, @NonNull OnRewardListener listener) {
        if (adsRemoved || !canRequestAds() || isShowingFullScreenAd || isInvalidActivity(activity)) {
            listener.onAdNotReady();
            return;
        }

        if (rewardedAd == null) {
            loadRewarded();
            listener.onAdNotReady();
            return;
        }

        final RewardedAd adToShow = rewardedAd;
        rewardedAd = null;
        isShowingFullScreenAd = true;
        notifyStateChanged();

        final int[] earnedAmount = {0};
        final String[] earnedType = {""};
        final boolean[] rewardGranted = {false};

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                notifyEvent("Rewarded ad shown.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false;
                notifyEvent("Rewarded ad closed. Preloading next…");
                loadRewarded();
                notifyStateChanged();
                if (rewardGranted[0]) {
                    listener.onRewardEarned(earnedAmount[0], earnedType[0]);
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingFullScreenAd = false;
                notifyEvent("Rewarded failed to show: " + adError.getMessage());
                loadRewarded();
                notifyStateChanged();
                listener.onAdNotReady();
            }
        });

        adToShow.show(activity, rewardItem -> {
            rewardGranted[0] = true;
            earnedAmount[0] = rewardItem.getAmount();
            earnedType[0] = rewardItem.getType();
            notifyEvent("User earned reward: " + earnedAmount[0] + " " + earnedType[0]);
        });
    }

    public static void loadRewarded() {
        if (appContext == null || adsRemoved || !canRequestAds() || !isSdkInitialized
                || isRewardedLoading || rewardedAd != null || !isNetworkAvailable()) {
            return;
        }

        isRewardedLoading = true;
        notifyStateChanged();
        RewardedAd.load(appContext, AdConfig.getRewardedId(), buildAdRequest(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        isRewardedLoading = false;
                        rewardedRetryCount = 0;
                        rewardedAd = ad;
                        notifyEvent("Rewarded ad preloaded & ready.");
                        notifyStateChanged();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        isRewardedLoading = false;
                        rewardedAd = null;
                        notifyEvent("Rewarded load error: " + error.getMessage());
                        notifyStateChanged();
                        scheduleRetry(++rewardedRetryCount, AdHelper::loadRewarded);
                    }
                });
    }

    // =========================================================================
    // 5. Rewarded Interstitial Ad (One-Line Call)
    // =========================================================================

    /** ONE-LINE REWARDED INTERSTITIAL AD. */
    public static void showRewardedInterstitial(
            @NonNull Activity activity,
            @NonNull OnSimpleRewardListener onReward
    ) {
        showRewardedInterstitial(activity, new OnRewardListener() {
            @Override
            public void onRewardEarned(int rewardAmount, @NonNull String rewardType) {
                onReward.onRewardEarned(rewardAmount);
            }

            @Override
            public void onAdNotReady() {
                notifyEvent("Rewarded Interstitial not ready yet; preloading…");
            }
        });
    }

    public static void showRewardedInterstitial(
            @NonNull Activity activity,
            @NonNull OnRewardListener listener
    ) {
        if (adsRemoved || !canRequestAds() || isShowingFullScreenAd || isInvalidActivity(activity)) {
            listener.onAdNotReady();
            return;
        }

        if (rewardedInterstitialAd == null) {
            loadRewardedInterstitial();
            listener.onAdNotReady();
            return;
        }

        final RewardedInterstitialAd adToShow = rewardedInterstitialAd;
        rewardedInterstitialAd = null;
        isShowingFullScreenAd = true;
        notifyStateChanged();

        final int[] earnedAmount = {0};
        final String[] earnedType = {""};
        final boolean[] rewardGranted = {false};

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                notifyEvent("Rewarded Interstitial shown.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false;
                notifyEvent("Rewarded Interstitial closed. Preloading next…");
                loadRewardedInterstitial();
                notifyStateChanged();
                if (rewardGranted[0]) {
                    listener.onRewardEarned(earnedAmount[0], earnedType[0]);
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingFullScreenAd = false;
                notifyEvent("Rewarded Interstitial show error: " + adError.getMessage());
                loadRewardedInterstitial();
                notifyStateChanged();
                listener.onAdNotReady();
            }
        });

        adToShow.show(activity, rewardItem -> {
            rewardGranted[0] = true;
            earnedAmount[0] = rewardItem.getAmount();
            earnedType[0] = rewardItem.getType();
            notifyEvent("Rewarded Interstitial earned: " + earnedAmount[0] + " " + earnedType[0]);
        });
    }

    public static void loadRewardedInterstitial() {
        if (appContext == null || adsRemoved || !canRequestAds() || !isSdkInitialized
                || isRewardedInterstitialLoading || rewardedInterstitialAd != null || !isNetworkAvailable()) {
            return;
        }

        isRewardedInterstitialLoading = true;
        notifyStateChanged();
        RewardedInterstitialAd.load(appContext, AdConfig.getRewardedInterstitialId(), buildAdRequest(),
                new RewardedInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedInterstitialAd ad) {
                        isRewardedInterstitialLoading = false;
                        rewardedInterstitialRetryCount = 0;
                        rewardedInterstitialAd = ad;
                        notifyEvent("Rewarded Interstitial preloaded & ready.");
                        notifyStateChanged();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        isRewardedInterstitialLoading = false;
                        rewardedInterstitialAd = null;
                        notifyEvent("Rewarded Interstitial load error: " + error.getMessage());
                        notifyStateChanged();
                        scheduleRetry(++rewardedInterstitialRetryCount, AdHelper::loadRewardedInterstitial);
                    }
                });
    }

    // =========================================================================
    // 6. Native Advanced Ad (One-Line Call with Policy-Safe Layout & Cleanup)
    // =========================================================================

    /**
     * ONE-LINE NATIVE AD: Loads a Native Advanced Ad, inflates {@code R.layout.layout_native_ad}
     * with a prominent "Ad" attribution badge and contrasting border, binds all required assets,
     * and automatically destroys the {@link NativeAd} when the Activity is destroyed.
     */
    public static void showNativeAd(@NonNull ComponentActivity activity, @NonNull ViewGroup container) {
        if (adsRemoved || !canRequestAds() || !isSdkInitialized) {
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);
        final NativeAd[] activeNativeAd = {null};

        AdLoader adLoader = new AdLoader.Builder(activity, AdConfig.getNativeId())
                .forNativeAd(nativeAd -> {
                    if (activity.isFinishing() || activity.isDestroyed() || adsRemoved) {
                        nativeAd.destroy();
                        return;
                    }
                    if (activeNativeAd[0] != null) {
                        activeNativeAd[0].destroy();
                    }
                    activeNativeAd[0] = nativeAd;

                    NativeAdView adView = (NativeAdView) LayoutInflater.from(activity)
                            .inflate(R.layout.layout_native_ad, container, false);
                    populateNativeAdView(nativeAd, adView);

                    container.removeAllViews();
                    container.addView(adView);
                    notifyEvent("Native Advanced Ad loaded & rendered.");
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        notifyEvent("Native Ad failed: " + loadAdError.getMessage());
                    }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build())
                .build();

        adLoader.loadAd(buildAdRequest());

        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                if (activeNativeAd[0] != null) {
                    activeNativeAd[0].destroy();
                    activeNativeAd[0] = null;
                }
                owner.getLifecycle().removeObserver(this);
            }
        });
    }

    private static void populateNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView adView) {
        TextView headlineView = adView.findViewById(R.id.ad_headline);
        TextView bodyView = adView.findViewById(R.id.ad_body);
        Button ctaView = adView.findViewById(R.id.ad_call_to_action);
        ImageView iconView = adView.findViewById(R.id.ad_app_icon);
        TextView priceView = adView.findViewById(R.id.ad_price);
        TextView storeView = adView.findViewById(R.id.ad_store);
        RatingBar starRatingView = adView.findViewById(R.id.ad_stars);
        TextView advertiserView = adView.findViewById(R.id.ad_advertiser);
        MediaView mediaView = adView.findViewById(R.id.ad_media);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setCallToActionView(ctaView);
        adView.setIconView(iconView);
        adView.setPriceView(priceView);
        adView.setStoreView(storeView);
        adView.setStarRatingView(starRatingView);
        adView.setAdvertiserView(advertiserView);
        adView.setMediaView(mediaView);

        headlineView.setText(nativeAd.getHeadline());
        if (nativeAd.getMediaContent() != null) {
            mediaView.setMediaContent(nativeAd.getMediaContent());
            mediaView.setVisibility(View.VISIBLE);
        } else {
            mediaView.setVisibility(View.GONE);
        }

        if (nativeAd.getBody() == null) {
            bodyView.setVisibility(View.GONE);
        } else {
            bodyView.setVisibility(View.VISIBLE);
            bodyView.setText(nativeAd.getBody());
        }

        if (nativeAd.getCallToAction() == null) {
            ctaView.setVisibility(View.INVISIBLE);
        } else {
            ctaView.setVisibility(View.VISIBLE);
            ctaView.setText(nativeAd.getCallToAction());
        }

        if (nativeAd.getIcon() == null) {
            iconView.setVisibility(View.GONE);
        } else {
            iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
            iconView.setVisibility(View.VISIBLE);
        }

        if (nativeAd.getPrice() == null) {
            priceView.setVisibility(View.GONE);
        } else {
            priceView.setVisibility(View.VISIBLE);
            priceView.setText(nativeAd.getPrice());
        }

        if (nativeAd.getStore() == null) {
            storeView.setVisibility(View.GONE);
        } else {
            storeView.setVisibility(View.VISIBLE);
            storeView.setText(nativeAd.getStore());
        }

        if (nativeAd.getStarRating() == null) {
            starRatingView.setVisibility(View.GONE);
        } else {
            starRatingView.setRating(nativeAd.getStarRating().floatValue());
            starRatingView.setVisibility(View.VISIBLE);
        }

        if (nativeAd.getAdvertiser() == null) {
            advertiserView.setVisibility(View.INVISIBLE);
        } else {
            advertiserView.setText(nativeAd.getAdvertiser());
            advertiserView.setVisibility(View.VISIBLE);
        }

        adView.setNativeAd(nativeAd);
    }

    // =========================================================================
    // 7. App Open Ad (4-Hour Expiry Check + Foreground Resume Automation)
    // =========================================================================

    /** ONE-LINE APP OPEN AD (manual trigger; also runs automatically on foreground resume). */
    public static void showAppOpen(@NonNull Activity activity) {
        showAppOpenInternal(activity, true);
    }

    private static void showAppOpenInternal(@NonNull Activity activity, boolean isManualTrigger) {
        if (adsRemoved || !canRequestAds() || isShowingFullScreenAd || isInvalidActivity(activity)) {
            return;
        }
        if (excludedAppOpenActivities.contains(activity.getClass())) {
            return;
        }
        if (!isManualTrigger) {
            long elapsedSec = (System.currentTimeMillis() - lastAppOpenShownMs) / 1000L;
            if (lastAppOpenShownMs > 0 && elapsedSec < AdConfig.DEFAULT_APP_OPEN_COOLDOWN_SECONDS) {
                notifyEvent("App Open skipped by cooldown.");
                return;
            }
        }

        if (!isAppOpenAvailable()) {
            notifyEvent("App Open not ready or expired (>4h); preloading…");
            loadAppOpen();
            return;
        }

        final AppOpenAd adToShow = appOpenAd;
        appOpenAd = null;
        isShowingFullScreenAd = true;
        notifyStateChanged();

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                lastAppOpenShownMs = System.currentTimeMillis();
                notifyEvent("App Open ad shown.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false;
                notifyEvent("App Open dismissed. Preloading next…");
                loadAppOpen();
                notifyStateChanged();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingFullScreenAd = false;
                notifyEvent("App Open show error: " + adError.getMessage());
                loadAppOpen();
                notifyStateChanged();
            }
        });

        adToShow.show(activity);
    }

    public static void loadAppOpen() {
        if (appContext == null || adsRemoved || !canRequestAds() || !isSdkInitialized
                || isAppOpenLoading || isAppOpenAvailable() || !isNetworkAvailable()) {
            return;
        }

        isAppOpenLoading = true;
        notifyStateChanged();
        // Play Services Ads v25+ orientation-agnostic AppOpenAd.load()
        AppOpenAd.load(appContext, AdConfig.getAppOpenId(), buildAdRequest(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        isAppOpenLoading = false;
                        appOpenRetryCount = 0;
                        appOpenAd = ad;
                        appOpenLoadTimeMs = System.currentTimeMillis();
                        notifyEvent("App Open ad preloaded (valid for 4h).");
                        notifyStateChanged();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        isAppOpenLoading = false;
                        appOpenAd = null;
                        notifyEvent("App Open load error: " + error.getMessage());
                        notifyStateChanged();
                        scheduleRetry(++appOpenRetryCount, AdHelper::loadAppOpen);
                    }
                });
    }

    private static boolean isAppOpenAvailable() {
        if (appOpenAd == null) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - appOpenLoadTimeMs;
        if (ageMs >= AdConfig.APP_OPEN_MAX_CACHE_AGE_MS) {
            appOpenAd = null;
            return false;
        }
        return true;
    }

    // =========================================================================
    // 8. GDPR Privacy Options & Policy Controls (One-Line Utilities)
    // =========================================================================

    /** ONE-LINE GDPR PRIVACY OPTIONS: Required in settings for EEA/UK users. */
    public static void showPrivacyOptions(@NonNull Activity activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (formError != null) {
                notifyEvent("Privacy options: " + formError.getMessage());
            } else {
                notifyEvent("Privacy preferences updated.");
            }
            notifyStateChanged();
        });
    }

    public static boolean isPrivacyOptionsRequired() {
        return consentInformation != null
                && consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    /** ONE-LINE PREMIUM KILL SWITCH: Immediately disables and clears all cached ads. */
    public static void setAdsRemoved(boolean removed) {
        adsRemoved = removed;
        if (removed) {
            interstitialAd = null;
            rewardedAd = null;
            rewardedInterstitialAd = null;
            appOpenAd = null;
            notifyEvent("All ads disabled (Premium Mode).");
        } else {
            notifyEvent("Ads re-enabled. Preloading…");
            if (isSdkInitialized && canRequestAds()) {
                preloadAllFullscreenAds();
            }
        }
        notifyStateChanged();
    }

    public static void setInterstitialCooldownSeconds(int seconds) {
        interstitialCooldownSeconds = Math.max(AdConfig.MIN_SAFE_INTERSTITIAL_COOLDOWN_SECONDS, seconds);
    }

    public static void addExcludedAppOpenActivity(@NonNull Class<? extends Activity> activityClass) {
        excludedAppOpenActivities.add(activityClass);
    }

    public static void setStatusListener(@Nullable AdStatusListener listener) {
        statusListener = listener;
    }

    // =========================================================================
    // Status Getters
    // =========================================================================

    public static boolean isAdsRemoved() {
        return adsRemoved;
    }

    public static boolean isSdkInitialized() {
        return isSdkInitialized;
    }

    public static boolean canRequestAds() {
        if (adsRemoved) {
            return false;
        }
        if (consentInformation != null && consentInformation.canRequestAds()) {
            return true;
        }
        return AdConfig.isTestMode() && debugConsentFallbackGranted;
    }

    public static boolean isInterstitialReady() {
        return !adsRemoved && interstitialAd != null;
    }

    public static boolean isRewardedReady() {
        return !adsRemoved && rewardedAd != null;
    }

    public static boolean isRewardedInterstitialReady() {
        return !adsRemoved && rewardedInterstitialAd != null;
    }

    public static boolean isAppOpenReady() {
        return !adsRemoved && isAppOpenAvailable();
    }

    public static long getRemainingInterstitialCooldownSeconds() {
        if (lastInterstitialShownMs == 0L) {
            return 0L;
        }
        long elapsedSeconds = (System.currentTimeMillis() - lastInterstitialShownMs) / 1000L;
        return Math.max(0L, interstitialCooldownSeconds - elapsedSeconds);
    }

    // =========================================================================
    // Internal Helpers
    // =========================================================================

    private static AdRequest buildAdRequest() {
        return new AdRequest.Builder().build();
    }

    private static boolean isInvalidActivity(@Nullable Activity activity) {
        return activity == null || activity.isFinishing() || activity.isDestroyed();
    }

    private static boolean isNetworkAvailable() {
        if (appContext == null) {
            return false;
        }
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static void scheduleRetry(int attemptNumber, @NonNull Runnable retryAction) {
        if (attemptNumber > AdConfig.MAX_RETRY_ATTEMPTS || adsRemoved) {
            return;
        }
        // Exponential backoff: 4s, 8s, 16s to avoid hammering AdMob servers on no-fill.
        long delayMs = (long) Math.pow(2, attemptNumber + 1) * 1000L;
        MAIN_HANDLER.postDelayed(retryAction, delayMs);
    }

    private static void runSafely(@Nullable Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }

    private static void notifyEvent(@NonNull String message) {
        Log.d(TAG, message);
        AdStatusListener listener = statusListener;
        if (listener != null) {
            MAIN_HANDLER.post(() -> listener.onAdEvent(message));
        }
    }

    private static void notifyStateChanged() {
        AdStatusListener listener = statusListener;
        if (listener != null) {
            MAIN_HANDLER.post(listener::onAdStateChanged);
        }
    }
}
