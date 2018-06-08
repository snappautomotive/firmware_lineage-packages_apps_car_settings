/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.settings.common;

import android.app.Activity;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Class that helps registering {@link CarUxRestrictionsManager.OnUxRestrictionsChangedListener} and
 * managing car connection.
 */
public class CarUxRestrictionsHelper {
    private static final Logger LOG = new Logger(CarUxRestrictionsHelper.class);

    // mCar is created in the constructor, but can be null if connection to the car is not
    // successful.
    @Nullable private final Car mCar;
    @Nullable private CarUxRestrictionsManager mCarUxRestrictionsManager;

    private final CarUxRestrictionsManager.OnUxRestrictionsChangedListener mListener;

    public CarUxRestrictionsHelper(Context context,
            CarUxRestrictionsManager.OnUxRestrictionsChangedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }
        mListener = listener;
        mCar = Car.createCar(context, mServiceConnection);
    };

    /**
     * Starts monitoring any changes in {@link CarUxRestrictions}.
     *
     * <p>This method can be called from {@code Activity}'s {@link Activity#onStart()}, or at the
     * time of construction.
     *
     * <p>This method must be accompanied with a matching {@link #stop()} to avoid leak.
     */
    public void start() {
        try {
            if (mCar != null && !mCar.isConnected()) {
                mCar.connect();
            }
        } catch (IllegalStateException e) {
            // Do nothing.
            LOG.w("start(); cannot connect to Car");
        }
    }

    /**
     * Stops monitoring any changes in {@link CarUxRestrictions}.
     *
     * <p>This method should be called from {@code Activity}'s {@link Activity#onStop()}, or at the
     * time of this adapter being discarded.
     */
    public void stop() {
        try {
            if (mCar != null && mCar.isConnected()) {
                mCar.disconnect();
            }
        } catch (IllegalStateException e) {
            // Do nothing.
            LOG.w("stop(); cannot disconnect from Car");
        }
    }

    /**
     * Checks if UX_RESTRICTIONS_NO_SETUP is set or not.
     */
    public static boolean isNoSetup(CarUxRestrictions carUxRestrictions) {
        return (carUxRestrictions.getActiveRestrictions()
                & CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP)
                == CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mCarUxRestrictionsManager = (CarUxRestrictionsManager)
                        mCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                mCarUxRestrictionsManager.registerListener(mListener);

                mListener.onUxRestrictionsChanged(
                        mCarUxRestrictionsManager.getCurrentCarUxRestrictions());
            } catch (CarNotConnectedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            try {
                mCarUxRestrictionsManager.unregisterListener();
                mCarUxRestrictionsManager = null;
            } catch (CarNotConnectedException e) {
                e.printStackTrace();
            }
        }
    };
}
