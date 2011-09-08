package nl.sense_os.service.provider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.sense_os.service.SensorData.DataPoint;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Debug;
import android.util.Log;

/**
 * ContentProvider that encapsulates recent sensor data. The data is stored in the devices RAM
 * memory, so this implementation is more energy efficient than storing everything in flash. This
 * does mean that parsing the selection queries is quite a challenge. Only a very limited set of
 * queries will work:
 * <ul>
 * <li>sensor_name = 'foo'</li>
 * <li>sensor_name != 'foo'</li>
 * <li>timestamp = foo</li>
 * <li>timestamp != foo</li>
 * <li>timestamp > foo</li>
 * <li>timestamp >= foo</li>
 * <li>timestamp < foo</li>
 * <li>timestamp <= foo</li>
 * <li>combinations of a sensor_name and a timestamp selection</li>
 * </ul>
 * 
 * @see ParserUtils
 * @see DataPoint
 */
public class LocalStorage extends ContentProvider {

    private static final String TAG = "Sense LocalStorage";

    public static final String AUTHORITY = "nl.sense_os.service.provider.LocalStorage";
    private static final String VALUES_TABLE_NAME = "recent_values";
    private static final int VALUES_URI = 1;

    private static final long RETENTION_TIME = 1000 * 60 * 90; // 90 minutes

    private static UriMatcher uriMatcher;
    private final Map<String, List<ContentValues>> storage = new HashMap<String, List<ContentValues>>();
    static {
        // set up URI matcher
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, VALUES_TABLE_NAME, VALUES_URI);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new IllegalArgumentException("Deleting rows is not possible");
    }

    @Override
    public String getType(Uri uri) {
        Log.v(TAG, "Get content type...");

        switch (uriMatcher.match(uri)) {
        case VALUES_URI:
            return DataPoint.CONTENT_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        // check URI
        if (uriMatcher.match(uri) != VALUES_URI) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // get currently stored values from the storage map
        String sensorName = values.getAsString(DataPoint.SENSOR_NAME);
        List<ContentValues> storedValues = storage.get(sensorName);
        if (null == storedValues) {
            storedValues = new ArrayList<ContentValues>();
        }

        // add the new data point
        // Log.v(TAG, "Insert '" + sensorName + "' value in local storage...");
        storedValues.add(values);

        // remove the oldest points from the storage
        List<ContentValues> tooOld = new ArrayList<ContentValues>();
        for (ContentValues storedValue : storedValues) {
            long ts = storedValue.getAsLong(DataPoint.TIMESTAMP);
            if (ts < System.currentTimeMillis() - RETENTION_TIME) {
                tooOld.add(storedValue);
            }
        }
        storedValues.removeAll(tooOld);

        storage.put(sensorName, storedValues);

        // notify any listeners (does this work properly?)
        Uri rowUri = ContentUris.withAppendedId(DataPoint.CONTENT_URI, 0);
        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }

    @Override
    public boolean onCreate() {
        Log.v(TAG, "Create local storage...");
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Log.v(TAG, "Query local storage...");

        // check URI
        switch (uriMatcher.match(uri)) {
        case VALUES_URI:
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // try to parse the selection criteria
        List<String> sensorNames = ParserUtils.getSelectedSensors(storage.keySet(), selection,
                selectionArgs);
        long[] timeRangeSelect = ParserUtils.getSelectedTimeRange(selection, selectionArgs);
        int transmitStateSelect = ParserUtils.getSelectedTransmitState(selection, selectionArgs);

        // create new cursor with the query result
        MatrixCursor result = new MatrixCursor(projection);

        for (String sensorName : sensorNames) {
            // return the sensor
            List<ContentValues> selectedValues = storage.get(sensorName);
            if (null != selectedValues) {
                for (ContentValues rowValues : selectedValues) {
                    long timestamp = rowValues.getAsLong(DataPoint.TIMESTAMP);
                    if (timestamp > timeRangeSelect[0] && timestamp < timeRangeSelect[1]) {
                        int transmitState = rowValues.getAsInteger(DataPoint.TRANSMIT_STATE);
                        if (transmitStateSelect == -1 || transmitState == transmitStateSelect) {
                            Object[] row = new Object[projection.length];
                            for (int i = 0; i < projection.length; i++) {
                                row[i] = rowValues.get(projection[i]);
                            }
                            result.addRow(row);
                        } else {
                            Log.v(TAG, "incorrect transmit state: " + transmitState);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Shows a summary of the current memory usage in the logs. Not used because it is not clear
     * what the information really tells us. The heap size keeps changing and the GCs keep
     * collecting garbage.
     */
    @SuppressWarnings("unused")
    private void showMemoryInfo() {
        long nativeFree = Debug.getNativeHeapFreeSize();
        long nativeTotal = Debug.getNativeHeapSize();
        double nativePct = BigDecimal.valueOf(nativeFree * 100d / nativeTotal).setScale(2, 0)
                .doubleValue();

        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);

        Log.d(TAG, "Memory info:\n" + "Native heap: " + nativeFree + " / " + nativeTotal
                + " free (" + nativePct + "%)" + "\n" + "");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        // check URI
        switch (uriMatcher.match(uri)) {
        case VALUES_URI:
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // try to parse the selection criteria
        List<String> sensorNames = ParserUtils.getSelectedSensors(storage.keySet(), selection,
                selectionArgs);
        long[] timeRangeSelect = ParserUtils.getSelectedTimeRange(selection, selectionArgs);
        int transmitStateSelect = ParserUtils.getSelectedTransmitState(selection, selectionArgs);

        for (String sensorName : sensorNames) {
            // return the sensor
            List<ContentValues> selectedValues = storage.get(sensorName);
            if (null != selectedValues) {
                for (ContentValues rowValues : selectedValues) {
                    long timestamp = rowValues.getAsLong(DataPoint.TIMESTAMP);
                    if (timestamp > timeRangeSelect[0] && timestamp < timeRangeSelect[1]) {
                        int transmitState = rowValues.getAsInteger(DataPoint.TRANSMIT_STATE);
                        if (transmitStateSelect == -1 || transmitState == transmitStateSelect) {
                            // Object[] row = new Object[projection.length];
                            // for (int i = 0; i < projection.length; i++) {
                            // row[i] = rowValues.get(projection[i]);
                            // }
                            // result.addRow(row);
                        } else {
                            Log.v(TAG, "incorrect transmit state: " + transmitState);
                        }
                    }
                }
            }
        }

        throw new IllegalArgumentException("Updating rows is not possible");
    }
}
