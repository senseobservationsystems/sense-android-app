package nl.sense_os.service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SenseApi {

    private static final String TAG = "SenseApi";
    private static final boolean USE_COMPRESSION = false;
    private static final long CACHE_REFRESH = 1000l * 60 * 60; // 1 hour

    /**
     * Gets the current device ID for use with CommonSense. The device ID is cached in the
     * preferences if it was fetched earlier.
     * 
     * @param context
     *            Context for getting preferences
     * @return the device ID
     */
    public static int getDeviceId(Context context) {
        try {
            // try to get the device ID from the preferences
            final SharedPreferences authPrefs = context.getSharedPreferences(Constants.AUTH_PREFS,
                    Context.MODE_PRIVATE);
            int cachedId = authPrefs.getInt(Constants.PREF_DEVICE_ID, -1);
            long cacheTime = authPrefs.getLong(Constants.PREF_DEVICE_ID_TIME, 0);
            boolean isOutdated = System.currentTimeMillis() - cacheTime > CACHE_REFRESH;
            if (cachedId != -1 && false == isOutdated) {
                return cachedId;
            }

            Log.v(TAG, "Device ID is missing or outdated, refreshing...");

            // Store phone type and IMEI. These are used to uniquely identify this device
            final TelephonyManager telMgr = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            final String imei = telMgr.getDeviceId();
            final Editor editor = authPrefs.edit();
            editor.putString(Constants.PREF_PHONE_IMEI, imei);
            editor.putString(Constants.PREF_PHONE_TYPE, Build.MODEL);
            editor.commit();

            // get list of devices that are already registered at CommonSense for this user
            boolean devMode = authPrefs.getBoolean(Constants.PREF_DEV_MODE, false);
            final URI uri = new URI(devMode ? Constants.URL_DEV_DEVICES : Constants.URL_DEVICES);
            String cookie = authPrefs.getString(Constants.PREF_LOGIN_COOKIE, "");
            JSONObject response = SenseApi.getJsonObject(uri, cookie);

            // check if this device is in the list
            if (response != null) {
                JSONArray deviceList = response.getJSONArray("devices");
                if (deviceList != null) {
                    for (int x = 0; x < deviceList.length(); x++) {

                        JSONObject device = deviceList.getJSONObject(x);
                        if (device != null) {
                            String uuid = device.getString("uuid");
                            // Found the right device if UUID matches IMEI
                            // found an error when an imei starts with a
                            if (uuid.equalsIgnoreCase(imei)) {

                                // cache device ID in preferences
                                cachedId = Integer.parseInt(device.getString("id"));
                                editor.putString(Constants.PREF_DEVICE_TYPE,
                                        device.getString("type"));
                                editor.putInt(Constants.PREF_DEVICE_ID, cachedId);
                                editor.putLong(Constants.PREF_DEVICE_ID_TIME,
                                        System.currentTimeMillis());
                                editor.remove(Constants.PREF_SENSOR_LIST);
                                editor.commit();
                                return cachedId;
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Retrieved device list from commonSense is empty");
                }
            } else
                return -2; // error return -2
        } catch (Exception e) {
            Log.e(TAG, "Exception determining device ID: " + e.getMessage());
            return -2; // error return -2
        }
        return -1; // not found return -1
    }

    /**
     * @return a JSONObject from the requested URI
     */
    public static JSONObject getJsonObject(URI uri, String cookie) {
        try {
            final HttpGet get = new HttpGet(uri);
            get.setHeader("Cookie", cookie);
            final HttpClient client = new DefaultHttpClient();

            // client.getConnectionManager().closeIdleConnections(2, TimeUnit.SECONDS);
            final HttpResponse response = client.execute(get);
            if (response == null)
                return null;
            if (response.getStatusLine().getStatusCode() != 200) {
                Log.e(TAG, "Error receiving content for " + uri.toString() + ". Status code: "
                        + response.getStatusLine().getStatusCode());
                return null;
            }

            HttpEntity entity = response.getEntity();
            InputStream is = entity.getContent();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is), 1024);
            String line;
            StringBuffer responseString = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                responseString.append(line);
                responseString.append('\r');
            }
            rd.close();
            return new JSONObject(responseString.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error receiving content for " + uri.toString() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets a list of all registered sensors for this device, and stores it in the preferences.
     * 
     * @param context
     *            Context for getting preferences
     * @see {@link Constants#PREF_SENSOR_LIST}
     */
    public static JSONArray getRegisteredSensors(Context context) {
        try {
            // get device ID to use in communication with CommonSense
            int deviceId = getDeviceId(context);
            if (deviceId == -1) { // -1 no device id found, create new one
                Log.e(TAG, "Cannot get list of sensors: device ID is unknown.");
                return new JSONArray("[]");
            } else if (deviceId == -2) // -2 error retrieving info from commonSense
            {
                Log.e(TAG, "Connection error while retrieving device ID from commonSense");
                return null;
            }

            // check cache retention time for the list of sensors
            final SharedPreferences authPrefs = context.getSharedPreferences(Constants.AUTH_PREFS,
                    Context.MODE_PRIVATE);
            String cachedSensors = authPrefs.getString(Constants.PREF_SENSOR_LIST, null);
            long cacheTime = authPrefs.getLong(Constants.PREF_SENSOR_LIST_TIME, 0);
            boolean isOutdated = System.currentTimeMillis() - cacheTime > CACHE_REFRESH;

            // return cached list of it is still valid
            if (false == isOutdated && null != cachedSensors) {
                return new JSONArray(cachedSensors);
            }

            Log.v(TAG, "List of sensor IDs is missing or outdated, refreshing...");

            // get fresh list of sensors for this device from CommonSense
            String cookie = authPrefs.getString(Constants.PREF_LOGIN_COOKIE, "NO_COOKIE");
            boolean devMode = authPrefs.getBoolean(Constants.PREF_DEV_MODE, false);
            String rawUrl = devMode ? Constants.URL_DEV_SENSORS : Constants.URL_SENSORS;
            URI uri = new URI(rawUrl.replaceAll("<id>", "" + deviceId));
            JSONObject response = SenseApi.getJsonObject(uri, cookie);

            // parse response and store the list
            if (response != null) {
                JSONArray sensorList = response.getJSONArray("sensors");
                if (sensorList != null) {
                    Editor authEditor = authPrefs.edit();
                    authEditor.putString(Constants.PREF_SENSOR_LIST, sensorList.toString());
                    authEditor.putLong(Constants.PREF_SENSOR_LIST_TIME, System.currentTimeMillis());
                    authEditor.commit();
                }
                return sensorList;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in retrieving registered sensors: ", e);
        }
        return null;
    }

    /**
     * This method returns the url to which the data must be send, it does this based on the sensor
     * name and device_type. If the sensor cannot be found, then it will be created
     * 
     * TODO: create a HashMap to search the sensor in, can we keep this in mem of the service?
     */
    public static String getSensorUrl(Context context, String sensorName, String sensorValue,
            String dataType, String deviceType) {
        try {

            final SharedPreferences authPrefs = context.getSharedPreferences(Constants.AUTH_PREFS,
                    Context.MODE_PRIVATE);
            final boolean devMode = authPrefs.getBoolean(Constants.PREF_DEV_MODE, false);

            JSONArray sensors = getRegisteredSensors(context);

            if (null != sensors) {

                // check all the sensors in the list
                for (int x = 0; x < sensors.length(); x++) {
                    JSONObject sensor = (JSONObject) sensors.get(x);

                    if (sensor.getString("device_type").equalsIgnoreCase(deviceType)
                            && sensor.getString("name").equalsIgnoreCase(sensorName)) {

                        // found the right sensor
                        if (dataType.equals(Constants.SENSOR_DATA_TYPE_FILE)) {
                            String rawUrl = devMode ? Constants.URL_DEV_SENSOR_FILE
                                    : Constants.URL_SENSOR_FILE;
                            return rawUrl.replaceFirst("<id>", sensor.getString("id"));
                        } else {
                            String rawUrl = devMode ? Constants.URL_DEV_SENSOR_DATA
                                    : Constants.URL_SENSOR_DATA;
                            return rawUrl.replaceFirst("<id>", sensor.getString("id"));
                        }
                    }
                }
            } else {
                // failed to check commonsense for the sensor ID, give up
                return null;
            }

            /* Sensor not found in current list of sensors, create it at CommonSense */

            // prepare request to create new sensor
            URL url = new URL(devMode ? Constants.URL_DEV_CREATE_SENSOR
                    : Constants.URL_CREATE_SENSOR);
            JSONObject postData = new JSONObject();
            JSONObject sensor = new JSONObject();
            sensor.put("name", sensorName);
            sensor.put("device_type", deviceType);
            sensor.put("pager_type", "");
            sensor.put("data_type", dataType);
            if (dataType.compareToIgnoreCase("json") == 0) {
                JSONObject dataStructJSon = new JSONObject(sensorValue);
                JSONArray names = dataStructJSon.names();
                for (int x = 0; x < names.length(); x++) {
                    String name = names.getString(x);
                    int start = dataStructJSon.get(name).getClass().getName().lastIndexOf(".");
                    dataStructJSon.put(name, dataStructJSon.get(name).getClass().getName()
                            .substring(start + 1));
                }
                sensor.put("data_structure", dataStructJSon.toString().replaceAll("\"", "\\\""));
            }
            postData.put("sensor", sensor);

            String cookie = authPrefs.getString(Constants.PREF_LOGIN_COOKIE, "");

            // check if sensor was created successfully
            HashMap<String, String> response = sendJson(url, postData, "POST", cookie);
            if (response == null) {
                // failed to create the sensor
                Log.e(TAG, "Error creating sensor. response=null");
                return null;
            }
            if (response.get("http response code").compareToIgnoreCase("201") != 0) {
                String code = response.get("http response code");
                Log.e(TAG, "Error creating sensor. Got response code: " + code);
                return null;
            }

            // store sensor URL in the preferences
            String content = response.get("content");
            JSONObject responseJson = new JSONObject(content);
            JSONObject JSONSensor = responseJson.getJSONObject("sensor");
            sensors.put(JSONSensor);
            Editor authEditor = authPrefs.edit();
            authEditor.putString(Constants.PREF_SENSOR_LIST, sensors.toString());
            authEditor.commit();

            Log.v(TAG, "Created sensor: \'" + sensorName + "\'");

            // Add sensor to this device at CommonSense
            String phoneType = authPrefs.getString(Constants.PREF_PHONE_TYPE, "smartphone");
            String rawUrl = devMode ? Constants.URL_DEV_ADD_SENSOR_TO_DEVICE
                    : Constants.URL_ADD_SENSOR_TO_DEVICE;
            url = new URL(rawUrl.replaceFirst("<id>", (String) JSONSensor.get("id")));
            postData = new JSONObject();
            JSONObject device = new JSONObject();
            device.put("type", authPrefs.getString(Constants.PREF_DEVICE_TYPE, phoneType));

            device.put("uuid", authPrefs.getString(Constants.PREF_PHONE_IMEI, "0000000000"));
            postData.put("device", device);

            response = sendJson(url, postData, "POST", cookie);
            if (response == null) {
                // failed to add the sensor to the device
                Log.e(TAG, "Error adding sensor to device. response=null");
                return null;
            }
            if (response.get("http response code").compareToIgnoreCase("201") != 0) {
                String code = response.get("http response code");
                Log.e(TAG, "Error adding sensor to device. Got response code: " + code);
                return null;
            }

            if (dataType.equals(Constants.SENSOR_DATA_TYPE_FILE)) {
                rawUrl = devMode ? Constants.URL_DEV_SENSOR_FILE : Constants.URL_SENSOR_FILE;
                return rawUrl.replaceFirst("<id>", (String) JSONSensor.get("id"));
            } else {
                rawUrl = devMode ? Constants.URL_DEV_SENSOR_DATA : Constants.URL_SENSOR_DATA;
                return rawUrl.replaceFirst("<id>", (String) JSONSensor.get("id"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in retrieving the right sensor URL: " + e.getMessage());
            return null;
        }
    }

    /**
     * @param hashMe
     *            "clear" password String to be hashed before sending it to CommonSense
     * @return hashed String
     */
    public static String hashPassword(String hashMe) {
        final byte[] unhashedBytes = hashMe.getBytes();
        try {
            final MessageDigest algorithm = MessageDigest.getInstance("MD5");
            algorithm.reset();
            algorithm.update(unhashedBytes);
            final byte[] hashedBytes = algorithm.digest();

            final StringBuffer hexString = new StringBuffer();
            for (final byte element : hashedBytes) {
                final String hex = Integer.toHexString(0xFF & element);
                if (hex.length() == 1) {
                    hexString.append(0);
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (final NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tries to log in at CommonSense using the supplied username and password. After login, the
     * cookie containing the session ID is stored in the preferences.
     * 
     * @param context
     *            Context for getting preferences
     * @param username
     *            username for login
     * @param pass
     *            hashed password for login
     * @return 0 if login completed successfully, -2 if login was forbidden, and -1 for any other
     *         errors.
     */
    public static int login(Context context, String username, String pass) {
        try {
            final SharedPreferences authPrefs = context.getSharedPreferences(Constants.AUTH_PREFS,
                    Context.MODE_PRIVATE);
            final boolean devMode = authPrefs.getBoolean(Constants.PREF_DEV_MODE, false);

            final URL url = new URL(devMode ? Constants.URL_DEV_LOGIN : Constants.URL_LOGIN);
            final JSONObject user = new JSONObject();
            user.put("username", username);
            user.put("password", pass);
            final HashMap<String, String> response = sendJson(url, user, "POST", "");
            if (response == null) {
                // request failed
                return -1;
            }

            final Editor authEditor = authPrefs.edit();

            // if response code is not 200 (OK), the login was incorrect
            String responseCode = response.get("http response code");
            if ("403".equalsIgnoreCase(responseCode)) {
                Log.e(TAG, "CommonSense login refused! Response: forbidden!");
                authEditor.remove(Constants.PREF_LOGIN_COOKIE);
                authEditor.commit();
                return -2;
            } else if (!"200".equalsIgnoreCase(responseCode)) {
                Log.e(TAG, "CommonSense login failed! Response: " + responseCode);
                authEditor.remove(Constants.PREF_LOGIN_COOKIE);
                authEditor.commit();
                return -1;
            }

            // if no cookie was returned, something went horribly wrong
            if (response.get("set-cookie") == null) {
                // incorrect login
                Log.e(TAG, "CommonSense login failed: no cookie received.");
                authEditor.remove(Constants.PREF_LOGIN_COOKIE);
                authEditor.commit();
                return -1;
            }

            // store cookie in the preferences
            String cookie = response.get("set-cookie");
            Log.v(TAG, "CommonSense login OK!");
            authEditor.putString(Constants.PREF_LOGIN_COOKIE, cookie);
            authEditor.commit();

            return 0;

        } catch (Exception e) {
            Log.e(TAG, "Exception during login: " + e.getMessage());

            final SharedPreferences authPrefs = context.getSharedPreferences(Constants.AUTH_PREFS,
                    Context.MODE_PRIVATE);
            final Editor editor = authPrefs.edit();
            editor.remove(Constants.PREF_LOGIN_COOKIE);
            editor.commit();
            return -1;
        }
    }

    /**
     * Tries to register a new user at CommonSense. Discards private data of any previous users.
     * 
     * @param context
     *            Context for getting preferences
     * @param username
     *            username to register
     * @param pass
     *            hashed password for the new user
     * @return 0 if registration completed successfully, -2 if the user already exists, and -1
     *         otherwise.
     */
    public static int register(Context context, String username, String pass) {

        // clear cached settings of the previous user
        final SharedPreferences authPrefs = context.getSharedPreferences(Constants.AUTH_PREFS,
                Context.MODE_PRIVATE);
        final Editor authEditor = authPrefs.edit();
        authEditor.remove(Constants.PREF_DEVICE_ID);
        authEditor.remove(Constants.PREF_DEVICE_TYPE);
        authEditor.remove(Constants.PREF_LOGIN_COOKIE);
        authEditor.remove(Constants.PREF_SENSOR_LIST);
        authEditor.commit();

        try {
            final boolean devMode = authPrefs.getBoolean(Constants.PREF_DEV_MODE, false);

            final URL url = new URL(devMode ? Constants.URL_DEV_REG : Constants.URL_REG);
            final JSONObject data = new JSONObject();
            final JSONObject user = new JSONObject();
            user.put("username", username);
            user.put("password", pass);
            user.put("email", username);
            data.put("user", user);
            final HashMap<String, String> response = SenseApi.sendJson(url, data, "POST", "");
            if (response == null) {
                Log.e(TAG, "Error registering new user. response=null");
                return -1;
            }
            String responseCode = response.get("http response code");
            if ("201".equalsIgnoreCase(responseCode)) {
                Log.v(TAG, "CommonSense registration successful");
            } else if ("409".equalsIgnoreCase(responseCode)) {
                Log.e(TAG, "Error registering new user! User already exists");
                return -2;
            } else {
                Log.e(TAG, "Error registering new user! Response code: " + responseCode);
                return -1;
            }
        } catch (final IOException e) {
            Log.e(TAG, "IOException during registration!", e);
            return -1;
        } catch (final IllegalAccessError e) {
            Log.e(TAG, "IllegalAccessError during registration!", e);
            return -1;
        } catch (JSONException e) {
            Log.e(TAG, "JSONException during registration!", e);
            return -1;
        } catch (Exception e) {
            Log.e(TAG, "Exception during registration!", e);
            return -1;
        }
        return 0;
    }

    /**
     * This method sends a JSON object to update or create an item it returns the HTTP-response code
     */
    public static HashMap<String, String> sendJson(URL url, JSONObject json, String method,
            String cookie) {
        HttpURLConnection urlConn = null;
        try {
            // Log.d(TAG, "Sending:" + url.toString());

            // Open New URL connection channel.
            urlConn = (HttpURLConnection) url.openConnection();

            // set post request
            urlConn.setRequestMethod(method);

            // Let the run-time system (RTS) know that we want input.
            urlConn.setDoInput(true);

            // we want to do output.
            urlConn.setDoOutput(true);

            // We want no caching
            urlConn.setUseCaches(false);

            // Set content type
            urlConn.setRequestProperty("Content-Type", "application/json");

            urlConn.setInstanceFollowRedirects(false);

            // Set cookie
            urlConn.setRequestProperty("Cookie", cookie);

            // Send POST output.
            DataOutputStream printout;

            // String testData = "username=epi&password=d0f92a90d5500f1d5c4136966c5c7e63"
            // Set compression
            if (USE_COMPRESSION) {
                // Don't Set content size
                urlConn.setRequestProperty("Transfer-Encoding", "chunked");
                urlConn.setRequestProperty("Content-Encoding", "gzip");
                GZIPOutputStream zipStream = new GZIPOutputStream(urlConn.getOutputStream());
                printout = new DataOutputStream(zipStream);
            } else {
                // Set content size
                urlConn.setFixedLengthStreamingMode(json.toString().length());
                urlConn.setRequestProperty("Content-Length", "" + json.toString().length());
                printout = new DataOutputStream(urlConn.getOutputStream());
            }

            printout.writeBytes(json.toString());
            printout.flush();
            printout.close();

            // Get Response
            HashMap<String, String> response = new HashMap<String, String>();
            int responseCode = urlConn.getResponseCode();
            response.put("http response code", "" + urlConn.getResponseCode());

            // content is only available for 2xx requests
            if (200 <= responseCode && 300 > responseCode) {
                InputStream is = urlConn.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is), 1024);
                String line;
                StringBuffer responseString = new StringBuffer();
                while ((line = rd.readLine()) != null) {
                    responseString.append(line);
                    responseString.append('\r');
                }
                rd.close();
                response.put("content", responseString.toString());
            }

            // read header fields
            Map<String, List<String>> headerFields = urlConn.getHeaderFields();
            for (Entry<String, List<String>> entry : headerFields.entrySet()) {
                String key = entry.getKey();
                List<String> value = entry.getValue();
                if (null != key && null != value) {
                    key = key.toLowerCase();
                    String valueString = value.toString();
                    valueString = valueString.substring(1, valueString.length() - 1);
                    // Log.d(TAG, "Header field '" + key + "': '" + valueString + "'");
                    response.put(key, valueString);
                } else {
                    // Log.d(TAG, "Skipped header field '" + key + "': '" + value + "'");
                }
            }
            return response;

        } catch (Exception e) {
            if (null == e.getMessage()) {
                Log.e(TAG, "Error in posting JSON: " + json.toString(), e);
            } else {
                // less verbose output
                Log.e(TAG, "Error in posting JSON: " + json.toString() + "\n" + e.getMessage());
            }
            return null;
        } finally {

            if (urlConn != null) {
                urlConn.disconnect();
            }
        }
    }
}
