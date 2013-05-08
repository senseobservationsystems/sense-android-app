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
import android.widget.Spinner;
import android.widget.Toast;

public class RegisterActivity extends FragmentActivity {

    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            // not used
        }

        @Override
        public void onRegisterResult(int result) throws RemoteException {
            Log.v(TAG, "Registration result: " + result);

            if (result == -2) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(RegisterActivity.this, R.string.toast_reg_conflict,
                                Toast.LENGTH_LONG).show();
                        showProgress(false);
                    }
                });
            } else if (result == -1) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(RegisterActivity.this, R.string.toast_reg_fail,
                                Toast.LENGTH_LONG).show();
                        showProgress(false);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(RegisterActivity.this, R.string.toast_reg_ok,
                                Toast.LENGTH_LONG).show();
                    }
                });
                onRegisterSuccess();
            }
        }

        @Override
        public void statusReport(int status) throws RemoteException {
            // not used
        }
    }

    private static final String TAG = "RegisterActivity";

    private EditText mAddressField;
    private final ISenseServiceCallback mCallback = new SenseCallback();
    private EditText mConfirmPasswordField;
    private Spinner mCountryField;
    private View mFormView;
    private EditText mNameField;
    private EditText mPasswordField;
    private EditText mPhoneField;
    private View mProgressView;
    private SensePlatform mSensePlatform;
    private EditText mSurnameField;
    private EditText mUsernameField;
    private EditText mZipCodeField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mSensePlatform = ((SenseApplication) getApplication()).getSensePlatform();

        // bind to UI
        mFormView = findViewById(R.id.register_form);
        mProgressView = findViewById(R.id.register_status);
        mUsernameField = (EditText) findViewById(R.id.reg_username);
        mPasswordField = (EditText) findViewById(R.id.reg_pass1);
        mConfirmPasswordField = (EditText) findViewById(R.id.reg_pass2);
        mAddressField = (EditText) findViewById(R.id.reg_address);
        mZipCodeField = (EditText) findViewById(R.id.reg_zipcode);
        mCountryField = (Spinner) findViewById(R.id.reg_country);
        mNameField = (EditText) findViewById(R.id.reg_name);
        mSurnameField = (EditText) findViewById(R.id.reg_surname);
        mPhoneField = (EditText) findViewById(R.id.reg_phone);
    }

    private void onNoPassMatch() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(RegisterActivity.this, R.string.toast_reg_pass, Toast.LENGTH_LONG)
                        .show();
                showProgress(false);
            }
        });
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

    private void onSubmit(String username, String password, String password2, String address,
            String zipCode, String country, String name, String surname, String phone) {
        if ((username != null) && (username.length() > 0) && (password != null)
                && (password.length() > 0)) {
            if (password.equals(password2)) {
                submit(username, password, address, zipCode, country, name, surname, phone);
                showProgress(true);

            } else {
                onNoPassMatch();
            }

        } else {
            onWrongInput();
        }
    }

    public void onSubmitClick(View v) {
        String username = mUsernameField.getText() == null ? null : mUsernameField.getText()
                .toString();
        String password = mPasswordField.getText() == null ? null : mPasswordField.getText()
                .toString();
        String password2 = mConfirmPasswordField.getText() == null ? null : mConfirmPasswordField
                .getText().toString();
        String name = mNameField.getText() == null ? null : mNameField.getText().toString();
        String surname = mSurnameField.getText() == null ? null : mSurnameField.getText()
                .toString();
        String address = mAddressField.getText() == null ? null : mAddressField.getText()
                .toString();
        String zipCode = mZipCodeField.getText() == null ? null : mZipCodeField.getText()
                .toString();
        String country = (String) mCountryField.getSelectedItem();
        String phone = mPhoneField.getText() == null ? null : mPhoneField.getText().toString();
        onSubmit(username, password, password2, address, zipCode, country, name, surname, phone);
    }

    private void onWrongInput() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(RegisterActivity.this, R.string.toast_missing_fields,
                        Toast.LENGTH_LONG).show();
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

    private void submit(String username, String password, String address, String zipCode,
            String country, String name, String surname, String phone) {
        String hashedPass = SenseApi.hashPassword(password);
        mSensePlatform.getService().register(username, hashedPass, username, address, zipCode,
                country, name, surname, phone, mCallback);
    }
}
