/*
 ************************************************************************************************************
 *     Copyright (C)  2010 Sense Observation Systems, Rotterdam, the Netherlands.  All rights reserved.     *
 ************************************************************************************************************
 */
package nl.sense_os.service.phonestate;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import nl.sense_os.service.Constants;
import nl.sense_os.service.MsgHandler;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SensePhoneState extends PhoneStateListener {

    private class PhoneStateUpdater extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            transmitLatestState();
        }
    }

    private static final String NAME_CALL = "call state";
    private static final String NAME_DATA = "data connection";
    private static final String NAME_IP = "ip address";
    private static final String NAME_SERVICE = "service state";
    private static final String NAME_SIGNAL = "signal strength";
    private static final String NAME_UNREAD = "unread msg";
    private static final String TAG = "Sense PhoneStateListener";
    private static final int REQID = 0xdeadbeef;
    private static final String ACTION_UPDATE_PHONESTATE = "nl.sense_os.service.UpdatePhoneState";
    private Context context;
    private PhoneStateUpdater updater = new PhoneStateUpdater();
    private boolean lastMsgIndicatorState;
    private String lastServiceState;
    private String lastSignalStrength;
    private String lastDataConnectionState;
    private String lastIp;

    public SensePhoneState(Context context) {
        super();
        this.context = context;
    }

    /**
     * Transmits the latest phone state data in a new Thread.
     */
    private void transmitLatestState() {

        new Thread(new Runnable() {

            @Override
            public void run() {

                Log.d(TAG, "Transmit phone state...");

                // IP address
                if (null != lastIp) {
                    Intent ipAddress = new Intent(MsgHandler.ACTION_NEW_MSG);
                    ipAddress.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_IP);
                    ipAddress.putExtra(MsgHandler.KEY_VALUE, lastIp);
                    ipAddress.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_STRING);
                    ipAddress.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(ipAddress);

                    lastIp = null;
                }

                // data connection state
                if (null != lastDataConnectionState) {
                    Intent dataConnection = new Intent(MsgHandler.ACTION_NEW_MSG);
                    dataConnection.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_DATA);
                    dataConnection.putExtra(MsgHandler.KEY_VALUE, lastDataConnectionState);
                    dataConnection.putExtra(MsgHandler.KEY_DATA_TYPE,
                            Constants.SENSOR_DATA_TYPE_STRING);
                    dataConnection.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(dataConnection);

                    lastDataConnectionState = null;
                }

                // message waiting indicator
                Intent msgIndicator = new Intent(MsgHandler.ACTION_NEW_MSG);
                msgIndicator.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_UNREAD);
                msgIndicator.putExtra(MsgHandler.KEY_VALUE, lastMsgIndicatorState);
                msgIndicator.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_BOOL);
                msgIndicator.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                context.startService(msgIndicator);
                lastMsgIndicatorState = false;

                // service state
                if (null != lastServiceState) {
                    Intent serviceState = new Intent(MsgHandler.ACTION_NEW_MSG);
                    serviceState.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_SERVICE);
                    serviceState.putExtra(MsgHandler.KEY_VALUE, lastServiceState);
                    serviceState
                            .putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_JSON);
                    serviceState.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(serviceState);

                    lastServiceState = null;
                }

                // signal strength
                if (null != lastSignalStrength) {
                    Intent signalStrength = new Intent(MsgHandler.ACTION_NEW_MSG);
                    signalStrength.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_SIGNAL);
                    signalStrength.putExtra(MsgHandler.KEY_VALUE, lastSignalStrength);
                    signalStrength.putExtra(MsgHandler.KEY_DATA_TYPE,
                            Constants.SENSOR_DATA_TYPE_JSON);
                    signalStrength.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                    context.startService(signalStrength);

                    lastSignalStrength = null;
                }
            }
        }).run();
    }

    /**
     * Starts periodic transmission of the phone state.
     */
    public void startSensing(int interval) {

        // register receiver for the alarms
        context.registerReceiver(updater, new IntentFilter(ACTION_UPDATE_PHONESTATE));

        // set periodic alarm to trigger transmission
        Intent alarm = new Intent(ACTION_UPDATE_PHONESTATE);
        PendingIntent operation = PendingIntent.getBroadcast(context, REQID, alarm, 0);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.cancel(operation);
        mgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15000,
                interval, operation);
    }

    /**
     * Stops transmission of the phone state.
     */
    public void stopSensing() {

        // stop the transmit alarms
        Intent alarm = new Intent(ACTION_UPDATE_PHONESTATE);
        PendingIntent operation = PendingIntent.getBroadcast(context, REQID, alarm, 0);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.cancel(operation);

        // unregister receiver
        try {
            context.unregisterReceiver(updater);
        } catch (IllegalArgumentException e) {
            // receiver was not registered
        }
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {

        JSONObject json = new JSONObject();
        try {
            switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                json.put("state", "idle");
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                json.put("state", "calling");
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                json.put("state", "ringing");
                json.put("incomingNumber", incomingNumber);
                break;
            default:
                Log.e(TAG, "Unexpected call state: " + state);
                return;
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException in onCallChanged", e);
            return;
        }

        // pass message immediately to the MsgHandler
        Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
        i.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_CALL);
        i.putExtra(MsgHandler.KEY_VALUE, json.toString());
        i.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_JSON);
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

        String strState = "";
        switch (state) {
        case TelephonyManager.DATA_CONNECTED:
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
        case TelephonyManager.DATA_CONNECTING:
            strState = "connecting";
            break;
        case TelephonyManager.DATA_DISCONNECTED:
            strState = "disconnected";
            break;
        case TelephonyManager.DATA_SUSPENDED:
            strState = "suspended";
            break;
        default:
            Log.e(TAG, "Unexpected data connection state: " + state);
            return;
        }

        lastDataConnectionState = strState;
    }

    @Override
    public void onMessageWaitingIndicatorChanged(boolean unreadMsgs) {
        lastMsgIndicatorState = unreadMsgs;
    }

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {

        JSONObject json = new JSONObject();
        try {
            switch (serviceState.getState()) {
            case ServiceState.STATE_EMERGENCY_ONLY:
                json.put("state", "emergency calls only");
                break;
            case ServiceState.STATE_IN_SERVICE:
                json.put("state", "in service");
                String number = ((TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
                json.put("phone number", number);
                break;
            case ServiceState.STATE_OUT_OF_SERVICE:
                json.put("state", "out of service");
                break;
            case ServiceState.STATE_POWER_OFF:
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