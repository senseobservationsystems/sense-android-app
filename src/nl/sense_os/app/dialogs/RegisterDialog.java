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
import android.widget.Toast;

public class RegisterDialog extends Dialog {

    private EditText usernameField;
    private EditText passField1;
    private EditText passField2;
    private EditText nameField;
    private EditText surnameField;
    private EditText emailField;
    private EditText phoneField;
    private AsyncTask<String, ?, ?> onSubmit;

    /**
     * Constructs a new dialog. Nota bene: make sure to call {@link #setOnSubmitTask(AsyncTask)}
     * before using the dialog.
     * 
     * @param context
     */
    public RegisterDialog(final Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.dialog_register);

        setTitle(R.string.dialog_reg_title);
        setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_launcher_sense);

        usernameField = (EditText) findViewById(R.id.reg_username);
        passField1 = (EditText) findViewById(R.id.reg_pass1);
        passField2 = (EditText) findViewById(R.id.reg_pass2);
        nameField = (EditText) findViewById(R.id.reg_name);
        surnameField = (EditText) findViewById(R.id.reg_surname);
        emailField = (EditText) findViewById(R.id.reg_email);
        phoneField = (EditText) findViewById(R.id.reg_phone);

        Button registerButton = (Button) findViewById(R.id.reg_btn_register);
        registerButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                final String username = usernameField.getText().toString();
                final String pass1 = passField1.getText().toString();
                final String pass2 = passField2.getText().toString();
                final String name = nameField.getText().toString();
                final String surname = surnameField.getText().toString();
                final String email = emailField.getText().toString();
                final String phone = phoneField.getText().toString();

                if (pass1.equals(pass2)) {

                    // start registration
                    onSubmit.execute(username, pass1, name, surname, email, phone);

                    // dismiss myself
                    RegisterDialog.this.dismiss();

                } else {
                    Toast.makeText(context, R.string.toast_reg_pass, Toast.LENGTH_SHORT).show();

                    passField1.setText("");
                    passField2.setText("");
                }
            }
        });
        Button cancelButton = (Button) findViewById(R.id.reg_btn_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                RegisterDialog.this.cancel();
            }
        });

        // clear the text fields when the dialog is dismissed
        setOnDismissListener(new OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dialog) {
                usernameField.setText("");
                passField1.setText("");
                passField2.setText("");
                nameField.setText("");
                surnameField.setText("");
                emailField.setText("");
                phoneField.setText("");
            }
        });
    }

    /**
     * @param onSubmit
     *            The AsyncTask to perform the login when the registration form is submitted.
     */
    public void setOnSubmitTask(AsyncTask<String, ?, ?> onSubmit) {
        this.onSubmit = onSubmit;
    }
}
