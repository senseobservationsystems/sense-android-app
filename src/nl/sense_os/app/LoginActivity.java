package nl.sense_os.app;

import nl.sense_os.platform.SensePlatform;
import nl.sense_os.service.ISenseServiceCallback;
import nl.sense_os.service.commonsense.SenseApi;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends FragmentActivity {

    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {

            if (result == -2) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(LoginActivity.this, R.string.toast_login_forbidden,
                                Toast.LENGTH_LONG).show();
                        showProgress(false);
                    }
                });

            } else if (result == -1) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(LoginActivity.this, R.string.toast_login_fail,
                                Toast.LENGTH_LONG).show();
                        showProgress(false);
                    }
                });

            } else {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(LoginActivity.this, R.string.toast_login_ok,
                                Toast.LENGTH_LONG).show();
                    }
                });
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

    private View mFormView;
    private View mProgressView;
    private EditText mUsernameField;
    private EditText mPasswordField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mSensePlatform = ((SenseApplication) getApplication()).getSensePlatform();

        // bind to UI
        mFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_status);
        mUsernameField = (EditText) findViewById(R.id.login_username);
        mPasswordField = (EditText) findViewById(R.id.login_pass);
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
        }

        // set default sensors
        mSensePlatform.getService().togglePhoneState(true);
        mSensePlatform.getService().toggleLocation(true);
        mSensePlatform.getService().toggleAmbience(true);
        mSensePlatform.getService().toggleMotion(true);
        mSensePlatform.getService().toggleDeviceProx(false);
        mSensePlatform.getService().toggleExternalSensors(false);

        setResult(RESULT_OK);
        finish();
    }

    public void onSubmitClick(View v) {
        Log.v(TAG, "Submit");
        String username = mUsernameField.getText().toString();
        String password = mPasswordField.getText().toString();
        if ((null != username) && (null != password) && (username.length() > 0)
                && (password.length() > 0)) {
            submit(username, password);
            showProgress(true);

        } else {
            onWrongInput();
        }
    }

    private void onWrongInput() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(LoginActivity.this, R.string.toast_missing_fields, Toast.LENGTH_LONG)
                        .show();
                showProgress(false);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // hide the keyboard
        if (show) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mUsernameField.getWindowToken(), 0);
        }

        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow for very easy
        // animations. If available, use these APIs to fade-in the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mProgressView.setVisibility(View.VISIBLE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(show ? 1 : 0)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                        }
                    });

            mFormView.setVisibility(View.VISIBLE);
            mFormView.animate().setDuration(shortAnimTime).alpha(show ? 0 : 1)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                        }
                    });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show and hide the
            // relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void submit(String username, String password) {
        mSensePlatform.getService().changeLogin(username, SenseApi.hashPassword(password),
                mCallback);
    }
}
