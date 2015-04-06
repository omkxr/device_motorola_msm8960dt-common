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
import android.hardware.SensorEvent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class FlatSensor {

    private static final boolean DEBUG = false;
    private static final String TAG = "MotoFlatSensor";

    public static final int SENSOR_TYPE_COMPOSITE_FLAT = 1000;

    private Context mContext;
    private MotoSensor mFlatUpSensor;
    private MotoSensor mFlatDownSensor;
    private List<FlatSensorListener> mListeners;
    private boolean mIsFlat = false;

    private MotoSensor.MotoSensorListener mSensorListener = new MotoSensor.MotoSensorListener() {
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
            }
        }
     };

    public interface FlatSensorListener {
        public void onEvent(boolean isFlat);
    }

    public FlatSensor(Context context) {
        mContext = context;
        mListeners = new ArrayList<FlatSensorListener>();
        mFlatUpSensor = new MotoSensor(mContext, MotoSensor.SENSOR_TYPE_MMI_FLAT_UP);
        mFlatUpSensor.registerListener(mSensorListener);
        mFlatDownSensor = new MotoSensor(mContext, MotoSensor.SENSOR_TYPE_MMI_FLAT_DOWN);
        mFlatDownSensor.registerListener(mSensorListener);
    }

    public void enable() {
        if (DEBUG) Log.d(TAG, "Enabling");
        mFlatUpSensor.enable();
        mFlatDownSensor.disable();
    }

    public void disable() {
        if (DEBUG) Log.d(TAG, "Disabling");
        mFlatUpSensor.disable();
        mFlatDownSensor.disable();
    }

    public void registerListener(FlatSensorListener listener) {
        mListeners.add(listener);
    }

    private void broadcastEvent() {
        if (DEBUG) Log.d(TAG, "Broadcasting flat state: " + mIsFlat);
        for (FlatSensorListener l : mListeners) {
            l.onEvent(mIsFlat);
        }
    }

    private void handleFlatUp(SensorEvent event) {
        // flat up sensor is 0.0 for vertical 1.0 for flat
        boolean isFlat = (event.values[0] == 1.0);

        if (mIsFlat != isFlat) {
            mIsFlat = isFlat;
            broadcastEvent();
        }
    }

    private void handleFlatDown(SensorEvent event) {
        // flat down sensor is 0.0 for vertical
        // flat down will only ever register 0.0
        if (mIsFlat) {
            mIsFlat = false;
            broadcastEvent();
        }
    }
}
