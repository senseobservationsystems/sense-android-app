package nl.sense_os.service.provider;

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

        protected static final String DATABASE_NAME = "persitent_storage.sqlite3";
        protected static final int DATABASE_VERSION = 1;

        DbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            final StringBuilder sb = new StringBuilder("CREATE TABLE " + TABLE_PERSISTENT + "(");
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
    private static final String TABLE_PERSISTENT = "persisted_values";
    private static final int VOLATILE_VALUES_URI = 1;
    private static final int PERSISTED_VALUES_URI = 2;
    private static final int MAX_VOLATILE_VALUES = 100;

    private static long count = 0;

    private DbHelper dbHelper;

    private static UriMatcher uriMatcher;
    private final static Map<String, ContentValues[]> storage = new HashMap<String, ContentValues[]>();
    private final static Map<String, Integer> pointers = new HashMap<String, Integer>();

    static {
        // set up URI matcher
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, TABLE_VOLATILE, VOLATILE_VALUES_URI);
        uriMatcher.addURI(AUTHORITY, TABLE_PERSISTENT, PERSISTED_VALUES_URI);
    }

    @Override
    public int delete(Uri uri, String where, String[] selectionArgs) {
        switch (uriMatcher.match(uri)) {
        case VOLATILE_VALUES_URI:
            throw new IllegalArgumentException("Cannot delete recent data points!");
        case PERSISTED_VALUES_URI:
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int result = db.delete(TABLE_PERSISTENT, where, selectionArgs);
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
                    "Cannot insert directly into persistent data point database");
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // add a unique ID
        values.put(BaseColumns._ID, count);
        count++;

        // get currently stored values from the storage map
        String sensorName = values.getAsString(DataPoint.SENSOR_NAME);
        ContentValues[] storedValues = storage.get(sensorName);
        Integer index = pointers.get(sensorName);
        if (null == storedValues) {
            storedValues = new ContentValues[MAX_VOLATILE_VALUES];
            index = 0;
        }

        // check for buffer overflows
        if (index >= MAX_VOLATILE_VALUES) {
            Log.d(TAG, "Buffer overflow! More than " + MAX_VOLATILE_VALUES + " points for '"
                    + sensorName + "'. Send to persistent storage...");

            // find out how many values have to be put in the persistant storage
            int persistFrom = -1;
            for (int i = 0; i < storedValues.length; i++) {
                if (storedValues[i].getAsInteger(DataPoint.TRANSMIT_STATE) == 0) {
                    // found the first data point that was not transmitted yet
                    persistFrom = i;
                    break;
                }
            }

            // persist the data that was not sent yet
            if (-1 != persistFrom) {
                ContentValues[] unsent = new ContentValues[MAX_VOLATILE_VALUES - persistFrom];
                System.arraycopy(storedValues, persistFrom, unsent, 0, unsent.length);
                persist(unsent);
            }

            // reset the array and index
            storedValues = new ContentValues[MAX_VOLATILE_VALUES];
            index = 0;
        }

        // add the new data point
        // Log.v(TAG, "Insert '" + sensorName + "' value in local storage...");
        storedValues[index] = values;
        index++;
        storage.put(sensorName, storedValues);
        pointers.put(sensorName, index);

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

    private void persist(ContentValues[] storedValues) {
        Log.d(TAG, "Persist " + storedValues.length + " data points");
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            for (ContentValues dataPoint : storedValues) {
                db.insert(TABLE_PERSISTENT, DataPoint.SENSOR_NAME, dataPoint);
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
                ContentValues[] selection = select(where, selectionArgs);

                // create new cursor with the query result
                MatrixCursor result = new MatrixCursor(projection);
                Object[] row = null;
                for (ContentValues dataPoint : selection) {
                    row = new Object[projection.length];
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
                Cursor persistentResult = db.query(TABLE_PERSISTENT, projection, where,
                        selectionArgs, null, null, sortOrder);
                return persistentResult;
            } catch (Exception e) {
                Log.e(TAG, "Failed to query the persisted data points", e);
            }

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    private synchronized ContentValues[] select(String where, String[] selectionArgs) {

        // try to parse the selection criteria
        List<String> sensorNames = ParserUtils.getSelectedSensors(storage.keySet(), where,
                selectionArgs);
        long[] timeRangeSelect = ParserUtils.getSelectedTimeRange(where, selectionArgs);
        int transmitStateSelect = ParserUtils.getSelectedTransmitState(where, selectionArgs);

        ContentValues[] selection = new ContentValues[50 * MAX_VOLATILE_VALUES], dataPoints;
        ContentValues dataPoint;
        long timestamp = 0;
        int count = 0, max = 0, transmitState = 0;
        for (String name : sensorNames) {
            dataPoints = storage.get(name);
            if (null != dataPoints) {
                max = pointers.get(name);
                for (int i = 0; i < max; i++) {
                    dataPoint = dataPoints[i];
                    timestamp = dataPoint.getAsLong(DataPoint.TIMESTAMP);
                    if (timestamp >= timeRangeSelect[0] && timestamp <= timeRangeSelect[1]) {
                        transmitState = dataPoint.getAsInteger(DataPoint.TRANSMIT_STATE);
                        if (transmitStateSelect == -1 || transmitState == transmitStateSelect) {
                            selection[count] = dataPoint;
                            count++;
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

        // copy selection to new array with proper length
        ContentValues[] result = new ContentValues[count];
        System.arraycopy(selection, 0, result, 0, count);

        return result;
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
        ContentValues[] selection = select(where, selectionArgs);

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
