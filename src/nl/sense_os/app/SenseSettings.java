/*
 * **************************************************************************************************
 * Copyright (C) 2010 Sense Observation Systems, Rotterdam, the Netherlands. All rights reserved. *
 * **
 * ************************************************************************************************
 */
package nl.sense_os.app;

import nl.sense_os.platform.SensePlatform;
import nl.sense_os.service.SenseServiceStub;
import nl.sense_os.service.constants.SensePrefs;
import nl.sense_os.service.constants.SensePrefs.Auth;
import nl.sense_os.service.constants.SensePrefs.Main.Advanced;
import nl.sense_os.service.constants.SensePrefs.Main.Ambience;
import nl.sense_os.service.constants.SensePrefs.Main.DevProx;
import nl.sense_os.service.constants.SensePrefs.Main.External.MyGlucoHealth;
import nl.sense_os.service.constants.SensePrefs.Main.External.OBD2Sensor;
import nl.sense_os.service.constants.SensePrefs.Main.External.TanitaScale;
import nl.sense_os.service.constants.SensePrefs.Main.External.ZephyrBioHarness;
import nl.sense_os.service.constants.SensePrefs.Main.External.ZephyrHxM;
import nl.sense_os.service.constants.SensePrefs.Main.Location;
import nl.sense_os.service.constants.SensePrefs.Main.Motion;
import nl.sense_os.service.constants.SensePrefs.Main.PhoneState;
import nl.sense_os.service.constants.SensePrefs.Main.Quiz;
import nl.sense_os.service.constants.SensePrefs.Status;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;

/**
 * Shows the Sense preferences as defines in /res/values/preferences.xml. Calls the SenseService to
 * set/get the preferences for the actual service.<br/>
 * <br/>
 * Uses a lot of deprecated API because we cannot use PreferenceFragments so we need to rely on
 * older solutions.
 */
@SuppressWarnings("deprecation")
public class SenseSettings extends PreferenceActivity {

    /**
     * Listener for changes in the preferences. Any changes are immediately sent to the Sense
     * Platform service.
     */
    private class PrefSyncListener implements OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.v(TAG, "Preference '" + key + "' changed");

            SenseServiceStub service = mSensePlatform.getService();
            if (service == null) {
                Log.e(TAG, "Could not send preference to Sense Platform service: service = null!");
                return;
            }

