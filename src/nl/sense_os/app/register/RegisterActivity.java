package nl.sense_os.app.register;

import nl.sense_os.app.R;
import nl.sense_os.app.SenseApplication;
import nl.sense_os.app.SenseSettings;
import nl.sense_os.app.dialogs.WaitDialog;
import nl.sense_os.app.register.RegisterDialog.IRegisterActivity;
import nl.sense_os.platform.SensePlatform;
import nl.sense_os.service.ISenseServiceCallback;
import nl.sense_os.service.commonsense.SenseApi;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

public class RegisterActivity extends FragmentActivity implements IRegisterActivity {

    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            // not used
        }

        @Override
        public void onRegisterResult(int result) throws RemoteException {
            Log.v(TAG, "Registration result: " + result);

            if (null != mWaitDialog) {
                try {
                    mWaitDialog.dismiss();
                } catch (final IllegalArgumentException e) {
                    // do nothing
                }
            }

            if (result == -2) {
                showToast(getString(R.string.toast_reg_conflict), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showRegisterDialog();
                    }
                });
            } else if (result == -1) {
                showToast(getString(R.string.toast_reg_fail), Toast.LENGTH_LONG);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showRegisterDialog();
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

    private static final String TAG = "RegisterActivity";

    private final ISenseServiceCallback mCallback = new SenseCallback();
    private SensePlatform mSensePlatform;
    private WaitDialog mWaitDialog;

    @Override
    public void onCancel() {
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSensePlatform = ((SenseApplication) getApplication()).getSensePlatform();

        showRegisterDialog();
    }

    private void onNoPassMatch() {
        showToast(getString(R.string.toast_reg_pass), Toast.LENGTH_LONG);
        showRegisterDialog();
    }

    private void onRegisterSuccess() {
        mSensePlatform.getService().toggleMain(true);

        // check if this is the very first login
        final SharedPreferences appPrefs = PreferenceManager
                .getDefaultSharedPreferences(RegisterActivity.this);
        if (appPrefs.getBoolean(SenseSettings.PREF_FIRST_LOGIN, true)) {
            final Editor editor = appPrefs.edit();
            editor.putBoolean(SenseSettings.PREF_FIRST_LOGIN, false);
            editor.commit();

            mSensePlatform.getService().togglePhoneState(true);
        }

        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onSubmit(String username, String password, String password2, String email,
            String address, String zipCode, String country, String name, String surname,
            String phone) {
        if ((username != null) && (username.length() > 0) && (password != null)
                && (password.length() > 0) && (email != null) && (email.length() > 0)) {
            if (password.equals(password2)) {
                submit(username, password, email, address, zipCode, country, name, surname, phone);
                showWaitDialog();

            } else {
                onNoPassMatch();
            }

        } else {
            onWrongInput();
        }
    }

    private void onWrongInput() {
        showToast(getString(R.string.toast_missing_fields), Toast.LENGTH_LONG);
        showRegisterDialog();
    }

    private void showRegisterDialog() {
        RegisterDialog dialog = RegisterDialog.newInstance(this);
        dialog.show(getSupportFragmentManager(), "register");
    }

    private void showToast(final CharSequence text, final int duration) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(RegisterActivity.this, text, duration).show();
            }
        });
    }

    private void showWaitDialog() {
        mWaitDialog = WaitDialog.newInstance(R.string.dialog_progress_reg_msg);
        mWaitDialog.show(getSupportFragmentManager(), "wait");
    }

    private void submit(String username, String password, String email, String address,
            String zipCode, String country, String name, String surname, String phone) {
        String hashedPass = SenseApi.hashPassword(password);
        mSensePlatform.getService().register(username, hashedPass, email, address, zipCode,
                country, name, surname, phone, mCallback);
    }
}
