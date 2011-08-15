/*
 ************************************************************************************************************
 *     Copyright (C)  2010 Sense Observation Systems, Rotterdam, the Netherlands.  All rights reserved.     *
 ************************************************************************************************************
 */
package nl.sense_os.service.location;

import nl.sense_os.app.R;
import nl.sense_os.service.Constants;
import nl.sense_os.service.MsgHandler;
import nl.sense_os.service.SensorData.DataPoint;
import nl.sense_os.service.SensorData.SensorNames;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class LocationSensor {

    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location fix) {
            JSONObject json = new JSONObject();
            try {
                json.put("latitude", fix.getLatitude());
                json.put("longitude", fix.getLongitude());

                // always include all JSON fields, or we get problems with varying data_structure
                json.put("accuracy", fix.hasAccuracy() ? fix.getAccuracy() : -1.0d);
                json.put("altitude", fix.hasAltitude() ? fix.getAltitude() : -1.0);
                json.put("speed", fix.hasSpeed() ? fix.getSpeed() : -1.0d);
                json.put("bearing", fix.hasBearing() ? fix.getBearing() : -1.0d);
                json.put("provider", null != fix.getProvider() ? fix.getProvider() : "unknown");

                if (fix.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                    lastGpsFix = fix;
                }

            } catch (JSONException e) {
                Log.e(TAG, "JSONException in onLocationChanged", e);
                return;
            }

            // pass message to the MsgHandler
            Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
            i.putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.LOCATION);
            i.putExtra(MsgHandler.KEY_VALUE, json.toString());
            i.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_JSON);
            i.putExtra(MsgHandler.KEY_TIMESTAMP, fix.getTime());
            context.startService(i);
        }

        @Override
        public void onProviderDisabled(String provider) {
            checkSensorSettings();
        }

        @Override
        public void onProviderEnabled(String provider) {
            checkSensorSettings();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // do nothing
        }
    }

    private static final String TAG = "Sense LocationSensor";
    private static final String ALARM_ACTION = "nl.sense_os.service.LocationAlarm";
    private static final int ALARM_ID = 56;
    private static final long ALARM_INTERVAL = 1000 * 60 * 15;
    private static final boolean SELF_AWARE_MODE = true;
    private static final int NOTIF_ID = 764;

    private Context context;
    private LocationManager locMgr;
    private MyLocationListener listener;
    private long time;
    private float distance;

    /**
     * Receiver for periodic alarms to check on the sensor status.
     */
    private final BroadcastReceiver alarmReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            checkSensorSettings();
        }
    };

    private boolean isGpsAllowed;
    private boolean isNetworkAllowed;

    private boolean isListeningGps;
    private long listenGpsStart;
    private long listenGpsStop;
    private Location lastGpsFix;
    private long gpsMaxDelay = 1000 * 60;

    public LocationSensor(Context context) {
        this.context = context;
        locMgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        listener = new MyLocationListener();
    }

    /**
     * Checks to see if the sensor is still doing a useful job or whether it is better if we disable
     * it for a while. This method is a callback for a periodic alarm to check the sensor status.
     * 
     * @see #alarmReceiver
     */
    private void checkSensorSettings() {
        if (SELF_AWARE_MODE) {
            Log.v(TAG, "Check location sensor settings...");

            boolean isGpsProductive = isGpsProductive();
            boolean isDeviceMoving = isDeviceMoving();
            boolean isSwitchedOffTooLong = isSwitchedOffTooLong();
            boolean switchBackOn = isDeviceMoving || isSwitchedOffTooLong;

            if (!isListeningGps && !isGpsAllowed) {
                // current settings are OK
                Log.d(TAG, "Current settings are OK: GPS is not allowed");

            } else if (!isListeningGps && !switchBackOn) {
                // current settings are OK
                Log.d(TAG, "Current settings are OK: not listening and not moving");

            } else if (isListeningGps && isGpsProductive) {
                // current settings are OK
                Log.d(TAG, "Current settings are OK: GPS is being productive");

            } else if (isListeningGps && !isGpsProductive) {
                // switch GPS off: not productive
                Log.d(TAG, "GPS listening should be turned off: GPS is not productive");
                stopListening();
                startListening(false);
                notifyListeningStopped();

            } else if (!isListeningGps && switchBackOn && isGpsAllowed) {
                // switch GPS back on
                if (isDeviceMoving) {
                    Log.d(TAG, "GPS should be turned back on: " + "device is on the move");
                } else {
                    Log.d(TAG, "GPS should be turned back on: " + "switched off for too long");
                }
                stopListening();
                startListening(true);

            } else {
                Log.w(TAG, "Unexpected location sensor state!");
            }
        }
    }

    /**
     * Stops listening for location updates.
     */
    public void disable() {
        Log.v(TAG, "Disable location sensor");

        stopListening();
        stopAlarms();
    }

    private void notifyListeningStopped() {

        Notification note = new Notification(R.drawable.ic_status_sense, "GPS listening stopped",
                System.currentTimeMillis());
        note.setLatestEventInfo(context, "Sense location sensor", "GPS listening stopped",
                PendingIntent.getActivity(context, 0, new Intent("nl.sense_os.app.SenseApp"),
                        Intent.FLAG_ACTIVITY_NEW_TASK));
        note.flags = Notification.FLAG_AUTO_CANCEL;
        note.vibrate = new long[] { 0, 100, 100, 300, 100, 100, 100, 300, 100, 100, 100, 300 };
        note.defaults = Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND;
        NotificationManager mgr = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mgr.notify(NOTIF_ID, note);
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
        this.gpsMaxDelay = 5 * time;
        this.distance = distance;

        startListening();
        startAlarms();
        getLastKnownLocation();
    }

    private void getLastKnownLocation() {

        // get the most recent location fixes
        Location gpsFix = null;
        Location nwFix = null;
        if (isGpsAllowed) {
            gpsFix = locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (isNetworkAllowed) {
            nwFix = locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        // see which is best
        if (null != gpsFix || null != nwFix) {
            Log.v(TAG, "Use last known location as first sensor value");
            Location bestFix = null;
            if (null != gpsFix) {
                if (System.currentTimeMillis() - 1000 * 60 * 60 * 1 < gpsFix.getTime()) {
                    // recent GPS fix
                    bestFix = gpsFix;
                }
            }
            if (null != nwFix) {
                if (null == bestFix) {
                    bestFix = nwFix;
                } else if (nwFix.getTime() < gpsFix.getTime()
                        && nwFix.getAccuracy() < bestFix.getAccuracy() + 100) {
                    // network fix is more recent and pretty accurate
                    bestFix = nwFix;
                }
            }
            if (null != bestFix) {
                listener.onLocationChanged(bestFix);
            } else {
                Log.v(TAG, "No usable last known location");
            }
        } else {
            Log.v(TAG, "No last known location");
        }
    }

    /**
     * @return true if GPS has recently produced new data points.
     */
    private boolean isGpsProductive() {

        boolean productive = isListeningGps;
        if (isListeningGps) {

            // check if any updates have been received recently from the GPS sensor
            if (lastGpsFix != null) {
                if (System.currentTimeMillis() - lastGpsFix.getTime() > gpsMaxDelay) {
                    // no updates for long time
                    Log.d(TAG, "GPS is NOT productive: no updates for a long time");
                    productive = false;
                }
            } else if (System.currentTimeMillis() - listenGpsStart > gpsMaxDelay) {
                // no updates for a long time
                Log.d(TAG, "GPS is NOT productive: no updates for a long time");
                productive = false;
            } else {
                Log.d(TAG, "GPS is productive");
            }

        } else {
            Log.d(TAG, "GPS is NOT productive: the listener is disabled");
        }

        return productive;
    }

    private boolean isDeviceMoving() {
        Log.v(TAG, "Check if device is moving");

        boolean moving = false;

        Cursor dataPoints = null;
        try {
            Uri uri = DataPoint.CONTENT_URI;
            String[] projection = new String[] { DataPoint.SENSOR_NAME, DataPoint.TIMESTAMP,
                    DataPoint.VALUE };
            String selection = DataPoint.SENSOR_NAME + "='" + SensorNames.LIN_ACCELERATION + "'";
            dataPoints = context.getContentResolver().query(uri, projection, selection, null, null);

            Log.d(TAG, dataPoints.getCount() + " linear acceleration data points");

        } finally {
            if (null != dataPoints) {
                dataPoints.close();
            }
        }

        return moving;
    }

    private boolean isSwitchedOffTooLong() {

        boolean tooLong = !isListeningGps;

        if (System.currentTimeMillis() - listenGpsStop > 5 * time) {
            // GPS has been turned off for a long time, or was never even started
            Log.d(TAG, "GPS should be turned on: "
                    + " it has been turned off for a long time, or was never even started");
            tooLong = true;
        } else if (!locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // the network provider is disabled: GPS is the only option
            Log.d(TAG, "GPS should be turned on: " + " the network provider is disabled");
            tooLong = true;
        } else {
            Log.d(TAG, "Not useful because we just decided to switch off GPS");
            tooLong = false;
        }

        return tooLong;
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
        gpsMaxDelay = 5 * time;
    }

    private void startAlarms() {

        // register to recieve the alarm
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
        startListening(true);
    }

    private void startListening(boolean useGps) {

        SharedPreferences mainPrefs = context.getSharedPreferences(Constants.MAIN_PREFS,
                Context.MODE_PRIVATE);
        isGpsAllowed = mainPrefs.getBoolean(Constants.PREF_LOCATION_GPS, true);
        isNetworkAllowed = mainPrefs.getBoolean(Constants.PREF_LOCATION_NETWORK, true);

        // start listening to GPS and/or Network location
        if ((useGps || !isNetworkAllowed) && isGpsAllowed) {
            Log.v(TAG, "Start listening to location updates from Network and GPS");
            Log.d(TAG, "time=" + time + ", distance=" + distance);
            locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, time, distance, listener);
            isListeningGps = true;
            listenGpsStart = System.currentTimeMillis();
        } else if (isNetworkAllowed) {
            Log.v(TAG, "Start listening to location updates from Network");
            locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, time, distance,
                    listener);
        } else {
            Log.w(TAG, "Not listening to any location provider at all!");
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
        Log.v(TAG, "Stop listening to location updates");
        locMgr.removeUpdates(listener);

        if (isListeningGps) {
            listenGpsStop = System.currentTimeMillis();
            isListeningGps = false;
        }
    }
}
