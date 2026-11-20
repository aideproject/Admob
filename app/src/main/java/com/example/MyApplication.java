package com.example;

import android.app.Application;
import com.example.ads.AdHelper;

/**
 * Custom Application class that wires up automatic policy-safe App Open Ads
 * on foreground resume in a single line.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 1-line setup for automatic App Open Ad lifecycle handling
        AdHelper.initApplication(this);
    }
}
