/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.adm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;

import java.io.IOException;

/**
 * Adm push provider.
 *
 * @hide
 */
public class AdmPushProvider implements PushProvider {

    private static final String AMAZON_SEND_PERMISSION = "com.amazon.device.messaging.permission.SEND";
    private static final long REGISTRATION_TIMEOUT_MS = 10000; // 10 seconds

    private static Boolean isAdmDependencyAvailable;


    @Override
    public int getPlatform() {
        return UAirship.AMAZON_PLATFORM;
    }


    @Override
    public String getRegistrationToken(@NonNull Context context) throws IOException, SecurityException {
        String admId = AdmWrapper.getRegistrationId(context);

        if (admId != null) {
            return admId;
        }

        RegistrationReceiver registerReceiver = new RegistrationReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT);
        intentFilter.addCategory(context.getPackageName());

        context.registerReceiver(registerReceiver, intentFilter, AMAZON_SEND_PERMISSION, new Handler(Looper.getMainLooper()));
        AdmWrapper.startRegistration(context);

        synchronized (registerReceiver) {
            try {
                registerReceiver.wait(REGISTRATION_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Logger.error("Interrupted while waiting for adm registration", e);
            }
        }

        context.unregisterReceiver(registerReceiver);
        return registerReceiver.registrationToken;
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        return true;
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        if (isAdmDependencyAvailable == null) {

            try {
                Class.forName("com.amazon.device.messaging.ADM");
                isAdmDependencyAvailable = true;
            } catch (ClassNotFoundException e) {
                isAdmDependencyAvailable = false;
            }
        }

        if (isAdmDependencyAvailable) {
            return AdmWrapper.isSupported();
        }

        return false;
    }

    @Nullable
    @Override
    public PushMessage processMessage(@NonNull Context context, @NonNull Bundle pushBundle) {
        return new PushMessage(pushBundle);
    }

    @Override
    public String toString() {
        return "Adm Push Provider";
    }

    private static class RegistrationReceiver extends BroadcastReceiver {

        private String registrationToken;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getExtras() != null && ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT.equals(intent.getAction())) {
                if (intent.getExtras().containsKey(ADMConstants.LowLevel.EXTRA_ERROR)) {
                    Logger.error("ADM error occurred: " + intent.getExtras().getString(ADMConstants.LowLevel.EXTRA_ERROR));
                } else {
                    this.registrationToken = intent.getStringExtra(ADMConstants.LowLevel.EXTRA_REGISTRATION_ID);
                }
            }

            if (this.isOrderedBroadcast()) {
                setResultCode(Activity.RESULT_OK);
            }

            synchronized (this) {
                this.notifyAll();
            }
        }
    }



}
