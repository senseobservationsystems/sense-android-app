/*
 ************************************************************************************************************
 *     Copyright (C)  2010 Sense Observation Systems, Rotterdam, the Netherlands.  All rights reserved.     *
 ************************************************************************************************************
 */
package nl.sense_os.service.location;

import nl.sense_os.service.Constants;
import nl.sense_os.service.MsgHandler;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class LocationSensor implements LocationListener {

    private static final String TAG = "Sense LocationSensor";
    private static final String SENSOR_NAME = "position";
    private static final String ALARM_ACTION = "nl.sense_os.service.LocationAlarm";
    private static final int ALARM_ID = 56;
    private static final long ALARM_INTERVAL = 1000 * 60 * 1;

    private Context context;
    private long time;
    private float distance;
    private BroadcastReceiver alarmReceiver;

    public LocationSensor(Context context) {
        this.context = context;
    }

    /**
     * Stops listening for location updates.
     */
    public void disable() {
        Log.v(TAG, "Disable location sensor");

        stopListening();
        stopAlarms();
    }

    /**
     * Starts listening for location updates, using the provided time and distance parameters.
     * 
     * @param time
     *            The minimum time between notifications, in meters.
     * @param distance
     *            The minimum distance interval for notifications, in meters.
     */
    public void enable(long time, float distance) {
        Log.v(TAG, "Enable location sensor");

        this.time = time;
        this.distance = distance;

        startListening();
        startAlarms();
    }

    @Override
    public void onLocationChanged(Location fix) {
        JSONObject json = new JSONObject();
        try {
            json.put("latitude", fix.getLatitude());
            json.put("longitude", fix.getLongitude());

            // always include all JSON fields, otherwise we get a problem posting data with varying
            // data_structure
            json.put("accuracy", fix.hasAccuracy() ? fix.getAccuracy() : -1.0f);
            json.put("altitude", fix.hasAltitude() ? fix.getAltitude() : -1.0d);
            json.put("speed", fix.hasSpeed() ? fix.getSpeed() : -1.0f);
            json.put("bearing", fix.hasBearing() ? fix.getBearing() : -1.0f);
            json.put("provider", null != fix.getProvider() ? fix.getProvider() : "unknown");
        } catch (JSONException e) {
            Log.e(TAG, "JSONException in onLocationChanged", e);
            return;
        }

        // pass message to the MsgHandler
        Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
        i.putExtra(MsgHandler.KEY_SENSOR_NAME, SENSOR_NAME);
        i.putExtra(MsgHandler.KEY_VALUE, json.toString());
        i.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_JSON);
        i.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
        context.startService(i);
    }

    private void onPeriodicAlarm() {
        Log.v(TAG, "Alarm");
        // TODO
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Log.v(TAG, "Provider " + provider + " disabled");
    }

    @Override
    public void onProviderEnabled(String provider) {
        // Log.v(TAG, "Provider " + provider + " enabled");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // switch (status) {
        // case LocationProvider.AVAILABLE:
        // Log.v(TAG, "Provider " + provider + " is now AVAILABLE");
        // case LocationProvider.OUT_OF_SERVICE:
        // Log.v(TAG, "Provider " + provider + " is now OUT OF SERVICE");
        // case LocationProvider.TEMPORARILY_UNAVAILABLE:
        // Log.v(TAG, "Provider " + provider + " is now TEMPORARILY UNAVAILABLE");
        // }
    }

    /**
     * @param distance
     *            Minimum distance between location updates.
     */
    public void setDistance(float distance) {
        this.distance = distance;
    }

    /**
     * @param time
     *            Minimum time between location refresh attempts.
     */
    public void setTime(long time) {
        this.time = time;
    }

    private void startAlarms() {

        // register to recieve the alarm
        alarmReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                onPeriodicAlarm();
            }
        };
        context.registerReceiver(alarmReceiver, new IntentFilter(ALARM_ACTION));

        // start periodic alarm
        Intent alarm = new Intent(ALARM_ACTION);
        PendingIntent operation = PendingIntent.getBroadcast(context, ALARM_ID, alarm, 0);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(operation);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), ALARM_INTERVAL,
                operation);
    }

    private void startListening() {

        LocationManager mgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        SharedPreferences mainPrefs = context.getSharedPreferences(Constants.MAIN_PREFS,
                Context.MODE_PRIVATE);
        final boolean gps = mainPrefs.getBoolean(Constants.PREF_LOCATION_GPS, true);
        final boolean network = mainPrefs.getBoolean(Constants.PREF_LOCATION_NETWORK, true);

        // start listening to GPS and/or Network location
        if (true == gps) {
            mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, time, distance, this);
        }
        if (true == network) {
            mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, time, distance, this);
        }
    }

    private void stopAlarms() {
        // unregister the receiver
        try {
            context.unregisterReceiver(alarmReceiver);
        } catch (IllegalArgumentException e) {
            // do nothing
        }

        // stop the alarms
        Intent alarm = new Intent(ALARM_ACTION);
        PendingIntent operation = PendingIntent.getBroadcast(context, ALARM_ID, alarm, 0);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(operation);
    }

    private void stopListening() {
        Log.v(TAG, "Stop listening");
        LocationManager mgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mgr.removeUpdates(this);
    }
}
