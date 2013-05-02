package nl.sense_os.app;

import nl.sense_os.app.login.LoginActivity;
import nl.sense_os.app.register.RegisterActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/**
 * Activity that shows a welcome text and lets the user start the login or register activities.
 * 
 * @author Steven Mulder <steven@sense-os.nl>
 */
public class WelcomeActivity extends Activity {

    private static final String TAG = "WelcomeActivity";
    private static final int REQ_CODE_LOGIN = 1;
    private static final int REQ_CODE_REGISTER = 2;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQ_CODE_LOGIN:
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK);
                finish();
            }
            break;
        case REQ_CODE_REGISTER:
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK);
                finish();
            }
            break;
        default:
            Log.w(TAG, "Unexpected request code: " + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
    }

    /**
     * Handles clicks on the login button
     */
    public void onLoginClick(View v) {
        Intent login = new Intent(this, LoginActivity.class);
        startActivityForResult(login, REQ_CODE_LOGIN);
    }

    /**
     * Handles clicks in the register button
     */
    public void onRegisterClick(View v) {
        Intent register = new Intent(this, RegisterActivity.class);
        startActivityForResult(register, REQ_CODE_REGISTER);
    }
}
