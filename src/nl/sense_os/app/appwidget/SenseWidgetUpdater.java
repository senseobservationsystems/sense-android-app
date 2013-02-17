package nl.sense_os.app.appwidget;

import nl.sense_os.app.R;
import nl.sense_os.service.ISenseServiceCallback;
import nl.sense_os.service.SenseService.SenseBinder;
import nl.sense_os.service.SenseServiceStub;
import nl.sense_os.service.constants.SensePrefs.Main;
import nl.sense_os.service.constants.SenseStatusCodes;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.RemoteViews;

public class SenseWidgetUpdater extends IntentService {

    /**
     * Service stub for callbacks from the Sense service.
     */
    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void statusReport(int status) {
            updateWidgets(status);
        }

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            // not used
        }

        @Override
        public void onRegisterResult(int result) throws RemoteException {
            // not used
        }
    }

    /**
     * Service connection to handle connection with the Sense service. Manages the
     * <code>service</code> field when the service is connected or disconnected.
     */
    private class SenseServiceConn implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            // Log.v(TAG, "Bound to Sense Platform service...");
            service = ((SenseBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // Log.v(TAG, "Sense Platform service disconnected...");
            service = null;
            isBoundOrBinding = false;
            checkServiceStatus();
        }
    }

    private static final String TAG = "Sense Widget Updater";

    private final ISenseServiceCallback callback = new SenseCallback();

    private boolean isBoundOrBinding;
    private SenseServiceStub service;
    private final ServiceConnection serviceConn = new SenseServiceConn();

    public SenseWidgetUpdater() {
        super(TAG);
    }

    /**
     * Binds to the Sense Service, creating it if necessary.
     */
    private void bindToSenseService() {

        // start the service if it was not running already
        if (!isBoundOrBinding) {
            // Log.v(TAG, "Try to bind to Sense Platform service");
            final Intent serviceIntent = new Intent(getString(R.string.action_sense_service));
            isBoundOrBinding = bindService(serviceIntent, serviceConn, 0);
        } else {
            // already bound
        }
    }

    /**
     * Calls {@link ISenseService#getStatus(ISenseServiceCallback)} on the service. This will
     * generate a callback that updates the buttons ToggleButtons showing the service's state.
     */
    private void checkServiceStatus() {

        // wait until the service is bound
        int counter = 0;
        while (null == service && counter < 5) {
            try {
                Thread.sleep(20);
                counter++;
            } catch (InterruptedException e) {
                break;
            }
        }

        // Log.v(TAG, "Checking service status..");
        if (null != service) {
            try {
                // request status report
                service.getStatus(callback);
            } catch (final RemoteException e) {
                Log.e(TAG, "Error checking service status. ", e);
            }
        } else {
            // Log.v(TAG, "Not bound to Sense Platform service! Assume it's not running...");
            updateWidgets(0);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Log.v(TAG, "Creating...");
        bindToSenseService();
    }

    @Override
    public void onDestroy() {
        // Log.v(TAG, "Destroying...");
        unbindFromSenseService();
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (getString(R.string.action_widget_update).equals(action)) {
            checkServiceStatus();
        } else if (getString(R.string.action_widget_start_phone_state).equals(action)) {
            setPhoneState(true);
        } else if (getString(R.string.action_widget_stop_phone_state).equals(action)) {
            setPhoneState(false);
        } else if (getString(R.string.action_widget_start_location).equals(action)) {
            setLocation(true);
        } else if (getString(R.string.action_widget_stop_location).equals(action)) {
            setLocation(false);
        } else if (getString(R.string.action_widget_start_motion).equals(action)) {
            setMotion(true);
        } else if (getString(R.string.action_widget_stop_motion).equals(action)) {
            setMotion(false);
        } else if (getString(R.string.action_widget_start_ambience).equals(action)) {
            setAmbience(true);
        } else if (getString(R.string.action_widget_stop_ambience).equals(action)) {
            setAmbience(false);
        } else if (getString(R.string.action_widget_start_devices).equals(action)) {
            setDevices(true);
        } else if (getString(R.string.action_widget_stop_devices).equals(action)) {
            setDevices(false);
        } else {
            Log.w(TAG, "Unexpected intent action: " + action);
        }
    }

    private void setAmbience(boolean active) {

        // wait until the service is bound
        int counter = 0;
        while (null == service && counter < 5) {
            try {
                Thread.sleep(20);
                counter++;
            } catch (InterruptedException e) {
                break;
            }
        }

        // Log.d(TAG, "Set ambience: " + active);
        if (null != service) {
            // request status report
            service.toggleAmbience(active);
        } else {
            Log.w(TAG, "Cannot set ambience sensor status! Failed to bind to Sense service!");
        }
    }

    private void setDevices(boolean active) {

        // wait until the service is bound
        int counter = 0;
        while (null == service && counter < 5) {
            try {
                Thread.sleep(20);
                counter++;
            } catch (InterruptedException e) {
                break;
            }
        }

        // Log.d(TAG, "Set devices: " + active);
        if (null != service) {
            // request status report
            service.toggleDeviceProx(active);
        } else {
            Log.w(TAG,
                    "Cannot set device proximity sensor status! Failed to bind to Sense service!");
        }
    }

    private void setLocation(boolean active) {

        // wait until the service is bound
        int counter = 0;
        while (null == service && counter < 5) {
            try {
                Thread.sleep(20);
                counter++;
            } catch (InterruptedException e) {
                break;
            }
        }

        // Log.d(TAG, "Set location: " + active);
        if (null != service) {
            // request status report
            service.toggleLocation(active);
        } else {
            Log.w(TAG, "Cannot set location sensor status! Failed to bind to Sense service!");
        }
    }

    private void setMotion(boolean active) {

        // wait until the service is bound
        int counter = 0;
        while (null == service && counter < 5) {
            try {
                Thread.sleep(20);
                counter++;
            } catch (InterruptedException e) {
                break;
            }
        }

        // Log.d(TAG, "Set motion: " + active);
        if (null != service) {
            // request status report
            service.toggleMotion(active);
        } else {
            Log.w(TAG, "Cannot set motion sensor status! Failed to bind to Sense service!");
        }
    }

    private void setPhoneState(boolean active) {

        // wait until the service is bound
        int counter = 0;
        while (null == service && counter < 5) {
            try {
                Thread.sleep(20);
                counter++;
            } catch (InterruptedException e) {
                break;
            }
        }

        // Log.d(TAG, "Set phone state: " + active);
        if (null != service) {
            // request status report
            service.togglePhoneState(active);
        } else {
            Log.w(TAG, "Cannot set phone state sensor status! Failed to bind to Sense service!");
        }
    }

    /**
     * Unbinds from the Sense service, resets {@link #service} and {@link #isBoundOrBinding}.
     */
    private void unbindFromSenseService() {

        if ((true == isBoundOrBinding) && (null != serviceConn)) {
            // Log.v(TAG, "Unbind from Sense Platform service");
            unbindService(serviceConn);
        } else {
            // already unbound
        }
        service = null;
        isBoundOrBinding = false;
    }

    private void updateSensorViews(RemoteViews views, int status) {
        /* phone state */
        boolean active = ((status & SenseStatusCodes.PHONESTATE) > 0);
        views.setImageViewResource(R.id.widget_phone_state_btn,
                active ? R.drawable.wi_pst_on_selector : R.drawable.wi_pst_off_selector);

        Intent intent = new Intent(active ? getString(R.string.action_widget_stop_phone_state)
                : getString(R.string.action_widget_start_phone_state));
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_phone_state_btn, pendingIntent);

        /* location */
        active = ((status & SenseStatusCodes.LOCATION) > 0);
        views.setImageViewResource(R.id.widget_location_btn, active ? R.drawable.wi_loc_on_selector
                : R.drawable.wi_loc_off_selector);

        intent = new Intent(active ? getString(R.string.action_widget_stop_location)
                : getString(R.string.action_widget_start_location));
        pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_location_btn, pendingIntent);

        /* motion */
        active = ((status & SenseStatusCodes.MOTION) > 0);
        views.setImageViewResource(R.id.widget_motion_btn, active ? R.drawable.wi_mot_on_selector
                : R.drawable.wi_mot_off_selector);

        intent = new Intent(active ? getString(R.string.action_widget_stop_motion)
                : getString(R.string.action_widget_start_motion));
        pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_motion_btn, pendingIntent);

        /* ambience */
        active = ((status & SenseStatusCodes.AMBIENCE) > 0);
        views.setImageViewResource(R.id.widget_ambience_btn, active ? R.drawable.wi_amb_on_selector
                : R.drawable.wi_amb_off_selector);

        intent = new Intent(active ? getString(R.string.action_widget_stop_ambience)
                : getString(R.string.action_widget_start_ambience));
        pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_ambience_btn, pendingIntent);

        /* devices */
        active = ((status & SenseStatusCodes.DEVICE_PROX) > 0);
        views.setImageViewResource(R.id.widget_devices_btn, active ? R.drawable.wi_dev_on_selector
                : R.drawable.wi_dev_off_selector);

        intent = new Intent(active ? getString(R.string.action_widget_stop_devices)
                : getString(R.string.action_widget_start_devices));
        pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_devices_btn, pendingIntent);
    }

    private void updateWidgets(int status) {

        // get the sync and sample rate from the service
        int sampleRate = 10, syncRate = 10;
        if (status != 0) {
            try {
                sampleRate = Integer.parseInt(service.getPrefString(Main.SAMPLE_RATE, "0"));
                syncRate = Integer.parseInt(service.getPrefString(Main.SYNC_RATE, "0"));
            } catch (RemoteException e) {
                Log.w(TAG, "Could not fetch sync or sample rate preference from the service!");
                return;
            }
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, SenseWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(provider);

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);

            updateSensorViews(views, status);
            updateSampleSyncViews(views, sampleRate, syncRate);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void updateSampleSyncViews(RemoteViews views, int sampleRate, int syncRate) {

        // sample rate button
        switch (sampleRate) {
        case -2: // real time
            views.setImageViewResource(R.id.widget_sample_btn, R.drawable.wi_smp_4_selector);
            break;
        case -1: // often
            views.setImageViewResource(R.id.widget_sample_btn, R.drawable.wi_smp_3_selector);
            break;
        case 0: // normal
            views.setImageViewResource(R.id.widget_sample_btn, R.drawable.wi_smp_2_selector);
            break;
        case 1: // rarely
            views.setImageViewResource(R.id.widget_sample_btn, R.drawable.wi_smp_1_selector);
            break;
        default: // no sampling
            views.setImageViewResource(R.id.widget_sample_btn, R.drawable.wi_smp_0_selector);
        }

        // sync rate button
        switch (syncRate) {
        case -2: // real time
            views.setImageViewResource(R.id.widget_sync_btn, R.drawable.wi_syn_4_selector);
            break;
        case -1: // often
            views.setImageViewResource(R.id.widget_sync_btn, R.drawable.wi_syn_3_selector);
            break;
        case 0: // normal
            views.setImageViewResource(R.id.widget_sync_btn, R.drawable.wi_syn_2_selector);
            break;
        case 1: // rarely
            views.setImageViewResource(R.id.widget_sync_btn, R.drawable.wi_syn_1_selector);
            break;
        default: // no sampling
            views.setImageViewResource(R.id.widget_sync_btn, R.drawable.wi_syn_0_selector);
        }
    }
}
