package nl.sense_os.app.login;

import nl.sense_os.app.R;
import nl.sense_os.app.SenseApplication;
import nl.sense_os.app.SenseSettings;
import nl.sense_os.app.dialogs.WaitDialog;
import nl.sense_os.app.login.LoginDialog.ILoginActivity;
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

public class LoginActivity extends FragmentActivity implements ILoginActivity {

    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            // Log.d(TAG, "Change login result: " + result);

            if (null != mWaitDialog) {
                try {
                    mWaitDialog.dismiss();
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

    private static final String TAG = "LoginActivity";

    private final ISenseServiceCallback mCallback = new SenseCallback();
    private SensePlatform mSensePlatform;
    private WaitDialog mWaitDialog;

    @Override
    public void onCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSensePlatform = ((SenseApplication) getApplication()).getSensePlatform();

        showLoginDialog();
    }

    private void onLoginSuccess() {
        mSensePlatform.getService().toggleMain(true);

        // check if this is the very first login
        final SharedPreferences appPrefs = PreferenceManager
                .getDefaultSharedPreferences(LoginActivity.this);
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
    public void onSubmit(String username, String password) {
        Log.v(TAG, "Submit");
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
        mWaitDialog = WaitDialog.newInstance(R.string.dialog_progress_login_msg);
        mWaitDialog.show(getSupportFragmentManager(), "wait");
    }

    private void submit(String username, String password) {
        mSensePlatform.getService().changeLogin(username, SenseApi.hashPassword(password), mCallback);
    }
}
