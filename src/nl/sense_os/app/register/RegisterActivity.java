package nl.sense_os.app.register;

import nl.sense_os.app.R;
import nl.sense_os.app.SenseSettings;
import nl.sense_os.service.ISenseService;
import nl.sense_os.service.ISenseServiceCallback;
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

public class RegisterActivity extends Activity {

    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            // not used
        }

        @Override
        public void onRegisterResult(int result) throws RemoteException {
            Log.d(TAG, "Registration result: " + result);

            try {
                dismissDialog(DIALOG_PROGRESS);
            } catch (final IllegalArgumentException e) {
                // do nothing
            }

            if (result == -2) {
                showToast(getString(R.string.toast_reg_conflict), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showDialog(DIALOG_REGISTER);
                    }
                });
            } else if (result == -1) {
                showToast(getString(R.string.toast_reg_fail), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showDialog(DIALOG_REGISTER);
                    }
                });
            } else {
                showToast(getString(R.string.toast_reg_ok), Toast.LENGTH_LONG);

                onRegisterSuccess();
            }
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

    private static final int DIALOG_REGISTER = 1;
    private static final int DIALOG_PROGRESS = 2;

    private static final String TAG = "RegisterActivity";

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

    private Dialog createRegisterDialog() {

        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // prepare content view
        View view = getLayoutInflater().inflate(R.layout.dialog_register, null);
        final EditText usernameField = (EditText) view.findViewById(R.id.reg_username);
        final EditText passField1 = (EditText) view.findViewById(R.id.reg_pass1);
        final EditText passField2 = (EditText) view.findViewById(R.id.reg_pass2);
        final EditText nameField = (EditText) view.findViewById(R.id.reg_name);
        final EditText surnameField = (EditText) view.findViewById(R.id.reg_surname);
        final EditText emailField = (EditText) view.findViewById(R.id.reg_email);
        final EditText phoneField = (EditText) view.findViewById(R.id.reg_phone);

        builder.setTitle(R.string.dialog_reg_title);
        builder.setIcon(R.drawable.ic_dialog_sense);
        builder.setView(view);
        builder.setPositiveButton(R.string.button_reg, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String username = usernameField.getText() == null ? null : usernameField.getText()
                        .toString();
                String password = passField1.getText() == null ? null : passField1.getText()
                        .toString();
                String passCheck = passField2.getText() == null ? null : passField2.getText()
                        .toString();
                String name = nameField.getText() == null ? null : nameField.getText().toString();
                String surname = surnameField.getText() == null ? null : surnameField.getText()
                        .toString();
                String email = emailField.getText() == null ? null : emailField.getText()
                        .toString();
                String phone = phoneField.getText() == null ? null : phoneField.getText()
                        .toString();
                if (username != null && username.length() > 0 && password != null
                        && password.length() > 0 && email != null && email.length() > 0) {
                    if (password.equals(passCheck)) {
                        submit(username, password, name, surname, email, phone);
                    } else {
                        showToast(getString(R.string.toast_reg_pass), Toast.LENGTH_LONG);
                        finish();
                    }
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

        showDialog(DIALOG_REGISTER);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch (id) {
        case DIALOG_REGISTER:
            dialog = createRegisterDialog();
            break;
        case DIALOG_PROGRESS:
            dialog = new ProgressDialog(this);
            dialog.setTitle(R.string.dialog_progress_title);
            ((ProgressDialog) dialog).setMessage(getString(R.string.dialog_progress_reg_msg));
            dialog.setCancelable(false);
            break;
        default:
            dialog = super.onCreateDialog(id);
        }
        return dialog;
    }

    private void onRegisterSuccess() {
        try {
            service.toggleMain(true);

            // check if this is the very first login
            final SharedPreferences appPrefs = PreferenceManager
                    .getDefaultSharedPreferences(RegisterActivity.this);
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

    private void showToast(final CharSequence text, final int duration) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(RegisterActivity.this, text, duration).show();
            }
        });
    }

    private void submit(String username, String password, String name, String surname,
            String email, String phone) {
        try {
            dismissDialog(DIALOG_REGISTER);
        } catch (IllegalArgumentException e) {
            // ignore
        }

        try {
            service.register(username, password, name, surname, email, phone, callback);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call register: '" + e + "'");
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
