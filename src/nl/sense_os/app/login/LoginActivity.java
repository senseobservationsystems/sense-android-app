package nl.sense_os.app.login;

import nl.sense_os.app.R;
import nl.sense_os.app.SenseSettings;
import nl.sense_os.app.dialogs.WaitDialog;
import nl.sense_os.app.login.LoginDialog.ILoginActivity;
import nl.sense_os.service.ISenseServiceCallback;
import nl.sense_os.service.SenseService.SenseBinder;
import nl.sense_os.service.SenseServiceStub;
import nl.sense_os.service.commonsense.SenseApi;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

public class LoginActivity extends FragmentActivity implements ILoginActivity {

    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            Log.d(TAG, "Change login result: " + result);

            if (null != waitDialog) {
                try {
                    waitDialog.dismiss();
                } catch (final IllegalArgumentException e) {
                    // do nothing, perhaps the progress dialog was already dismissed
                }
            }

            if (result == -2) {
                showToast(getString(R.string.toast_login_forbidden), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showLoginDialog();
                    }
                });

            } else if (result == -1) {
                showToast(getString(R.string.toast_login_fail), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showLoginDialog();
                    }
                });

            } else {
                showToast(getString(R.string.toast_login_ok), Toast.LENGTH_LONG);

                onLoginSuccess();
            }
        }

        @Override
        public void onRegisterResult(int result) throws RemoteException {
            // not used
        }

        @Override
        public void statusReport(int status) throws RemoteException {
            // not used
        }
    }

    private class SenseServiceConn implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SenseBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            /* this is not called when the service is stopped, only when it is suddenly killed! */
            service = null;
            isServiceBound = false;
        }
    };

    private static final String TAG = "LoginActivity";

    private final ISenseServiceCallback callback = new SenseCallback();
    private boolean isServiceBound;
    private SenseServiceStub service;
    private final ServiceConnection serviceConn = new SenseServiceConn();

    private WaitDialog waitDialog;

    /**
     * Binds to the Sense Service, creating it if necessary.
     */
    private void bindToSenseService() {

        // start the service if it was not running already
        if (!isServiceBound) {
            // Log.v(TAG, "Try to bind to Sense Platform service");
            final Intent serviceIntent = new Intent(getString(R.string.action_sense_service));
            isServiceBound = bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
        } else {
            // already bound
        }
    }

    @Override
    public void onCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        showLoginDialog();
    }

    private void onLoginSuccess() {
        service.toggleMain(true);

        // check if this is the very first login
        final SharedPreferences appPrefs = PreferenceManager
                .getDefaultSharedPreferences(LoginActivity.this);
        if (appPrefs.getBoolean(SenseSettings.PREF_FIRST_LOGIN, true)) {
            final Editor editor = appPrefs.edit();
            editor.putBoolean(SenseSettings.PREF_FIRST_LOGIN, false);
            editor.commit();

            service.togglePhoneState(true);
        }

        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onStart() {
        // Log.v(TAG, "onStart");
        super.onStart();
        bindToSenseService();
    }

    @Override
    protected void onStop() {
        // Log.v(TAG, "onStop");
        unbindFromSenseService();
        super.onStop();
    }

    @Override
    public void onSubmit(String username, String password) {
        if ((null != username) && (null != password) && (username.length() > 0)
                && (password.length() > 0)) {
            submit(username, password);
            showWaitDialog();

        } else {
            onWrongInput();
        }
    }

    private void onWrongInput() {
        showToast(getString(R.string.toast_missing_fields), Toast.LENGTH_LONG);
        showLoginDialog();
    }

    private void showLoginDialog() {
        LoginDialog dialog = LoginDialog.newInstance(this);
        dialog.show(getSupportFragmentManager(), "login");
    }

    private void showToast(final CharSequence text, final int duration) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(LoginActivity.this, text, duration).show();
            }
        });
    }

    private void showWaitDialog() {
        waitDialog = WaitDialog.newInstance(R.string.dialog_progress_login_msg);
        waitDialog.show(getSupportFragmentManager(), "wait");
    }

    private void submit(String username, String password) {
        try {
            service.changeLogin(username, SenseApi.hashPassword(password), callback);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call changeLogin: '" + e + "'");
            finish();
        }
    }

    /**
     * Unbinds from the Sense service, resets {@link #service} and {@link #isServiceBound}.
     */
    private void unbindFromSenseService() {

        if ((true == isServiceBound) && (null != serviceConn)) {
            // Log.v(TAG, "Unbind from Sense Platform service");
            unbindService(serviceConn);
        } else {
            // already unbound
        }
        service = null;
        isServiceBound = false;
    }
}
