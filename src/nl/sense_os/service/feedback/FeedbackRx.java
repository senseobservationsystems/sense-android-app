package nl.sense_os.service.feedback;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FeedbackRx extends BroadcastReceiver {

    private static final String TAG = "Sense Feedback BroadcastReceiver";
    public static final String ACTION_CHECK_FEEDBACK = "nl.sense_os.service.CheckFeedback";
    public static final int REQ_CHECK_FEEDBACK = 2;

    public int periodCheckSensor = 0;
    public String sensorName = null;
    public String actionAfterCheck = null;
    
    @Override
    public void onReceive(Context context, Intent intent) {
    	Log.e(TAG, "onReceive");

    	periodCheckSensor 	= intent.getIntExtra("period", (1000 * 60 * 2));
    	sensorName 			= intent.getStringExtra("sensor_name");
    	actionAfterCheck 	= intent.getStringExtra("broadcast_after");
    	
        /* set the next check broadcast */
        final Intent alarmIntent = new Intent(ACTION_CHECK_FEEDBACK);
        	alarmIntent.putExtra("period", periodCheckSensor);
        	alarmIntent.putExtra("sensor_name", sensorName);
        	alarmIntent.putExtra("broadcast_after", "actionAfterCheck");
        final PendingIntent alarmOp = PendingIntent.getBroadcast(context, REQ_CHECK_FEEDBACK,
                alarmIntent, 0);
        final long alarmTime = System.currentTimeMillis() + periodCheckSensor;
        final AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.cancel(alarmOp);
        mgr.set(AlarmManager.RTC_WAKEUP, alarmTime, alarmOp);

        /* start the feedback check task */
        Intent checkFeedback = new Intent(FeedbackChecker.ACTION_CHECK_FEEDBACK);
        checkFeedback.putExtra("sensor_name", sensorName);
        checkFeedback.putExtra("broadcast_after", actionAfterCheck);
        Log.e(TAG, "SensorName Rx: " + sensorName);
        
        ComponentName component = context.startService(checkFeedback);
        
        if (null == component) {
            Log.w(TAG, "Could not start feedback checker");
        }
    }
}
