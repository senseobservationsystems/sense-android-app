package nl.sense_os.app.dialogs;

import nl.sense_os.app.R;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

public class LoginDialog extends Dialog {

    private EditText usernameField;
    private EditText passField;
    private AsyncTask<String, ?, ?> onSubmit;

    public LoginDialog(final Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.dialog_login);

        setTitle(R.string.dialog_login_title);
        setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_launcher_sense);

        usernameField = (EditText) findViewById(R.id.login_username);
        passField = (EditText) findViewById(R.id.login_pass);

        Button registerButton = (Button) findViewById(R.id.login_btn_login);
        registerButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                final String name = usernameField.getText().toString();
                final String pass = passField.getText().toString();

                // initiate Login
                onSubmit.execute(name, pass);
            }
        });
        Button cancelButton = (Button) findViewById(R.id.login_btn_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                LoginDialog.this.cancel();
            }
        });

        // clear the text fields when the dialog is dismissed
        setOnDismissListener(new OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dialog) {
                passField.setText("");
            }
        });
    }

    /**
     * @param username
     *            The username to use as preset for the login form.
     */
    public void setUsername(String username) {
        usernameField.setText(username);
    }

    /**
     * @param onSubmit
     *            The AsyncTask to perform the login when the login form is submitted.
     */
    public void setOnSubmitTask(AsyncTask<String, ?, ?> onSubmit) {
        this.onSubmit = onSubmit;
    }
}
