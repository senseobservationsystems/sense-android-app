package nl.sense_os.app.register;

import nl.sense_os.app.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.EditText;

public class RegisterDialog extends DialogFragment {

    public static interface IRegisterActivity {

        /**
         * Called when the register dialog is canceled
         */
        void onCancel();

        /**
         * Called when the user pressed the register button
         * 
         * @param username
         *            Value of the username field
         * @param password
         *            Value of the password field
         * @param password2
         *            Value of the password confirmation field
         * @param email
         *            Value of the email field
         * @param name
         *            Value of the (optional) name field
         * @param surname
         *            Value of the (optional) surname field
         * @param phone
         *            Value of the (optional) phone field
         */
        void onSubmit(String username, String password, String password2, String email,
                String name, String surname, String phone);
    }

    public static RegisterDialog newInstance(IRegisterActivity listener) {
        RegisterDialog dialog = new RegisterDialog();
        dialog.setListener(listener);
        return dialog;
    }

    private IRegisterActivity listener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // prepare content view
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_register, null);
        final EditText usernameField = (EditText) view.findViewById(R.id.reg_username);
        final EditText passField1 = (EditText) view.findViewById(R.id.reg_pass1);
        final EditText passField2 = (EditText) view.findViewById(R.id.reg_pass2);
        final EditText emailField = (EditText) view.findViewById(R.id.reg_email);
        final EditText nameField = (EditText) view.findViewById(R.id.reg_name);
        final EditText surnameField = (EditText) view.findViewById(R.id.reg_surname);
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
                String password2 = passField2.getText() == null ? null : passField2.getText()
                        .toString();
                String name = nameField.getText() == null ? null : nameField.getText().toString();
                String surname = surnameField.getText() == null ? null : surnameField.getText()
                        .toString();
                String email = emailField.getText() == null ? null : emailField.getText()
                        .toString();
                String phone = phoneField.getText() == null ? null : phoneField.getText()
                        .toString();
                listener.onSubmit(username, password, password2, email, name, surname, phone);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        listener.onCancel();
    }

    private void setListener(IRegisterActivity listener) {
        this.listener = listener;
    }
}
