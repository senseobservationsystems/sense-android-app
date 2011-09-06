/*
 ************************************************************************************************************
 *     Copyright (C)  2010 Sense Observation Systems, Rotterdam, the Netherlands.  All rights reserved.     *
 ************************************************************************************************************
 */
package nl.sense_os.service.phonestate;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

import nl.sense_os.service.MsgHandler;
import nl.sense_os.service.SenseDataTypes;
import nl.sense_os.service.SensorData.SensorNames;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SensePhoneState extends PhoneStateListener {

    private static final String TAG = "Sense PhoneStateListener";
    private final Context context;
    private final TelephonyManager telMgr;
    private Timer transmitTimer = new Timer();
    private boolean lastMsgIndicatorState;
    private boolean msgIndicatorUpdated = false;
    private String lastServiceState;
    private String lastSignalStrength;
    private String lastDataConnectionState;
    private String lastIp;
    private int previousConnectionType = -2; // used to detect changes in connection type

    public SensePhoneState(Context context) {
        super();
        this.context = context;
        telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * Transmits the latest phone state data in a new Thread.
     */
    private void transmitLatestState() {

        HandlerThread ht = new HandlerThread("Phone state transmission");
        ht.start();
        new Handler(ht.getLooper()) {

            @Override
            public void handleMessage(Message msg) {

                if (null != lastIp || null != lastDataConnectionState
                        || true == msgIndicatorUpdated || null != lastServiceState
                        || null != lastSignalStrength) {
                    // Log.v(TAG, "Transmit the latest phone state...");
                }

                // IP address
                if (null != lastIp) {
                    // Log.d(TAG, "Transmit IP address...");
                    Intent ipAddress = new Intent(MsgHandler.ACTION_NEW_MSG);
                    ipAddress.putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.IP_ADDRESS);
                    ipAddress.putExtra(MsgHandler.KEY_VALUE, lastIp);
                    ipAddress.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.STRING);
                    ipAddress.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(ipAddress);
                    lastIp = null;
                }

                // data connection state
                if (null != lastDataConnectionState) {
                    // Log.d(TAG, "Transmit data connection state...");
                    Intent dataConnection = new Intent(MsgHandler.ACTION_NEW_MSG);
                    dataConnection.putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.DATA_CONN);
                    dataConnection.putExtra(MsgHandler.KEY_VALUE, lastDataConnectionState);
                    dataConnection.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.STRING);
                    dataConnection.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(dataConnection);
                    lastDataConnectionState = null;
                }

                // message waiting indicator
                if (msgIndicatorUpdated) {
                    // Log.d(TAG, "Transmit unread messages indicator...");
                    Intent msgIndicator = new Intent(MsgHandler.ACTION_NEW_MSG);
                    msgIndicator.putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.UNREAD_MSG);
                    msgIndicator.putExtra(MsgHandler.KEY_VALUE, lastMsgIndicatorState);
                    msgIndicator.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.BOOL);
                    msgIndicator.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(msgIndicator);
                    msgIndicatorUpdated = false;
                }

                // service state
                if (null != lastServiceState) {
                    // Log.d(TAG, "Transmit service state...");
                    Intent serviceState = new Intent(MsgHandler.ACTION_NEW_MSG);
                    serviceState.putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.SERVICE_STATE);
                    serviceState.putExtra(MsgHandler.KEY_VALUE, lastServiceState);
                    serviceState.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.JSON);
                    serviceState.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(serviceState);
                    lastServiceState = null;
                }

                // signal strength
                if (null != lastSignalStrength) {
                    // Log.d(TAG, "Transmit signal strength...");
                    Intent signalStrength = new Intent(MsgHandler.ACTION_NEW_MSG);
                    signalStrength
                            .putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.SIGNAL_STRENGTH);
                    signalStrength.putExtra(MsgHandler.KEY_VALUE, lastSignalStrength);
                    signalStrength.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.JSON);
                    signalStrength.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(signalStrength);
                    lastSignalStrength = null;
                }

                getLooper().quit();
            }
        }.sendEmptyMessage(0);
    }

    /**
     * Starts sensing and schedules periodic transmission of the phone state.
     */
    public void startSensing(final int interval) {

        // start listening to the phone state
        telMgr.listen(this, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR
                | PhoneStateListener.LISTEN_CALL_STATE
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                | PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR
                | PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        if (null != transmitTimer) {
            transmitTimer.cancel();
        }
        transmitTimer = new Timer();

        transmitTimer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                transmitLatestState();
            }
        }, interval, interval);
    }

    /**
     * Stops listening and stops transmission of the phone state.
     */
    public void stopSensing() {

        telMgr.listen(this, PhoneStateListener.LISTEN_NONE);

        if (null != transmitTimer) {
            transmitTimer.cancel();
        }
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        // Log.d(TAG, "Call state changed...");

        JSONObject json = new JSONObject();
        try {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE :
                    json.put("state", "idle");
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK :
                    json.put("state", "calling");
                    break;
                case TelephonyManager.CALL_STATE_RINGING :
                    json.put("state", "ringing");
                    json.put("incomingNumber", incomingNumber);
                    break;
                default :
                    Log.e(TAG, "Unexpected call state: " + state);
                    return;
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException in onCallChanged", e);
            return;
        }

        // pass message immediately to the MsgHandler
        Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
        i.putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.CALL_STATE);
        i.putExtra(MsgHandler.KEY_VALUE, json.toString());
        i.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.JSON);
        i.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
        context.startService(i);
    }

    @Override
    public void onCellLocationChanged(CellLocation location) {
        // TODO: Catch listen cell location!
    }

    @Override
    public void onDataActivity(int direction) {
        // not used to prevent a loop
    }

    @Override
    public void onDataConnectionStateChanged(int state) {
        // Log.v(TAG, "Connection state changed...");

        String strState = "";
        switch (state) {
            case TelephonyManager.DATA_CONNECTED :
                // send the URL on which the phone can be reached
                String ip = "";
                try {
                    Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
                    while (nis.hasMoreElements()) {
                        NetworkInterface ni = nis.nextElement();
                        Enumeration<InetAddress> iis = ni.getInetAddresses();
                        while (iis.hasMoreElements()) {
                            InetAddress ia = iis.nextElement();
                            if (ni.getDisplayName().equalsIgnoreCase("rmnet0")) {
                                ip = ia.getHostAddress();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting my own IP:", e);
                }
                if (ip.length() > 1) {
                    lastIp = ip;
                }

                strState = "connected";

                break;
            case TelephonyManager.DATA_CONNECTING :
                strState = "connecting";
                break;
            case TelephonyManager.DATA_DISCONNECTED :
                strState = "disconnected";
                break;
            case TelephonyManager.DATA_SUSPENDED :
                strState = "suspended";
                break;
            default :
                Log.e(TAG, "Unexpected data connection state: " + state);
                return;
        }

        lastDataConnectionState = strState;

        // check network type
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo active = connectivityManager.getActiveNetworkInfo();
        String typeName;
        int type = -1;
        if (active == null)
            typeName = "none";
        else {
            typeName = active.getTypeName();
            type = active.getType();
        }

        // only send changes. Note that this method is also called when another part of the state
        // changed.
        if (previousConnectionType != type) {
            previousConnectionType = type;

            // pass message immediately to the MsgHandler
            Intent msg = new Intent(MsgHandler.ACTION_NEW_MSG);
            msg.putExtra(MsgHandler.KEY_SENSOR_NAME, SensorNames.CONN_TYPE);
            msg.putExtra(MsgHandler.KEY_SENSOR_DEVICE, SensorNames.CONN_TYPE);
            msg.putExtra(MsgHandler.KEY_VALUE, typeName);
            msg.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.STRING);
            msg.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
            context.startService(msg);
        }
    }

    @Override
    public void onMessageWaitingIndicatorChanged(boolean unreadMsgs) {
        // Log.v(TAG, "Message waiting changed...");
        lastMsgIndicatorState = unreadMsgs;
        msgIndicatorUpdated = true;
    }

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {

        JSONObject json = new JSONObject();
        try {
            switch (serviceState.getState()) {
                case ServiceState.STATE_EMERGENCY_ONLY :
                    json.put("state", "emergency calls only");
                    break;
                case ServiceState.STATE_IN_SERVICE :
                    json.put("state", "in service");
                    String number = ((TelephonyManager) context
                            .getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
                    json.put("phone number", number);
                    break;
                case ServiceState.STATE_OUT_OF_SERVICE :
                    json.put("state", "out of service");
                    break;
                case ServiceState.STATE_POWER_OFF :
                    json.put("state", "power off");
                    break;
            }

            json.put("manualSet", serviceState.getIsManualSelection() ? true : false);

        } catch (JSONException e) {
            Log.e(TAG, "JSONException in onServiceStateChanged", e);
            return;
        }

        lastServiceState = json.toString();
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {

        JSONObject json = new JSONObject();
        try {
            json.put("CDMA dBm", signalStrength.getCdmaDbm());
            json.put("EVDO dBm", signalStrength.getEvdoDbm());
            json.put("GSM signal strength", signalStrength.getGsmSignalStrength());
            json.put("GSM bit error rate", signalStrength.getGsmBitErrorRate());
        } catch (JSONException e) {
            Log.e(TAG, "JSONException in onSignalStrengthsChanged", e);
            return;
        }

        lastSignalStrength = json.toString();
    }
}
