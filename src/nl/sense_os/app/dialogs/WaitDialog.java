package nl.sense_os.app.dialogs;

import nl.sense_os.app.R;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class WaitDialog extends DialogFragment {

    public static WaitDialog newInstance(int msg) {
        Bundle args = new Bundle();
        args.putInt("msg", msg);
        WaitDialog dialog = new WaitDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // get text
        int msg = getArguments().getInt("msg");

        Dialog dialog = new ProgressDialog(getActivity());
        dialog.setTitle(R.string.dialog_progress_title);
        ((ProgressDialog) dialog).setMessage(getString(msg));
        dialog.setCancelable(false);
        return dialog;
    }
}
