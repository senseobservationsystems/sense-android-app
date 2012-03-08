package nl.sense_os.app.login;

import nl.sense_os.app.R;
import nl.sense_os.app.SenseSettings;
import nl.sense_os.service.ISenseService;
import nl.sense_os.service.ISenseServiceCallback;
import nl.sense_os.service.commonsense.SenseApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends Activity {

    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            Log.d(TAG, "Change login result: " + result);

            try {
                dismissDialog(DIALOG_PROGRESS);
            } catch (final IllegalArgumentException e) {
                // do nothing, perhaps the progress dialog was already dismissed
            }

            if (result == -2) {
                showToast(getString(R.string.toast_login_forbidden), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showDialog(DIALOG_LOGIN);
                    }
                });

            } else if (result == -1) {
                showToast(getString(R.string.toast_login_fail), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showDialog(DIALOG_LOGIN);
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
            service = ISenseService.Stub.asInterface(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            /* this is not called when the service is stopped, only when it is suddenly killed! */
            service = null;
            isServiceBound = false;
        }
    };

    private static final int DIALOG_LOGIN = 1;
    private static final int DIALOG_PROGRESS = 2;

    private static final String TAG = "LoginActivity";

    private final ISenseServiceCallback callback = new SenseCallback();
    private boolean isServiceBound;
    private ISenseService service;
    private final ServiceConnection serviceConn = new SenseServiceConn();

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

    private Dialog createLoginDialog() {

        // prepare content view
        View view = getLayoutInflater().inflate(R.layout.dialog_login, null);
        final EditText usernameField = (EditText) view.findViewById(R.id.login_username);
        final EditText passField = (EditText) view.findViewById(R.id.login_pass);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_login_title);
        builder.setIcon(R.drawable.ic_dialog_sense);
        builder.setView(view);
        builder.setPositiveButton(R.string.button_login, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String username = usernameField.getText() == null ? null : usernameField.getText()
                        .toString();
                String password = passField.getText() == null ? null : passField.getText()
                        .toString();
                if (null != username && null != password && username.length() > 0) {
                    submit(username, password);
                } else {
                    showToast(getString(R.string.toast_missing_fields), Toast.LENGTH_LONG);
                    finish();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        return builder.create();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        showDialog(DIALOG_LOGIN);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch (id) {
        case DIALOG_LOGIN:
            dialog = createLoginDialog();
            break;
        case DIALOG_PROGRESS:
            dialog = new ProgressDialog(this);
            dialog.setTitle(R.string.dialog_progress_title);
            ((ProgressDialog) dialog).setMessage(getString(R.string.dialog_progress_login_msg));
            dialog.setCancelable(false);
            break;
        default:
            dialog = super.onCreateDialog(id);
        }
        return dialog;
    }

    private void onLoginSuccess() {
        try {
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
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to start service after login: '" + e + "'");
        }

        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart");
        super.onStart();
        bindToSenseService();
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        unbindFromSenseService();
        super.onStop();
    }

    private void showToast(final CharSequence text, final int duration) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(LoginActivity.this, text, duration).show();
            }
        });
    }

    private void submit(String username, String password) {
        try {
            dismissDialog(DIALOG_LOGIN);
        } catch (IllegalArgumentException e) {
            // ignore
        }

        try {
            service.changeLogin(username, SenseApi.hashPassword(password), callback);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call changeLogin: '" + e + "'");
            finish();
        }

        showDialog(DIALOG_PROGRESS);
    }

    /**
     * Unbinds from the Sense service, resets {@link #service} and {@link #isServiceBound}.
     */
    private void unbindFromSenseService() {

        if (true == isServiceBound && null != serviceConn) {
            // Log.v(TAG, "Unbind from Sense Platform service");
            unbindService(serviceConn);
        } else {
            // already unbound
        }
        service = null;
        isServiceBound = false;
    }
}
