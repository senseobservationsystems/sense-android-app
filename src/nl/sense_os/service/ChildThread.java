package nl.sense_os.service;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class ChildThread extends Thread {

	public Handler handler = null;
	private static final String TAG = "Child Thread";
	
	public void run() {
		Looper.prepare();

		handler = new Handler() {
			public void handleMessage(Message msg) {
				// process incoming messages here
				Log.d(TAG,"Message received in childThread:"+msg.toString());
			}
		};

		Looper.loop();
	}
}