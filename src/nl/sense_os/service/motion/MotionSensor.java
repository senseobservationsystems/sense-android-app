/*
 * ***********************************************************************************************************
 * Copyright (C) 2010 Sense Observation Systems, Rotterdam, the Netherlands. All rights reserved. *
 * **
 * ************************************************************************************************
 * *********
 */
package nl.sense_os.service.motion;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import nl.sense_os.service.Constants;
import nl.sense_os.service.MsgHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

public class MotionSensor implements SensorEventListener {

    private static final String NAME_ACCELR = "accelerometer";
    private static final String NAME_LINACC = "linear acceleration";
    private static final String NAME_GYRO = "gyroscope";
    private static final String NAME_MAGNET = "magnetic_field";
    private static final String NAME_ORIENT = "orientation";
    private static final String NAME_EPI = "accelerometer (epi-mode)";
    /**
     * Stand-in for Sensor.TYPE_LINEAR_ACCELERATION constant for API < 9.
     */
    private static final int TYPE_LINEAR_ACCELERATION = 10;
    private static final String TAG = "Sense MotionSensor";
    private static final String TYPE_MOTION_ENERGY = "motion energy";
    private FallDetector fallDetector;
    private boolean useFallDetector;
    private boolean isMotionEnergyMode;
    private boolean hasLinAccSensor;
    private boolean isEpiMode;
    private boolean isUnregisterWhenIdle;
    private boolean firstStart = true;
    private Context context;
    private long[] lastSampleTimes = new long[50];
    private Handler motionHandler = new Handler();
    private boolean motionSensingActive = false;
    private Runnable motionThread = null;
    private long sampleDelay = 0; // in milliseconds
    private long[] lastLocalSampleTimes = new long[50];
    private long localBufferTime = 15 * 1000;
    private List<Sensor> sensors;
    private SensorManager smgr;
    private long firstTimeSend = 0;
    private JSONArray[] dataBuffer = new JSONArray[10];
    private double avgSpeedChange;
    private int avgSpeedCount;
    private float[] gravity = { 0, 0, SensorManager.GRAVITY_EARTH };
    private long lastLinAccSampleTime;

    public MotionSensor(Context context) {
        this.context = context;
        smgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensors = smgr.getSensorList(Sensor.TYPE_ALL);
        fallDetector = new FallDetector();
    }

    /**
     * Calculates the kinetic energy of an accelerometer sample. Tries to determine the gravity
     * component by putting the signal through a first-order low-pass filter.
     * 
     * @param values
     *            Array with accelerometer values for the three axes.
     * @return The approximate kinetic energy of the sample.
     */
    private float[] calcLinAcc(float[] values) {

        // low-pass filter raw accelerometer data to approximate the gravity
        final float alpha = 0.8f; // filter constants should depend on sample rate
        gravity[0] = alpha * gravity[0] + (1 - alpha) * values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * values[2];

        return new float[] { values[0] - gravity[0], values[1] - gravity[1], values[2] - gravity[2] };
    }

