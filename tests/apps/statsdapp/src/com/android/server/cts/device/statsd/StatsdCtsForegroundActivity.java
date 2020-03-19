/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.cts.device.statsd;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/** An activity (to be run as a foreground process) which performs one of a number of actions. */
public class StatsdCtsForegroundActivity extends Activity {
    private static final String TAG = StatsdCtsForegroundActivity.class.getSimpleName();

    public static final String KEY_ACTION = "action";
    public static final String ACTION_END_IMMEDIATELY = "action.end_immediately";
    public static final String ACTION_SLEEP_WHILE_TOP = "action.sleep_top";
    public static final String ACTION_LONG_SLEEP_WHILE_TOP = "action.long_sleep_top";
    public static final String ACTION_SHOW_APPLICATION_OVERLAY = "action.show_application_overlay";
    public static final String ACTION_SHOW_NOTIFICATION = "action.show_notification";
    public static final String ACTION_CRASH = "action.crash";
    public static final String ACTION_CREATE_CHANNEL_GROUP = "action.create_channel_group";
    public static final String ACTION_GENERATE_MOBILE_TRAFFIC = "action.generate_mobile_traffic";

    public static final int SLEEP_OF_ACTION_SLEEP_WHILE_TOP = 2_000;
    public static final int SLEEP_OF_ACTION_SHOW_APPLICATION_OVERLAY = 2_000;
    public static final int LONG_SLEEP_WHILE_TOP = 60_000;
    private static final int NETWORK_TIMEOUT_MILLIS = 15000;
    private static final String HTTPS_HOST_URL =
            "https://connectivitycheck.gstatic.com/generate_204";
    // Minimum and Maximum of iterations of exercise host, @see #doGenerateNetworkTraffic.
    private static final int MIN_EXERCISE_HOST_ITERATIONS = 1;
    private static final int MAX_EXERCISE_HOST_ITERATIONS = 19;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        Intent intent = this.getIntent();
        if (intent == null) {
            Log.e(TAG, "Intent was null.");
            finish();
        }

        String action = intent.getStringExtra(KEY_ACTION);
        Log.i(TAG, "Starting " + action + " from foreground activity.");

