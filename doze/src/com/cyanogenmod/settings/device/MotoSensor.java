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

package com.cyanogenmod.settings.device;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MotoSensor {

    private static final boolean DEBUG = false;
    private static final String TAG = "MotoSensor";

    public static final int SENSOR_TYPE_MMI_FLAT_UP = 34;
    public static final int SENSOR_TYPE_MMI_FLAT_DOWN = 35;
    public static final int SENSOR_TYPE_MMI_STOW = 36;
    public static final int SENSOR_TYPE_MMI_CAMERA_ACTIVATION = 37;

    protected static final int BATCH_LATENCY_IN_MS = 100;

    protected Context mContext;
    protected SensorManager mSensorManager;
    protected Sensor mSensor;
    protected int mType;
    private List<MotoSensorListener> mListeners;

    static {
        System.load("/system/lib/libjni_motoSensor.so");
    }

    protected static native void native_enableWakeSensor(int wakeSensor);
    protected static native void native_disableWakeSensor(int wakeSensor);

    public interface MotoSensorListener {
        public void onEvent(int sensorType, SensorEvent event);
    }

    public MotoSensor(Context context, int type) {
        mContext = context;
        mType = type;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(mType);
        mListeners = new ArrayList<MotoSensorListener>();
    }

    public void enable() {
        if (DEBUG) Log.d(TAG, "Enabling");
        mSensorManager.registerListener(mSensorEventListener, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_IN_MS * 1000);
        native_enableWakeSensor(mType);
    }

    public void disable() {
        if (DEBUG) Log.d(TAG, "Disabling");
        mSensorManager.unregisterListener(mSensorEventListener);
        native_disableWakeSensor(mType);
    }

    public void registerListener(MotoSensorListener listener) {
        mListeners.add(listener);
    }

    private void onSensorEvent(SensorEvent event) {
        for (MotoSensorListener l : mListeners) {
            l.onEvent(mType, event);
        }
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            onSensorEvent(event);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            /* Empty */
        }
    };
}
