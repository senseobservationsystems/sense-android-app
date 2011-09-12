package nl.sense_os.service;

import nl.sense_os.app.R;
import nl.sense_os.service.SensePrefs.Auth;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class ServiceStateHelper {

    /**
     * ID for the notification in the status bar. Used to cancel the notification.
     */
    public static final int NOTIF_ID = 1;

    @SuppressWarnings("unused")
    private static final String TAG = "Sense Service State";

    private final Context context;
    private boolean started, foreground, loggedIn, ambienceActive, devProxActive, externalActive,
            locationActive, motionActive, phoneStateActive, quizActive;

    public ServiceStateHelper(Context context) {
        this.context = context;
    }

    /**
     * @return the current status of the sensing modules
     */
    public int getStatusCode() {

        int status = 0;

        status = isStarted() ? SenseStatusCodes.RUNNING : status;
        status = isLoggedIn() ? status + SenseStatusCodes.CONNECTED : status;
        status = isPhoneStateActive() ? status + SenseStatusCodes.PHONESTATE : status;
        status = isLocationActive() ? status + SenseStatusCodes.LOCATION : status;
        status = isAmbienceActive() ? status + SenseStatusCodes.AMBIENCE : status;
        status = isQuizActive() ? status + SenseStatusCodes.QUIZ : status;
        status = isDevProxActive() ? status + SenseStatusCodes.DEVICE_PROX : status;
        status = isExternalActive() ? status + SenseStatusCodes.EXTERNAL : status;
        status = isMotionActive() ? status + SenseStatusCodes.MOTION : status;

        return status;
    }

    public boolean isAmbienceActive() {
        return ambienceActive;
    }

    public boolean isDevProxActive() {
        return devProxActive;
    }

    public boolean isExternalActive() {
        return externalActive;
    }

    public boolean isForeground() {
        return foreground;
    }

    public boolean isLocationActive() {
        return locationActive;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isMotionActive() {
        return motionActive;
    }

    public boolean isPhoneStateActive() {
        return phoneStateActive;
    }

    public boolean isQuizActive() {
        return quizActive;
    }

    public boolean isStarted() {
        return started;
    }

    public void setAmbienceActive(boolean active) {
        ambienceActive = active;
    }

    public void setDevProxActive(boolean active) {
        devProxActive = active;
    }

    public void setExternalActive(boolean active) {
        externalActive = active;
    }

    public void setForeground(boolean foreground) {
        if (foreground != isForeground()) {
            this.foreground = foreground;
            // Log.v(TAG, isForeground()
            // ? "Sense Platform Service is in foreground..."
            // : "Sense Platform Service is in background...");
            updateNotification();
        }
    }

    public void setLocationActive(boolean active) {
        locationActive = active;
    }

    public void setLoggedIn(boolean loggedIn) {
        if (loggedIn != isLoggedIn()) {
            this.loggedIn = loggedIn;
            // Log.v(TAG, isLoggedIn() ? "Sense Platform Service logged in..."
            // : "Sense Platform Service logged out...");
            updateNotification();
        }
    }

    public void setMotionActive(boolean active) {
        motionActive = active;
    }

    public void setPhoneStateActive(boolean active) {
        phoneStateActive = active;
    }

    public void setQuizActive(boolean active) {
        quizActive = active;
    }

    public void setStarted(boolean started) {
        if (started != isStarted()) {
            this.started = started;
            // Log.v(TAG, isStarted()
            // ? "Sense Platform Service started..."
            // : "Sense Platform Service stopped...");
            updateNotification();
        }
    }

    /**
     * Shows a status bar notification that the Sense service is active, also displaying the
     * username if the service is logged in.
     * 
     * @param loggedIn
     *            set to <code>true</code> if the service is logged in.
     */
    private void updateNotification() {
        NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (isForeground()) {
            nm.notify(NOTIF_ID, getStateNotification());
        } else {
            nm.cancel(NOTIF_ID);
        }
    }

    public Notification getStateNotification() {

        // icon and content text depend on the current state
        int icon = -1;
        final CharSequence contentTitle = "Sense Platform";
        CharSequence contentText = null;
        if (isStarted()) {
            if (isLoggedIn()) {
                icon = R.drawable.ic_status_sense;
                final SharedPreferences authPrefs = context.getSharedPreferences(
                        SensePrefs.AUTH_PREFS, Context.MODE_PRIVATE);
                String username = authPrefs.getString(Auth.LOGIN_USERNAME, "UNKNOWN");
                contentText = "Sensors active, logged in as '" + username + "'";
            } else {
                icon = R.drawable.ic_status_sense_alert;
                contentText = "Sensors active, no connection to CommonSense";
            }
        } else {
            if (isLoggedIn()) {
                icon = R.drawable.ic_status_sense_disabled;
                final SharedPreferences authPrefs = context.getSharedPreferences(
                        SensePrefs.AUTH_PREFS, Context.MODE_PRIVATE);
                String username = authPrefs.getString(Auth.LOGIN_USERNAME, "UNKNOWN");
                contentText = "Sensors inactive, logged in as '" + username + "'";
            } else {
                icon = R.drawable.ic_status_sense_disabled;
                contentText = "Sensors inactive, not logged in";
            }
        }

        // action to take when the notification is tapped
        final Intent notifIntent = new Intent("nl.sense_os.app.SenseApp");
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notifIntent, 0);

        // time of the notification
        final long when = System.currentTimeMillis();

        // create the notification
        Notification note = new Notification(icon, null, when);
        note.flags = Notification.FLAG_NO_CLEAR;
        note.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        return note;
    }
}