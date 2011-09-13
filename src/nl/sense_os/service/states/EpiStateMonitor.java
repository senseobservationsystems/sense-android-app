package nl.sense_os.service.states;

import nl.sense_os.service.SensorData;
import nl.sense_os.service.SensorData.DataPoint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

public class EpiStateMonitor extends AbstractStateMonitor {

    private static final long TIME_RANGE = 1000 * 60;
    private static final String ACTION_UPDATE_STATE = "nl.sense_os.states.EpiStateUpdate";
    private static final String TAG = "EpiStateMonitor";
    private long lastAnalyzed;

    /**
     * Updates the epi-state by analyzing the local data points.
     */
    @Override
    protected void updateState() {
        Cursor data = null;
        try {
            String[] projection = new String[] { DataPoint._ID, DataPoint.SENSOR_NAME,
                    DataPoint.VALUE, DataPoint.TIMESTAMP };
            String where = DataPoint.SENSOR_NAME + "='" + SensorData.SensorNames.ACCELEROMETER_EPI
                    + "'" + " AND " + DataPoint.TIMESTAMP + ">"
                    + (System.currentTimeMillis() - TIME_RANGE);
            data = getContentResolver().query(DataPoint.CONTENT_URI, projection, where, null, null);

            if (null != data && data.moveToFirst()) {
                int result = analyze(data);
                Log.d(TAG, "Epi analysis result: " + result);
                if (result > 0) {
                    Log.d(TAG, "SEIZURE!!!");
                    sendAlert(data);
                }
            } else {
                Log.d(TAG, "No recent epi data to analyze");
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse data");
            return;

        } finally {
            if (null != data) {
                data.close();
                data = null;
            }
        }
    }

    private void sendAlert(Cursor data) {
        Intent alert = new Intent("nl.ask.paige.receiver.IntentRx");
        alert.putExtra("sensorName", "epi state");
        alert.putExtra("value", "SEIZURE!!!");
        alert.putExtra("timestamp", "" + System.currentTimeMillis());
        sendBroadcast(alert);
    }

    private int analyze(Cursor data) throws JSONException {

        data.moveToLast();

        // parse the value
        long timestamp = data.getLong(data.getColumnIndex(DataPoint.TIMESTAMP));
        if (lastAnalyzed < timestamp) {
            lastAnalyzed = timestamp;
        } else {
            Log.d(TAG, "Already analyzed this one");
            return 0;
        }
        String rawValue = data.getString(data.getColumnIndex(DataPoint.VALUE));
        JSONObject value = new JSONObject(rawValue);
        JSONArray array = value.getJSONArray("data");
        int interval = value.getInt("interval");

        Log.d(TAG, "Found " + array.length() + " epi data points, interval: " + interval + " ms");

        // analyze the array of data points
        double total = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject dataPoint = array.getJSONObject(i);
            double x = dataPoint.getDouble("x-axis");
            double y = dataPoint.getDouble("y-axis");
            double z = dataPoint.getDouble("z-axis");
            double length = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));

            total += length;
        }

        double avg = total / array.length();
        Log.d(TAG, "Average acceleration: " + avg);
        if (avg > 12) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        startMonitoring(ACTION_UPDATE_STATE, 5000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
    }
}