        switch (action) {
            case ACTION_END_IMMEDIATELY:
                finish();
                break;
            case ACTION_SLEEP_WHILE_TOP:
                doSleepWhileTop(SLEEP_OF_ACTION_SLEEP_WHILE_TOP);
                break;
            case ACTION_LONG_SLEEP_WHILE_TOP:
                doSleepWhileTop(LONG_SLEEP_WHILE_TOP);
                break;
            case ACTION_SHOW_APPLICATION_OVERLAY:
                doShowApplicationOverlay();
                break;
            case ACTION_SHOW_NOTIFICATION:
                doShowNotification();
                break;
            case ACTION_CRASH:
                doCrash();
                break;
            case ACTION_CREATE_CHANNEL_GROUP:
                doCreateChannelGroup();
                break;
            case ACTION_GENERATE_MOBILE_TRAFFIC:
                doGenerateNetworkTraffic(NetworkCapabilities.TRANSPORT_CELLULAR);
                break;
            default:
                Log.e(TAG, "Intent had invalid action " + action);
                finish();
        }
    }

    /** Does nothing, but asynchronously. */
    private void doSleepWhileTop(int sleepTime) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                AtomTests.sleep(sleepTime);
                return null;
            }

            @Override
            protected void onPostExecute(Void nothing) {
                finish();
            }
        }.execute();
    }

    private void doShowApplicationOverlay() {
        // Adapted from BatteryStatsBgVsFgActions.java.
        final WindowManager wm = getSystemService(WindowManager.class);
        Point size = new Point();
        wm.getDefaultDisplay().getSize(size);

        WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        wmlp.width = size.x / 4;
        wmlp.height = size.y / 4;
        wmlp.gravity = Gravity.CENTER | Gravity.LEFT;
        wmlp.setTitle(getPackageName());

        ViewGroup.LayoutParams vglp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        View v = new View(this);
        v.setBackgroundColor(Color.GREEN);
        v.setLayoutParams(vglp);
        wm.addView(v, wmlp);

        // The overlay continues long after the finish. The following is just to end the activity.
        AtomTests.sleep(SLEEP_OF_ACTION_SHOW_APPLICATION_OVERLAY);
        finish();
    }

    private void doShowNotification() {
        final int notificationId = R.layout.activity_main;
        final String notificationChannelId = "StatsdCtsChannel";

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(notificationChannelId, "Statsd Cts",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Statsd Cts Channel");
        nm.createNotificationChannel(channel);

        nm.notify(
                notificationId,
                new Notification.Builder(this, notificationChannelId)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("StatsdCts")
                        .setContentText("StatsdCts")
                        .build());
        nm.cancel(notificationId);
        finish();
    }

    private void doCreateChannelGroup() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannelGroup channelGroup = new NotificationChannelGroup("StatsdCtsGroup",
                "Statsd Cts Group");
        channelGroup.setDescription("StatsdCtsGroup Description");
        nm.createNotificationChannelGroup(channelGroup);
        finish();
    }

    private void doGenerateNetworkTraffic(@NetworkCapabilities.Transport int transport) {
        final ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        final NetworkRequest request = new NetworkRequest.Builder().addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(transport).build();
        final ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                final long startTime = SystemClock.elapsedRealtime();
                try {
                    // Since history of network stats only have 2 hours of resolution, when it is
                    // being queried, service will assume that history network stats has uniform
                    // distribution and return a fraction of network stats that is originally
                    // subject to 2 hours. To be specific:
                    //    <returned network stats> = <total network stats> * <duration> / 2 hour,
                    // assuming the duration can fit in a 2 hours bucket.
                    // In the other hand, in statsd, the network stats is queried since boot,
                    // that means in order to assert non-zero packet counts, either the test should
                    // be run after enough time since boot, or the packet counts generated here
                    // should be enough. That is to say:
                    //   <total packet counts> * <up time> / 2 hour >= 1,
                    // or
                    //   iterations >= 2 hour / (<up time> * <packets per iteration>)
                    // Thus, iterations can be chosen based on the factors above to make this
                    // function generate enough packets in each direction to accommodate enough
                    // packet counts for a fraction of history bucket.
                    final double iterations = (TimeUnit.HOURS.toMillis(2) / startTime / 7);
                    // While just enough iterations are going to make the test flaky, add a 20%
                    // buffer to stabilize it and make sure it's in a reasonable range, so it won't
                    // consumes more than 100kb of traffic, or generates 0 byte of traffic.
                    final int augmentedIterations =
                            (int) Math.max(iterations * 1.2, MIN_EXERCISE_HOST_ITERATIONS);
                    if (augmentedIterations > MAX_EXERCISE_HOST_ITERATIONS) {
                        throw new IllegalStateException("Exceeded max allowed iterations"
                                + ", iterations=" + augmentedIterations
                                + ", uptime=" + TimeUnit.MILLISECONDS.toSeconds(startTime) + "s");
                    }

                    for (int i = 0; i < augmentedIterations; i++) {
                        // By observing results of "dumpsys netstats --uid", typically the single
                        // run of the https request below generates 4200/1080 rx/tx bytes with
                        // around 7/9 rx/tx packets.
                        // This blocks the thread of NetworkCallback, thus no other event
                        // can be processed before return.
                        exerciseRemoteHost(cm, network, new URL(HTTPS_HOST_URL));
                    }
                    Log.i(TAG, "exerciseRemoteHost successful in " + (SystemClock.elapsedRealtime()
                            - startTime) + " ms with iterations=" + augmentedIterations
                            + ", uptime=" + TimeUnit.MILLISECONDS.toSeconds(startTime) + "s");
                } catch (Exception e) {
                    Log.e(TAG, "exerciseRemoteHost failed in " + (SystemClock.elapsedRealtime()
                            - startTime) + " ms: " + e);
                } finally {
                    cm.unregisterNetworkCallback(this);
                    finish();
                }
            }
        };

        // Request network, and make http query when the network is available.
        cm.requestNetwork(request, cb);
    }

    /**
     * Generate traffic on specified network.
     */
    private void exerciseRemoteHost(@NonNull ConnectivityManager cm, @NonNull Network network,
            @NonNull URL url) throws Exception {
        cm.bindProcessToNetwork(network);
        HttpURLConnection urlc = null;
        try {
            urlc = (HttpURLConnection) network.openConnection(url);
            urlc.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
            urlc.setUseCaches(false);
            urlc.connect();
        } finally {
            if (urlc != null) {
                urlc.disconnect();
            }
        }
    }

    @SuppressWarnings("ConstantOverflow")
    private void doCrash() {
        Log.e(TAG, "About to crash the app with 1/0 " + (long) 1 / 0);
    }
}
