/*
 * ***********************************************************************************************************
 * Copyright (C) 2010 Sense Observation Systems, Rotterdam, the Netherlands. All rights reserved. *
 * **
 * ************************************************************************************************
 * *********
 */
package nl.sense_os.service;

import nl.sense_os.service.SensePrefs.Status;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class DataTransmitter extends BroadcastReceiver {

    private static final String TAG = "Sense DataTransmitter";
    public static final int REQID = 26;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "onReceive");

        SharedPreferences statusPrefs = context.getSharedPreferences(SensePrefs.STATUS_PREFS,
                Context.MODE_PRIVATE);
        final boolean alive = statusPrefs.getBoolean(Status.MAIN, false);

        // check if the service is (supposed to be) alive before scheduling next alarm
        if (true == alive) {
            // start send task
            Intent task = new Intent(MsgHandler.ACTION_SEND_DATA);
            context.startService(task);
        } else {
            Log.v(TAG, "Sense service should not be alive!");
        }
    }
}
