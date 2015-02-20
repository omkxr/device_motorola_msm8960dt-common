/*
 * Copyright (c) 2015 The CyanogenMod Project
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

package org.cyanogenmod.doze.motorola;

import android.app.Activity;
import android.app.IntentService;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UserHandle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

public class MotoDozeService extends Service {

    private static final boolean DEBUG = false;
    private static final String TAG = "MotoDozeService";

    private static final String DOZE_INTENT = "com.android.systemui.doze.pulse";

    private static final int SENSOR_WAKELOCK_DURATION = 200;
    private static final int MIN_PULSE_INTERVAL_MS = 5000;

    private Context mContext;
    private MotoSensor mFlatUpSensor;
    private MotoSensor mFlatDownSensor;
    private MotoSensor mStowSensor;
    private MotoSensor mCameraActivationSensor;
    private WakeLock mSensorWakeLock;
    private long mEntryTimestamp;

    private MotoSensor.MotoSensorListener mListener = new MotoSensor.MotoSensorListener() {
        @Override
        public void onEvent(int sensorType, SensorEvent event) {
            if (DEBUG) Log.d(TAG, "Got sensor event: " + event.values[0] + " for type " + sensorType);

            switch (sensorType) {
                case MotoSensor.SENSOR_TYPE_MMI_FLAT_UP:
                    handleFlatUp(event);
                    break;
                case MotoSensor.SENSOR_TYPE_MMI_FLAT_DOWN:
                    handleFlatDown(event);
                    break;
                case MotoSensor.SENSOR_TYPE_MMI_STOW:
                    handleStow(event);
                    break;
                case MotoSensor.SENSOR_TYPE_MMI_CAMERA_ACTIVATION:
                    handleCameraActivation(event);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");
        super.onCreate();
        mContext = this;
        mFlatUpSensor = new MotoSensor(mContext, MotoSensor.SENSOR_TYPE_MMI_FLAT_UP);
        mFlatUpSensor.registerListener(mListener);
        mFlatDownSensor = new MotoSensor(mContext, MotoSensor.SENSOR_TYPE_MMI_FLAT_DOWN);
        mFlatDownSensor.registerListener(mListener);
        mStowSensor = new MotoSensor(mContext, MotoSensor.SENSOR_TYPE_MMI_STOW);
        mStowSensor.registerListener(mListener);
        mCameraActivationSensor = new MotoSensor(mContext, MotoSensor.SENSOR_TYPE_MMI_CAMERA_ACTIVATION);
        mCameraActivationSensor.registerListener(mListener);
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mSensorWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotoSensorWakeLock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenStateReceiver, screenStateFilter);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        super.onDestroy();
        mFlatDownSensor.disable();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void launchDozePulse() {
        mContext.sendBroadcast(new Intent(DOZE_INTENT));
    }

    private void launchCamera() {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mSensorWakeLock.acquire(SENSOR_WAKELOCK_DURATION);
        powerManager.wakeUp(SystemClock.uptimeMillis());
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            mContext.startActivityAsUser(intent, null, new UserHandle(UserHandle.USER_CURRENT));
        } catch (ActivityNotFoundException e) {
            /* Ignore */
        }
    }

    private boolean isDozeEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
            Settings.Secure.DOZE_ENABLED, 1) != 0;
    }

    private void handleFlatUp(SensorEvent event) {
        long delta = SystemClock.elapsedRealtime() - mEntryTimestamp;
        if (delta < MIN_PULSE_INTERVAL_MS) {
            return;
        } else {
            mEntryTimestamp = SystemClock.elapsedRealtime();
        }

        /* FlatUp is 0 when vertical */
        /* FlatUp is 1 when horizontal */
        /* MSP430 will not report event if proximity sensor is covered */
        if (event.values[0] == 0) {
            launchDozePulse();
        }
    }

    private void handleFlatDown(SensorEvent event) {
        long delta = SystemClock.elapsedRealtime() - mEntryTimestamp;
        if (delta < MIN_PULSE_INTERVAL_MS) {
            return;
        } else {
            mEntryTimestamp = SystemClock.elapsedRealtime();
        }

        /* FlatDown is 0 when vertical */
        /* MSP430 will not report event if proximity sensor is covered */
        if (event.values[0] == 0) {
            launchDozePulse();
        }
    }

    private void handleStow(SensorEvent event) {
        launchDozePulse();
    }

    private void handleCameraActivation(SensorEvent event) {
        launchCamera();
    }

    private void onDisplayOn() {
        if (DEBUG) Log.d(TAG, "Display on");
        mFlatDownSensor.disable();
        mCameraActivationSensor.disable();
    }

    private void onDisplayOff() {
        if (DEBUG) Log.d(TAG, "Display off");
        if (isDozeEnabled()) {
            mEntryTimestamp = SystemClock.elapsedRealtime();
            mFlatDownSensor.enable();
            mCameraActivationSensor.enable();
        }
    }

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                onDisplayOff();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                onDisplayOn();
            }
        }
    };
}
