package nl.sense_os.app.dialogs;

import nl.sense_os.app.R;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

public class LoginDialog extends Dialog {

    private static final String TAG = "LoginDialog";

    private EditText usernameField;
    private EditText passField;

    public LoginDialog(final Context context) {
        super(context);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.dialog_login);

        setTitle(R.string.dialog_login_title);
        setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_dialog_sense);

        usernameField = (EditText) findViewById(R.id.login_username);
        passField = (EditText) findViewById(R.id.login_pass);

        Button registerButton = (Button) findViewById(R.id.login_btn_login);
        registerButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        Button cancelButton = (Button) findViewById(R.id.login_btn_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                passField.setText("");
                cancel();
            }
        });
    }

    public String getPassword() {
        return passField.getText().toString();
    }

    public String getUsername() {
        return usernameField.getText().toString();
    }

    /**
     * @param password
     *            The username to use as preset for the login form.
     */
    public void setPassword(String password) {
        passField.setText(password);
    }

    /**
     * @param username
     *            The username to use as preset for the login form.
     */
    public void setUsername(String username) {
        usernameField.setText(username);
    }
}
