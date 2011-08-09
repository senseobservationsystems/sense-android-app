package nl.sense_os.service.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public class SensorData {

    public static class DataBuffer implements BaseColumns {

        private DataBuffer() {
            // class should not be instantiated
        }

        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
                + "/vnd.sense_os.data_buffer";
        public static final Uri CONTENT_URI = Uri.parse("content://" + LocalStorage.AUTHORITY
                + "/data_buffer");

        public static final String SENSOR = "sensor";
        public static final String ACTIVE = "active";
        public static final String JSON = "json";
    }

    public static class DataPoint implements BaseColumns {

        private DataPoint() {
            // class should not be instantiated
        }

        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
                + "/vnd.sense_os.data_point";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
                + "/vnd.sense_os.data_point";
        public static final Uri CONTENT_URI = Uri.parse("content://" + LocalStorage.AUTHORITY
                + "/data_point");

        public static final String SENSOR_NAME = "sensor_name";
        public static final String SENSOR_DESCRIPTION = "sensor_description";
        public static final String DATA_TYPE = "data_type";
        public static final String TIMESTAMP = "timestamp";
        public static final String VALUE = "value";
    }

}