    private void doEpiSample(Sensor sensor, JSONObject json) {

        if (dataBuffer[sensor.getType()] == null) {
            dataBuffer[sensor.getType()] = new JSONArray();
        }
        dataBuffer[sensor.getType()].put(json);
        if (lastLocalSampleTimes[sensor.getType()] == 0) {
            lastLocalSampleTimes[sensor.getType()] = System.currentTimeMillis();
        }

        if (System.currentTimeMillis() > lastLocalSampleTimes[sensor.getType()] + localBufferTime) {
            // send the stuff
            // Log.v(TAG, "Transmit accelerodata: " + dataBuffer[sensor.getType()].length());
            // pass message to the MsgHandler
            Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
            i.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_EPI);
            i.putExtra(MsgHandler.KEY_SENSOR_DEVICE, sensor.getName());
            i.putExtra(
                    MsgHandler.KEY_VALUE,
                    "{\"interval\":"
                            + Math.round(localBufferTime / dataBuffer[sensor.getType()].length())
                            + ",\"data\":" + dataBuffer[sensor.getType()].toString() + "}");
            i.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_JSON_TIME_SERIE);
            i.putExtra(MsgHandler.KEY_TIMESTAMP, lastLocalSampleTimes[sensor.getType()]);
            context.startService(i);
            dataBuffer[sensor.getType()] = new JSONArray();
            lastLocalSampleTimes[sensor.getType()] = System.currentTimeMillis();
            if (firstTimeSend == 0) {
                firstTimeSend = System.currentTimeMillis();
            }
        }
    }

    /**
     * Measures the speed change and determines the average, for the motion energy sensor.
     * 
     * @param event
     *            The sensor change event with accelerometer or linear acceleration data.
     */
    private void doMotionSample(SensorEvent event) {

        float[] linAcc = null;

        // approximate linear acceleration if we have no special sensor for it
        if (!hasLinAccSensor && Sensor.TYPE_ACCELEROMETER == event.sensor.getType()) {
            linAcc = calcLinAcc(event.values);
        } else if (hasLinAccSensor && TYPE_LINEAR_ACCELERATION == event.sensor.getType()) {
            linAcc = event.values;
        } else {
            // sensor is not the right type
            return;
        }

        // calculate speed change and adjust average
        if (null != linAcc) {
            float timeStep = (System.currentTimeMillis() - lastLinAccSampleTime) / 1000f;
            lastLinAccSampleTime = System.currentTimeMillis();
            if (timeStep > 0 && timeStep < 1) {
                float accLength = (float) Math.sqrt(Math.pow(linAcc[0], 2) + Math.pow(linAcc[1], 2)
                        + Math.pow(linAcc[2], 2));

                float speedChange = accLength * timeStep;
                // Log.v(TAG, "Speed change: " + speedChange);

                avgSpeedChange = (avgSpeedCount * avgSpeedChange + speedChange)
                        / (avgSpeedCount + 1);
                avgSpeedCount++;
            }
        }
    }

    public long getSampleDelay() {
        return sampleDelay;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        // pass sensor value to fall detector first
        if (useFallDetector && sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double aX = event.values[1];
            double aY = event.values[0];
            double aZ = event.values[2];
            float accVecSum = (float) Math.sqrt(aX * aX + aY * aY + aZ * aZ);

            if (fallDetector.fallDetected(accVecSum)) {
                sendFallMessage(true); // send msg
            }

        }

        // if motion energy sensor is active, determine energy of every sample
        boolean isMotionSample = !hasLinAccSensor && Sensor.TYPE_ACCELEROMETER == sensor.getType()
                || hasLinAccSensor && TYPE_LINEAR_ACCELERATION == sensor.getType();
        if (isMotionEnergyMode && isMotionSample) {
            doMotionSample(event);
        }

        // check sensor delay
        if (System.currentTimeMillis() > lastSampleTimes[sensor.getType()] + sampleDelay) {
            lastSampleTimes[sensor.getType()] = System.currentTimeMillis();
        } else {
            return;
        }

        // Epi-mode is only interested in the accelerometer
        if (isEpiMode && sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        // determine sensor name
        String sensorName = "";
        switch (sensor.getType()) {
        case Sensor.TYPE_ACCELEROMETER:
            sensorName = NAME_ACCELR;
            break;
        case Sensor.TYPE_ORIENTATION:
            sensorName = NAME_ORIENT;
            break;
        case Sensor.TYPE_MAGNETIC_FIELD:
            sensorName = NAME_MAGNET;
            break;
        case Sensor.TYPE_GYROSCOPE:
            sensorName = NAME_GYRO;
            break;
        case TYPE_LINEAR_ACCELERATION:
            sensorName = NAME_LINACC;
            break;
        default:
            Log.w(TAG, "Unexpected sensor type: " + sensor.getType());
            return;
        }

        // prepare JSON object to send to MsgHandler
        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
        NumberFormat formatter = new DecimalFormat("###.###", otherSymbols);
        JSONObject json = new JSONObject();
        int axis = 0;
        try {
            for (double value : event.values) {
                switch (axis) {
                case 0:
                    if (sensor.getType() == Sensor.TYPE_ACCELEROMETER
                            || sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD
                            || sensor.getType() == TYPE_LINEAR_ACCELERATION) {
                        json.put("x-axis", Float.parseFloat(formatter.format(value)));
                    } else if (sensor.getType() == Sensor.TYPE_ORIENTATION
                            || sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        json.put("azimuth", Float.parseFloat(formatter.format(value)));
                    } else {
                        Log.e(TAG, "Unexpected sensor type creating JSON value");
                        return;
                    }
                    break;
                case 1:
                    if (sensor.getType() == Sensor.TYPE_ACCELEROMETER
                            || sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD
                            || sensor.getType() == TYPE_LINEAR_ACCELERATION) {
                        json.put("y-axis", Float.parseFloat(formatter.format(value)));
                    } else if (sensor.getType() == Sensor.TYPE_ORIENTATION
                            || sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        json.put("pitch", Float.parseFloat(formatter.format(value)));
                    } else {
                        Log.e(TAG, "Unexpected sensor type creating JSON value");
                        return;
                    }
                    break;
                case 2:
                    if (sensor.getType() == Sensor.TYPE_ACCELEROMETER
                            || sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD
                            || sensor.getType() == TYPE_LINEAR_ACCELERATION) {
                        json.put("z-axis", Float.parseFloat(formatter.format(value)));
                    } else if (sensor.getType() == Sensor.TYPE_ORIENTATION
                            || sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        json.put("roll", Float.parseFloat(formatter.format(value)));
                    } else {
                        Log.e(TAG, "Unexpected sensor type creating JSON value");
                        return;
                    }
                    break;
                default:
                    Log.w(TAG, "Unexpected sensor value! More than three axes?!");
                }
                axis++;
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException in onSensorChanged", e);
            return;
        }

        // add the data to the buffer if we are in Epi-mode:
        if (isEpiMode) {
            doEpiSample(sensor, json);
        } else {
            sendNormalMessage(sensor, sensorName, json);
        }

        // send motion energy message
        if (isMotionEnergyMode && isMotionSample) {
            sendEnergyMessage();
        }

        if (isUnregisterWhenIdle && sampleDelay > 500 && motionSensingActive && !useFallDetector
                && !isMotionEnergyMode) {

            // unregister the listener and start again in sampleDelay seconds
            stopMotionSensing();
            motionHandler.postDelayed(motionThread = new Runnable() {

                @Override
                public void run() {
                    startMotionSensing(sampleDelay);
                }
            }, sampleDelay);
        }
    }

    /**
     * Sends message with average motion energy to the MsgHandler.
     */
    private void sendEnergyMessage() {
        if (avgSpeedCount > 1) {
            // Log.v(TAG, "Motion energy: " + avgSpeedChange + " (" + avgSpeedCount + " samples)");

            // prepare JSON object to send to MsgHandler
            DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
            NumberFormat formatter = new DecimalFormat("###.###", otherSymbols);

            Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
            i.putExtra(MsgHandler.KEY_SENSOR_NAME, TYPE_MOTION_ENERGY);
            i.putExtra(MsgHandler.KEY_SENSOR_DEVICE, TYPE_MOTION_ENERGY);
            i.putExtra(MsgHandler.KEY_VALUE, Float.parseFloat(formatter.format(avgSpeedChange)));
            i.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_FLOAT);
            i.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
            context.startService(i);

        }
        avgSpeedChange = 0;
        avgSpeedCount = 0;
    }

    private void sendFallMessage(boolean fall) {
        Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
        i.putExtra(MsgHandler.KEY_SENSOR_NAME, "fall detector");
        i.putExtra(MsgHandler.KEY_SENSOR_DEVICE, fallDetector.demo ? "demo fall" : "human fall");
        i.putExtra(MsgHandler.KEY_VALUE, fall);
        i.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_BOOL);
        i.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
        context.startService(i);
    }

    private void sendNormalMessage(Sensor sensor, String sensorName, JSONObject json) {
        Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
        i.putExtra(MsgHandler.KEY_SENSOR_NAME, sensorName);
        i.putExtra(MsgHandler.KEY_SENSOR_DEVICE, sensor.getName());
        i.putExtra(MsgHandler.KEY_VALUE, json.toString());
        i.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_JSON);
        i.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
        context.startService(i);
    }

    public void setSampleDelay(long _sampleDelay) {
        sampleDelay = _sampleDelay;
    }

    public void startMotionSensing(long sampleDelay) {

        final SharedPreferences mainPrefs = context.getSharedPreferences(Constants.MAIN_PREFS,
                Context.MODE_PRIVATE);
        isEpiMode = mainPrefs.getBoolean(Constants.PREF_MOTION_EPIMODE, false);
        isMotionEnergyMode = mainPrefs.getBoolean(Constants.PREF_MOTION_ENERGY, false);
        isUnregisterWhenIdle = mainPrefs.getBoolean(Constants.PREF_MOTION_UNREG, true);

        if (isEpiMode) {
            sampleDelay = 0;
        }

        // check if the fall detector is enabled
        useFallDetector = mainPrefs.getBoolean(Constants.PREF_MOTION_FALL_DETECT, false);
        if (fallDetector.demo = mainPrefs.getBoolean(Constants.PREF_MOTION_FALL_DETECT_DEMO, false)) {
            useFallDetector = true;
        }

        if (firstStart && useFallDetector) {
            sendFallMessage(false);
            firstStart = false;
        }

        motionSensingActive = true;
        setSampleDelay(sampleDelay);
        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER
                    || sensor.getType() == Sensor.TYPE_ORIENTATION
                    || sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                // Log.d(TAG, "registering for sensor " + sensor.getName());
                smgr.registerListener(this, sensor,
                        useFallDetector || isEpiMode ? SensorManager.SENSOR_DELAY_GAME
                                : SensorManager.SENSOR_DELAY_NORMAL);
            } else if (Build.VERSION.SDK_INT >= 9 && sensor.getType() == 10) {
                // use linear accelerometer sensor on gingerbread phones
                // Log.v(TAG,
                // "w00t w00t! Found fancy linear acceleration sensor! pwning gravity...");
                smgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                hasLinAccSensor = true;
            }
        }
    }

    public void stopMotionSensing() {
        try {
            motionSensingActive = false;
            smgr.unregisterListener(this);

            if (motionThread != null) {
                motionHandler.removeCallbacks(motionThread);
                motionThread = null;
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

    }
}
