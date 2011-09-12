package nl.sense_os.service.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.sense_os.service.SensorData.DataPoint;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
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

    /**
     * Inner class that handles the creation of the SQLite3 database with the desired tables and
     * columns.
     */
    private static class DbHelper extends SQLiteOpenHelper {

        protected static final String DATABASE_NAME = "persitant_storage.sqlite3";
        protected static final int DATABASE_VERSION = 1;

        DbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            final StringBuilder sb = new StringBuilder("CREATE TABLE " + TABLE_PERSISTANT + "(");
            sb.append(BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT");
            sb.append(", " + DataPoint.SENSOR_NAME + " STRING");
            sb.append(", " + DataPoint.SENSOR_DESCRIPTION + " STRING");
            sb.append(", " + DataPoint.DATA_TYPE + " STRING");
            sb.append(", " + DataPoint.TIMESTAMP + " INTEGER");
            sb.append(", " + DataPoint.VALUE + " STRING");
            sb.append(", " + DataPoint.TRANSMIT_STATE + " INTEGER");
            sb.append(");");
            db.execSQL(sb.toString());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVers, int newVers) {
            Log.w(TAG, "Upgrading '" + DATABASE_NAME + "' database from version " + oldVers
                    + " to " + newVers + ", which will destroy all old data");

            db.execSQL("DROP TABLE IF EXISTS " + TABLE_VOLATILE);
            onCreate(db);
        }
    }

    private static final String TAG = "Sense LocalStorage";

    public static final String AUTHORITY = "nl.sense_os.service.provider.LocalStorage";
    private static final String TABLE_VOLATILE = "recent_values";
    private static final String TABLE_PERSISTANT = "persisted_values";
    private static final int VOLATILE_VALUES_URI = 1;
    private static final int PERSISTED_VALUES_URI = 2;
    private static final long RETENTION_TIME = 1000 * 60 * 90; // 90 minutes

    private static long count = 0;

    private DbHelper dbHelper;

    private static UriMatcher uriMatcher;
    private final static Map<String, List<ContentValues>> storage = new HashMap<String, List<ContentValues>>();
    static {
        // set up URI matcher
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, TABLE_VOLATILE, VOLATILE_VALUES_URI);
        uriMatcher.addURI(AUTHORITY, TABLE_PERSISTANT, PERSISTED_VALUES_URI);
    }

    @Override
    public int delete(Uri uri, String where, String[] selectionArgs) {
        switch (uriMatcher.match(uri)) {
        case VOLATILE_VALUES_URI:
            throw new IllegalArgumentException("Cannot delete recent data points!");
        case PERSISTED_VALUES_URI:
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int result = db.delete(TABLE_PERSISTANT, where, selectionArgs);
            return result;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public String getType(Uri uri) {
        Log.v(TAG, "Get content type...");
        switch (uriMatcher.match(uri)) {
        case VOLATILE_VALUES_URI:
            return DataPoint.CONTENT_TYPE;
        case PERSISTED_VALUES_URI:
            return DataPoint.CONTENT_TYPE;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        // check URI
        switch (uriMatcher.match(uri)) {
        case VOLATILE_VALUES_URI:
            break;
        case PERSISTED_VALUES_URI:
            throw new IllegalArgumentException(
                    "Cannot insert directly into persitant data point database");
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // add a unique ID
        values.put(BaseColumns._ID, count);
        count++;

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
        Uri rowUri = ContentUris.withAppendedId(DataPoint.CONTENT_URI, count - 1);
        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }

    @Override
    public boolean onCreate() {
        Log.v(TAG, "Create local storage...");
        dbHelper = new DbHelper(getContext());
        return true;
    }

    private void persist(List<ContentValues> dataPoints) {
        Log.d(TAG, "Persist " + dataPoints.size() + " data points");
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            for (ContentValues dataPoint : dataPoints) {
                db.insert(TABLE_PERSISTANT, DataPoint.SENSOR_NAME, dataPoint);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception persisting recent sensor values to database", e);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String where, String[] selectionArgs,
            String sortOrder) {
        // Log.v(TAG, "Query local storage...");

        // default projection
        if (projection == null) {
            projection = new String[] { BaseColumns._ID, DataPoint.SENSOR_NAME,
                    DataPoint.SENSOR_DESCRIPTION, DataPoint.DATA_TYPE, DataPoint.VALUE,
                    DataPoint.TIMESTAMP, DataPoint.TRANSMIT_STATE };
        }

        // check URI
        switch (uriMatcher.match(uri)) {
        case VOLATILE_VALUES_URI:

            try {
                // do selection
                List<ContentValues> selection = select(where, selectionArgs);

                // create new cursor with the query result
                MatrixCursor result = new MatrixCursor(projection);
                for (ContentValues dataPoint : selection) {
                    Object[] row = new Object[projection.length];
                    for (int i = 0; i < projection.length; i++) {
                        row[i] = dataPoint.get(projection[i]);
                    }
                    result.addRow(row);
                }

                return result;

            } catch (Exception e) {
                Log.e(TAG, "Failed to query the recent data points", e);
            }

        case PERSISTED_VALUES_URI:

            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Cursor persistantResult = db.query(TABLE_PERSISTANT, projection, where,
                        selectionArgs, null, null, sortOrder);
                return persistantResult;
            } catch (Exception e) {
                Log.e(TAG, "Failed to query the persisted data points", e);
            }

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    private synchronized List<ContentValues> select(String where, String[] selectionArgs) {

        // try to parse the selection criteria
        List<String> sensorNames = ParserUtils.getSelectedSensors(storage.keySet(), where,
                selectionArgs);
        long[] timeRangeSelect = ParserUtils.getSelectedTimeRange(where, selectionArgs);
        int transmitStateSelect = ParserUtils.getSelectedTransmitState(where, selectionArgs);

        List<ContentValues> selection = new ArrayList<ContentValues>();
        for (String name : sensorNames) {
            List<ContentValues> dataPoints = storage.get(name);
            if (null != dataPoints) {
                for (ContentValues dataPoint : dataPoints) {
                    long timestamp = dataPoint.getAsLong(DataPoint.TIMESTAMP);
                    if (timestamp >= timeRangeSelect[0] && timestamp <= timeRangeSelect[1]) {
                        int transmitState = dataPoint.getAsInteger(DataPoint.TRANSMIT_STATE);
                        if (transmitStateSelect == -1 || transmitState == transmitStateSelect) {
                            selection.add(dataPoint);
                        } else {
                            // Log.v(TAG, "Transmit state doesn't match: " + transmitState);
                        }
                    } else {
                        // Log.d(TAG, "Outside time range: " + timestamp);
                    }
                }
            } else {
                Log.d(TAG, "Could not find values for the selected sensor: '" + name + "'");
            }
        }

        return selection;
    }

    @Override
    public int update(Uri uri, ContentValues newValues, String where, String[] selectionArgs) {
        // Log.v(TAG, "Update local storage...");

        // check URI
        switch (uriMatcher.match(uri)) {
        case VOLATILE_VALUES_URI:
            break;
        case PERSISTED_VALUES_URI:
            throw new IllegalArgumentException("Cannot update the persisted data points");
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // persist parameter is used to initiate persisting the volatile data
        boolean persist = "true".equals(uri.getQueryParameter("persist"));

        // select the correct data points to update
        List<ContentValues> selection = select(where, selectionArgs);

        // do the update
        int result = 0;
        if (!persist) {
            for (ContentValues dataPoint : selection) {
                dataPoint.putAll(newValues);
                result++;
            }
        } else {
            persist(selection);
            storage.clear();
        }

        // notify content observers
        getContext().getContentResolver().notifyChange(uri, null);

        return result;
    }
}
