package org.edx.mobile.base;


import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap.CompressFormat;
import android.os.Bundle;

import com.crashlytics.android.Crashlytics;
import com.newrelic.agent.android.NewRelic;

import org.edx.mobile.logger.Logger;
import org.edx.mobile.module.analytics.SegmentFactory;
import org.edx.mobile.module.prefs.PrefManager;
import org.edx.mobile.module.storage.Storage;
import org.edx.mobile.receivers.NetworkConnectivityReceiver;
import org.edx.mobile.util.AppConstants;
import org.edx.mobile.util.Config;
import org.edx.mobile.util.Environment;
import org.edx.mobile.util.NetworkUtil;
import org.edx.mobile.util.images.ImageCacheManager;
import org.edx.mobile.util.images.RequestManager;
import org.edx.mobile.view.Router;

import java.util.ArrayList;
import java.util.List;

import io.fabric.sdk.android.Fabric;

/**
 * This class initializes the modules of the app based on the configuration.
 */
public class MainApplication extends Application{

    protected final Logger logger = new Logger(getClass().getName());

    NetworkConnectivityReceiver connectivityReceiver;

    private static MainApplication application;

    public static final MainApplication instance(){
        return application;
    }



    @Override
    public void onCreate() {
        super.onCreate();
        init();
    }

    /**
     * Initializes the request manager, image cache,
     * all third party integrations and shared components.
     */
    private void init() {
        // initialize logger
        Logger.init(this.getApplicationContext());

        application = this;
        registerActivityLifecycleCallbacks(new MyActivityLifecycleCallbacks());

        // setup environment
        Environment env = new Environment();
        env.setupEnvironment(this.getApplicationContext());

        // setup image cache
        createImageCache();

        // initialize SegmentIO
        if (Config.getInstance().getSegmentConfig().isEnabled()) {
            SegmentFactory.makeInstance(this);
        }

        // initialize Fabric
        if (Config.getInstance().getFabricConfig().isEnabled()) {
            Fabric.with(this, new Crashlytics());
        }

        // initialize NewRelic with crash reporting disabled
        if (Config.getInstance().getNewRelicConfig().isEnabled()) {
            //Crash reporting for new relic has been disabled
            NewRelic.withApplicationToken(Config.getInstance().getNewRelicConfig().getNewRelicKey())
                    .withCrashReportingEnabled(false)
                    .start(this);
        }

        // initialize Facebook SDK
        boolean isOnZeroRatedNetwork = NetworkUtil.isOnZeroRatedNetwork(getApplicationContext());
        if ( !isOnZeroRatedNetwork
                && Config.getInstance().getFacebookConfig().isEnabled()) {
            com.facebook.Settings.setApplicationId(Config.getInstance().getFacebookConfig().getFacebookAppId());
        }

        // try repair of download data if app version is updated
        new Storage(this).repairDownloadCompletionData();

        // register connectivity receiver
        connectivityReceiver = new NetworkConnectivityReceiver();
        registerReceiver(connectivityReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));

    }

    
    /**
     * Create the image cache. Uses Memory Cache by default. 
     * Change to Disk for a Disk based LRU implementation.
     */
    private void createImageCache(){
        int DISK_IMAGECACHE_SIZE = 1024*1024*10;
        CompressFormat DISK_IMAGECACHE_COMPRESS_FORMAT = CompressFormat.PNG;
        //PNG is lossless so quality is ignored but must be provided
        int DISK_IMAGECACHE_QUALITY = 100;

        RequestManager.init(this);
        ImageCacheManager.getInstance().init(this,
                this.getPackageCodePath()
                , DISK_IMAGECACHE_SIZE
                , DISK_IMAGECACHE_COMPRESS_FORMAT
                , DISK_IMAGECACHE_QUALITY
                , ImageCacheManager.CacheType.MEMORY);
    }


    /**
     * callback when application is launched from background or from a cold launch,
     */
    public void onApplicationLaunchedFromBackground(){
        logger.debug("onApplicationLaunchedFromBackground");
        PrefManager pref = new PrefManager(this, PrefManager.Pref.LOGIN);
        if ( pref.hasAuthTokenSocialCookie() ){
             Router.getInstance().forceLogout(this);
        }
    }


    private final class MyActivityLifecycleCallbacks
            implements Application.ActivityLifecycleCallbacks{

        Activity  prevPausedOne;

        public void onActivityCreated(Activity activity, Bundle bundle) {

        }

        public void onActivityDestroyed(Activity activity) {

        }

        public void onActivityPaused(Activity activity) {
            prevPausedOne = activity;
        }

        public void onActivityResumed(Activity activity) {
             if( null ==  prevPausedOne || prevPausedOne == activity ){
                 //application launched from background,
                 onApplicationLaunchedFromBackground();
             }
        }

        public void onActivitySaveInstanceState(Activity activity,
                                                Bundle outState) {
        }

        public void onActivityStarted(Activity activity) {
        }

        public void onActivityStopped(Activity activity) {
        }
    }
}