            // send the new preference to the Sense Platform service
            try {
                String value = sharedPreferences.getString(key, "");
                service.setPrefString(key, value);
                showSummaries();
                return;
            } catch (ClassCastException e) {
                // do nothing, try another preference type
            }
            try {
                boolean value = sharedPreferences.getBoolean(key, false);
                service.setPrefBool(key, value);
                showSummaries();
                return;
            } catch (ClassCastException e) {
                // do nothing, try another preference type
            }
            try {
                float value = sharedPreferences.getFloat(key, 0f);
                service.setPrefFloat(key, value);
                showSummaries();
                return;
            } catch (ClassCastException e) {
                // do nothing, try another preference type
            }
            try {
                int value = sharedPreferences.getInt(key, 0);
                service.setPrefInt(key, value);
                showSummaries();
                return;
            } catch (ClassCastException e) {
                // do nothing, try another preference type
            }
            try {
                long value = sharedPreferences.getLong(key, 0l);
                service.setPrefLong(key, value);
                showSummaries();
                return;
            } catch (ClassCastException e) {
                Log.e(TAG, "Can't read new preference setting!");
            }

        }
    };

    private static final int DIALOG_DEV_MODE = 0;
    private static final int DIALOG_LOGOUT = 1;
    /**
     * Sense App specific preference to keep track of whether the user has logged in at least once.
     */
    public static final String PREF_FIRST_LOGIN = "first_login_complete";
    private static final String TAG = "SenseSettings";

    private PrefSyncListener mPrefChangeListener = new PrefSyncListener();
    private SensePlatform mSensePlatform;

    @TargetApi(11)
    private Dialog createDialogDevMode() {

        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // specifically set dark theme for Android 3.0+
            builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        }

        builder.setTitle(R.string.dialog_dev_mode_title);
        builder.setMessage(R.string.dialog_dev_mode_msg);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // do nothing
            }
        });
        return builder.create();
    }

    /**
     * @return a dialog to confirm if the user want to log out.
     */
    @TargetApi(11)
    private Dialog createDialogLogout() {

        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // specifically set dark theme for Android 3.0+
            builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        }

        // get username
        SenseServiceStub service = mSensePlatform.getService();
        String username = null;
        if (null != service) {
            username = service.getPrefString(Auth.LOGIN_USERNAME, null);
        }

        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.dialog_logout_msg);
        builder.setTitle(getString(R.string.dialog_logout_title, username));
        builder.setPositiveButton(R.string.button_logout, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                logout();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    /**
     * Loads all preferences from the Sense Platform service, and puts them into this activity's
     * default preferences.
     */
    private void loadPreferences() {
        Log.v(TAG, "Load preferences");

        SenseServiceStub service = mSensePlatform.getService();
        if (null == service) {
            Log.w(TAG, "Cannot load Sense Platform preferences! service=null");
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = prefs.edit();

        // general preferences
        editor.putString(SensePrefs.Main.SAMPLE_RATE,
                service.getPrefString(SensePrefs.Main.SAMPLE_RATE, "0"));
        editor.putString(SensePrefs.Main.SYNC_RATE,
                service.getPrefString(SensePrefs.Main.SYNC_RATE, "0"));
        editor.putBoolean(Status.AUTOSTART, service.getPrefBool(Status.AUTOSTART, false));

        // phone state preferences
        editor.putBoolean(PhoneState.BATTERY, service.getPrefBool(PhoneState.BATTERY, true));
        editor.putBoolean(PhoneState.CALL_STATE, service.getPrefBool(PhoneState.CALL_STATE, true));
        editor.putBoolean(PhoneState.SCREEN_ACTIVITY,
                service.getPrefBool(PhoneState.SCREEN_ACTIVITY, true));
        editor.putBoolean(PhoneState.PROXIMITY, service.getPrefBool(PhoneState.PROXIMITY, true));
        editor.putBoolean(PhoneState.DATA_CONNECTION,
                service.getPrefBool(PhoneState.DATA_CONNECTION, true));
        editor.putBoolean(PhoneState.SERVICE_STATE,
                service.getPrefBool(PhoneState.SERVICE_STATE, true));
        editor.putBoolean(PhoneState.SIGNAL_STRENGTH,
                service.getPrefBool(PhoneState.SIGNAL_STRENGTH, true));
        editor.putBoolean(PhoneState.IP_ADDRESS, service.getPrefBool(PhoneState.IP_ADDRESS, true));
        editor.putBoolean(PhoneState.UNREAD_MSG, service.getPrefBool(PhoneState.UNREAD_MSG, true));

        // location preferences
        editor.putBoolean(Location.GPS, service.getPrefBool(Location.GPS, true));
        editor.putBoolean(Location.NETWORK, service.getPrefBool(Location.NETWORK, true));
        editor.putBoolean(Location.AUTO_GPS, service.getPrefBool(Location.AUTO_GPS, true));

        // ambience preferences
        editor.putBoolean(Ambience.LIGHT, service.getPrefBool(Ambience.LIGHT, true));
        editor.putBoolean(Ambience.MIC, service.getPrefBool(Ambience.MIC, true));
        editor.putBoolean(Ambience.PRESSURE, service.getPrefBool(Ambience.PRESSURE, true));
        editor.putBoolean(Ambience.CAMERA_LIGHT, service.getPrefBool(Ambience.CAMERA_LIGHT, true));
        editor.putBoolean(Ambience.AUDIO_SPECTRUM,
                service.getPrefBool(Ambience.AUDIO_SPECTRUM, true));
        editor.putBoolean(Ambience.MAGNETIC_FIELD,
                service.getPrefBool(Ambience.MAGNETIC_FIELD, true));

        // motion preferences
        editor.putBoolean(Motion.FALL_DETECT, service.getPrefBool(Motion.FALL_DETECT, false));
        editor.putBoolean(Motion.FALL_DETECT_DEMO,
                service.getPrefBool(Motion.FALL_DETECT_DEMO, false));
        editor.putBoolean(Motion.UNREG, service.getPrefBool(Motion.UNREG, true));
        editor.putBoolean(Motion.SCREENOFF_FIX, service.getPrefBool(Motion.SCREENOFF_FIX, false));

        // neighboring devices
        editor.putBoolean(DevProx.BLUETOOTH, service.getPrefBool(DevProx.BLUETOOTH, true));
        editor.putBoolean(DevProx.WIFI, service.getPrefBool(DevProx.WIFI, true));
        editor.putBoolean(DevProx.NFC, service.getPrefBool(DevProx.NFC, true));

        // pop quiz preferences
        editor.putString(Quiz.RATE, service.getPrefString(Quiz.RATE, "0"));
        editor.putBoolean(Quiz.SILENT_MODE, service.getPrefBool(Quiz.SILENT_MODE, false));

        // Zephir BioHarness preferences
        editor.putBoolean(ZephyrBioHarness.MAIN, service.getPrefBool(ZephyrBioHarness.MAIN, false));
        editor.putBoolean(ZephyrBioHarness.ACC, service.getPrefBool(ZephyrBioHarness.ACC, true));
        editor.putBoolean(ZephyrBioHarness.BATTERY,
                service.getPrefBool(ZephyrBioHarness.BATTERY, true));
        editor.putBoolean(ZephyrBioHarness.HEART_RATE,
                service.getPrefBool(ZephyrBioHarness.HEART_RATE, true));
        editor.putBoolean(ZephyrBioHarness.RESP, service.getPrefBool(ZephyrBioHarness.RESP, true));
        editor.putBoolean(ZephyrBioHarness.TEMP, service.getPrefBool(ZephyrBioHarness.TEMP, true));
        editor.putBoolean(ZephyrBioHarness.WORN_STATUS,
                service.getPrefBool(ZephyrBioHarness.WORN_STATUS, true));

        // Zephir HxM preferences
        editor.putBoolean(ZephyrHxM.MAIN, service.getPrefBool(ZephyrHxM.MAIN, false));
        editor.putBoolean(ZephyrHxM.BATTERY, service.getPrefBool(ZephyrHxM.BATTERY, true));
        editor.putBoolean(ZephyrHxM.DISTANCE, service.getPrefBool(ZephyrHxM.DISTANCE, true));
        editor.putBoolean(ZephyrHxM.HEART_RATE, service.getPrefBool(ZephyrHxM.HEART_RATE, true));
        editor.putBoolean(ZephyrHxM.SPEED, service.getPrefBool(ZephyrHxM.SPEED, true));
        editor.putBoolean(ZephyrHxM.STRIDES, service.getPrefBool(ZephyrHxM.STRIDES, true));

        // MyGlucohealth
        editor.putBoolean(MyGlucoHealth.MAIN, service.getPrefBool(MyGlucoHealth.MAIN, false));

        // Tanita scale
        editor.putBoolean(TanitaScale.MAIN, service.getPrefBool(TanitaScale.MAIN, false));

        // ODB-II dongle
        editor.putBoolean(OBD2Sensor.MAIN, service.getPrefBool(OBD2Sensor.MAIN, false));

        // advanced settings
        editor.putBoolean(Advanced.DEV_MODE, service.getPrefBool(Advanced.DEV_MODE, false));
        editor.putBoolean(Advanced.COMPRESS, service.getPrefBool(Advanced.COMPRESS, true));
        editor.putBoolean(Advanced.USE_COMMONSENSE,
                service.getPrefBool(Advanced.USE_COMMONSENSE, true));
        editor.putBoolean(Advanced.AGOSTINO, service.getPrefBool(Advanced.AGOSTINO, false));
        editor.putBoolean(Motion.EPIMODE, service.getPrefBool(Motion.EPIMODE, false));
        editor.putBoolean(Advanced.LOCATION_FEEDBACK,
                service.getPrefBool(Advanced.LOCATION_FEEDBACK, false));

        editor.commit();
    }

    private void logout() {
        SenseServiceStub service = mSensePlatform.getService();
        service.logout();
        service.toggleMain(false);

        showSummaries();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSensePlatform = ((SenseApplication) getApplication()).getSensePlatform();

        addPreferencesFromResource(R.xml.preferences);

        // setup some preferences with custom dialogs
        setupLoginPref();
        setupRegisterPref();
        setupExternalSensorPrefs();

        final Preference devMode = findPreference(Advanced.DEV_MODE);
        devMode.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(SenseSettings.this);
                if (prefs.getBoolean(Advanced.DEV_MODE, false)) {
                    showDialog(DIALOG_DEV_MODE);
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        switch (id) {
        case DIALOG_DEV_MODE:
            dialog = createDialogDevMode();
            break;
        case DIALOG_LOGOUT:
            dialog = createDialogLogout();
            break;
        default:
            dialog = super.onCreateDialog(id);
            break;
        }
        return dialog;
    }

    private void onLoginClick() {

        SenseServiceStub service = mSensePlatform.getService();
        boolean loggedIn = false;
        if (service != null) {
            loggedIn = service.getPrefString(Auth.LOGIN_USERNAME, null) != null;
        }

        if (loggedIn) {
            showDialog(DIALOG_LOGOUT);
        } else {
            startActivity(new Intent(SenseSettings.this, LoginActivity.class));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // app icon in action bar clicked; go home
            Intent intent = new Intent(this, SenseMainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        SharedPreferences appPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        appPrefs.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener);

        super.onPause();
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_LOGOUT) {
            SenseServiceStub service = mSensePlatform.getService();
            if (null != service) {
                String username = service.getPrefString(Auth.LOGIN_USERNAME, null);
                dialog.setTitle(getString(R.string.dialog_logout_title, username));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadPreferences();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(mPrefChangeListener);
        showSummaries();
    }

    /**
     * Sets up the Login preference to display a login dialog.
     */
    private void setupLoginPref() {
        final Preference loginPref = findPreference("login_placeholder");
        loginPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                onLoginClick();
                return true;
            }
        });
    }

    /**
     * Sets up the Register preference to display a registration dialog.
     */
    private void setupRegisterPref() {
        final Preference registerPref = findPreference("register_placeholder");
        registerPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(SenseSettings.this, RegisterActivity.class));
                return true;
            }
        });
    }

    /**
     * Sets up the Register preference to display a registration dialog.
     */
    private void setupExternalSensorPrefs() {

        // set up BioHarness preference
        CheckBoxPreference bioharnessPref = (CheckBoxPreference) findPreference(ZephyrBioHarness.MAIN);
        final Preference bioharnessScreen = findPreference("prefscr_zephyr_bioharness");
        bioharnessScreen.setEnabled(bioharnessPref.isChecked());
        bioharnessPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean enabled = (Boolean) newValue;
                bioharnessScreen.setEnabled(enabled);
                return true;
            }
        });

        // set up HxM preference
        CheckBoxPreference hxmPref = (CheckBoxPreference) findPreference(ZephyrHxM.MAIN);
        final Preference hxmScreen = findPreference("prefscr_zephyr_hxm");
        hxmScreen.setEnabled(hxmPref.isChecked());
        hxmPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean enabled = (Boolean) newValue;
                hxmScreen.setEnabled(enabled);
                return true;
            }
        });
    }

    /**
     * Shows the summaries of the two sync/sense rate list preferences.
     */
    private void showSummaries() {
        Log.v(TAG, "Show summaries");

        SenseServiceStub service = mSensePlatform.getService();
        if (null == service) {
            Log.w(TAG, "Cannot show preference summaries! service=null");
            return;
        }

        // get username from Sense Platform service
        Preference loginPref = findPreference("login_placeholder");
        Preference regPref = findPreference("register_placeholder");
        String username = null;
        username = service.getPrefString(Auth.LOGIN_USERNAME, null);

        if (null != username) {
            loginPref.setTitle(R.string.pref_logout_title);
            loginPref.setSummary(getString(R.string.pref_logout_summary, username));
            regPref.setEnabled(false);
        } else {
            loginPref.setTitle(R.string.pref_login_title);
            loginPref.setSummary(R.string.pref_login_summary);
            regPref.setEnabled(true);
        }
    }
}
