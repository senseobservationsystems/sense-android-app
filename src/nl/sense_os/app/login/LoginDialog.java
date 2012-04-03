package nl.sense_os.app.login;

import nl.sense_os.app.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class LoginDialog extends DialogFragment {

    public static interface ILoginActivity {

        /**
         * Called when the login dialog is canceled
         */
        void onCancel();

        /**
         * Called when the user presses the login button
         * 
         * @param username
         *            Value of the username field (can be null)
         * @param password
         *            Value of the password field (can be null)
         */
        void onSubmit(String username, String password);
    }

    private static final String TAG = "Login dialog";

    public static LoginDialog newInstance(ILoginActivity listener) {
        LoginDialog dialog = new LoginDialog();
        dialog.setListener(listener);
        return dialog;
    }

    private ILoginActivity listener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.d(TAG, "onCreateDialog");

        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // prepare content view
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_login, null);
        ;
        final EditText usernameField = (EditText) view.findViewById(R.id.login_username);
        final EditText passField = (EditText) view.findViewById(R.id.login_pass);

        builder.setTitle(R.string.dialog_login_title);
        builder.setIcon(R.drawable.ic_dialog_sense);
        builder.setView(view);
        builder.setPositiveButton(R.string.button_login, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String username = usernameField.getText() == null ? null : usernameField.getText()
                        .toString();
                String password = passField.getText() == null ? null : passField.getText()
                        .toString();
                listener.onSubmit(username, password);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                listener.onCancel();
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                listener.onCancel();
            }
        });
        return builder.create();
    }

    private void setListener(ILoginActivity listener) {
        this.listener = listener;
    }
}
