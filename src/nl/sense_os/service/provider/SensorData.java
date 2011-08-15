package nl.sense_os.service.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public class SensorData {

    /**
     * Columns for Cursors that represent a set of buffered data points for a certain sensor.
     */
    public static class BufferedData implements BaseColumns {

        private BufferedData() {
            // class should not be instantiated
        }

        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
                + "/vnd.sense_os.buffered_data";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
                + "/vnd.sense_os.buffered_data";

        public static final String SENSOR = "sensor";
        public static final String ACTIVE = "active";
        public static final String JSON = "json";
    }

    /**
     * Columns for Cursors that represent a sensor data point.
     */
    public static class DataPoint implements BaseColumns {

        private DataPoint() {
            // class should not be instantiated
        }

        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
                + "/vnd.sense_os.data_point";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
                + "/vnd.sense_os.data_point";
        public static final Uri CONTENT_URI = Uri.parse("content://" + LocalStorage.AUTHORITY
                + "/recent_values");

        public static final String SENSOR_NAME = "sensor_name";
        public static final String SENSOR_DESCRIPTION = "sensor_description";
        public static final String DATA_TYPE = "data_type";
        public static final String TIMESTAMP = "timestamp";
        public static final String VALUE = "value";
    }
}
