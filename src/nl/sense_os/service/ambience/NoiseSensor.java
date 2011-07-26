/*
 ************************************************************************************************************
 *     Copyright (C)  2010 Sense Observation Systems, Rotterdam, the Netherlands.  All rights reserved.     *
 ************************************************************************************************************
 */
package nl.sense_os.service.ambience;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import nl.sense_os.service.Constants;
import nl.sense_os.service.MsgHandler;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class NoiseSensor extends PhoneStateListener {

    private class SoundStreamThread implements Runnable {

        @Override
        public void run() {

            try {
                // cameraDevice = android.hardware.Camera.open();
                // Parameters params = cameraDevice.getParameters();
                // String effect = "mono";
                // params.set("effect", effect);
                // cameraDevice.setParameters(params);
                // recorder.setCamera(cameraDevice);
                if (isCalling) {
                    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_UPLINK);
                } else {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                }
                // recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                // recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                final String fileName = recordFileName + fileCounter + ".3gp";
                fileCounter = (++fileCounter) % MAX_FILES;
                new File(recordFileName).createNewFile();
                String command = "chmod 666 " + fileName;
                Runtime.getRuntime().exec(command);
                recorder.setOutputFile(fileName);
                recorder.setMaxDuration(RECORDING_TIME_STREAM);
                recorder.setOnInfoListener(new OnInfoListener() {

                    @Override
                    public void onInfo(MediaRecorder mr, int what, int extra) {

                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                            try {
                                // recording is done, upload file
                                recorder.stop();
                                recorder.reset();
                                // wait until finished otherwise it will be overwritten
                                SoundStreamThread tmp = soundStreamThread;

                                // pass message to the MsgHandler
                                Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
                                i.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_MIC);
                                i.putExtra(MsgHandler.KEY_VALUE, fileName);
                                i.putExtra(MsgHandler.KEY_DATA_TYPE,
                                        Constants.SENSOR_DATA_TYPE_FILE);
                                i.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                                NoiseSensor.this.context.startService(i);

                                if (isEnabled && listenInterval == -1
                                        && tmp.equals(soundStreamThread)) {
                                    soundStreamThread = new SoundStreamThread();
                                    soundStreamHandler.post(soundStreamThread);
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                    }
                });

                recorder.prepare();
                recorder.start();

            } catch (final Exception e) {
                Log.d(TAG, "Error while recording sound:", e);
            }
        }
    }

    private static final String TAG = "Sense NoiseSensor";
    private static final String NAME_NOISE = "noise_sensor";
    private static final String NAME_MIC = "microphone";
    private static final int MAX_FILES = 60;
    private static final int DEFAULT_SAMPLE_RATE = 8000;
    private static final int RECORDING_TIME_NOISE = 2000;
    private static final int BUFFER_SIZE = DEFAULT_SAMPLE_RATE * 2 * 2; // samples per second * 2
                                                                        // seconds, 2 bytes
    private static final int RECORDING_TIME_STREAM = 60000;
    private AudioRecord audioRecord;
    private boolean isEnabled = false;
    private boolean isCalling = false;
    private int listenInterval; // Update interval in msec
    private Context context;
    private SoundStreamThread soundStreamThread = null;
    private Handler soundStreamHandler = new Handler(Looper.getMainLooper());
    private MediaRecorder recorder = null;
    private int fileCounter = 0;
    private String recordFileName = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/sense/micSample";
    private TelephonyManager telMgr;
    private Timer noiseStartTimer = new Timer();
    private Timer noiseProcessTimer = new Timer();

    public NoiseSensor(Context context) {
        this.context = context;
        this.telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * Disables the noise sensor, stopping the sound recording and unregistering it as phone state
     * listener.
     */
    public void disable() {
        // Log.v(TAG, "Noise sensor disabled...");

        isEnabled = false;
        pauseSampling();

        this.telMgr.listen(this, PhoneStateListener.LISTEN_NONE);
    }

    /**
     * Enables the noise sensor, starting the sound recording and registering it as phone state
     * listener.
     */
    public void enable(int interval) {
        // Log.v(TAG, "Noise sensor enabled...");

        listenInterval = interval;
        isEnabled = true;

        // registering the phone state listener will trigger a call to startListening()
        this.telMgr.listen(this, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * @return <code>true</code> if {@link #audioRecord} was initialized successfully
     */
    private boolean initAudioRecord() {
        // Log.v(TAG, "Initializing AudioRecord instance...");

        if (null != audioRecord) {
            Log.w(TAG, "AudioRecord object is already present! Releasing it...");
            // release the audioRecord object and stop any recordings that are running
            pauseSampling();
        }

        // create the AudioRecord
        try {
            if (isCalling) {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_UPLINK,
                        DEFAULT_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
            } else {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, DEFAULT_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to create the audiorecord!", e);
            return false;
        }

        if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            Log.w(TAG, "Failed to create AudioRecord!");
            Log.w(TAG,
                    "format: " + audioRecord.getAudioFormat() + " source: "
                            + audioRecord.getAudioSource() + " channel: "
                            + audioRecord.getChannelConfiguration() + " buffer size: "
                            + BUFFER_SIZE);
            return false;
        }

        // initialized OK
        return true;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        // Log.v(TAG, "Call state changed...");

        try {
            if (state == TelephonyManager.CALL_STATE_OFFHOOK
                    || state == TelephonyManager.CALL_STATE_RINGING) {
                isCalling = true;
            } else {
                isCalling = false;
            }

            pauseSampling();

            // recording while not calling is disabled
            if (isEnabled && state == TelephonyManager.CALL_STATE_IDLE && !isCalling) {
                startSampling();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in onCallStateChanged!", e);
        }
    }

    private void pauseSampling() {
        // Log.v(TAG, "Pause sampling the noise level...");

        try {
            // clear any old noise sensing threads
            noiseStartTimer.cancel();

            if (soundStreamThread != null) {
                soundStreamHandler.removeCallbacks(soundStreamThread);
                soundStreamThread = null;
            }

            stopRecording();

            if (listenInterval == -1 && recorder != null) {
                recorder.stop();
                recorder.reset(); // You can reuse the object by going back to setAudioSource() step
                // recorder.release(); // Now the object cannot be reused
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in pauseListening!", e);
        }
    }

    private void startSampling() {
        // Log.v(TAG, "Start sampling the noise level...");

        if (noiseStartTimer != null) {
            noiseStartTimer.cancel();
        }

        try {
            if (listenInterval == -1) {
                // create dir
                (new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/sense"))
                        .mkdir();
                recorder = new MediaRecorder();
                if (soundStreamThread != null) {
                    soundStreamHandler.removeCallbacks(soundStreamThread);
                }
                soundStreamThread = new SoundStreamThread();
                soundStreamHandler.post(soundStreamThread);
            } else {

                // schedule tasks to sample the noise
                noiseStartTimer = new Timer();
                noiseStartTimer.scheduleAtFixedRate(new TimerTask() {

                    @Override
                    public void run() {
                        startNoiseSample();
                    }
                }, 0, listenInterval);
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in startListening:" + e.getMessage());
        }
    }

    private void startNoiseSample() {

        if (isEnabled) {

            boolean init = initAudioRecord();

            if (init) {

                try {
                    Log.i(TAG, "Start recording noise...");
                    audioRecord.startRecording();

                    if (null != noiseProcessTimer) {
                        noiseProcessTimer.cancel();
                    }

                    // schedule task to stop recording and calculate the noise
                    noiseProcessTimer = new Timer();
                    noiseProcessTimer.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            processNoiseSample();
                            noiseProcessTimer.cancel();
                        }
                    }, RECORDING_TIME_NOISE);

                } catch (Exception e) {
                    Log.e(TAG, "Exception starting noise recording!", e);
                }

            } else {
                Log.w(TAG, "Did not start recording: AudioRecord could not be initialized!");
            }

        } else {
            // Log.d(TAG, "Did not start recording: noise sensor is disabled...");
        }
    }

    private void stopRecording() {

        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {

            try {
                audioRecord.stop();
                Log.i(TAG, "Stopped recording noise...");
            } catch (IllegalStateException e) {
                // audioRecord is probably already stopped..?
            }
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void processNoiseSample() {

        try {
            // calculate the noise power
            double dB = calculateDb();

            if (dB < 0) {
                // there was an error calculating the noise power
                Log.w(TAG, "There was an error calculating noise power. No new data point.");

            } else {
                // Log.v(TAG, "Sampled noise level: " + dB);

                // pass message to the MsgHandler
                Intent sensorData = new Intent(MsgHandler.ACTION_NEW_MSG);
                sensorData.putExtra(MsgHandler.KEY_SENSOR_NAME, NAME_NOISE);
                sensorData.putExtra(MsgHandler.KEY_VALUE, Double.valueOf(dB).floatValue());
                sensorData.putExtra(MsgHandler.KEY_DATA_TYPE, Constants.SENSOR_DATA_TYPE_FLOAT);
                sensorData.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                context.startService(sensorData);
            }

        } finally {
            stopRecording();
        }
    }

    /**
     * @return the noise power of the current buffer. In case of an error, -1 is returned.
     */
    private double calculateDb() {

        double dB = 0;
        try {
            if (!isEnabled) {
                Log.w(TAG, "Noise sensor is disabled, skipping noise power calculation...");
                return -1;
            }

            byte[] buffer = new byte[BUFFER_SIZE];

            int readBytes = 0;
            if (audioRecord != null) {
                readBytes = audioRecord.read(buffer, 0, BUFFER_SIZE);
            }

            if (readBytes < 0) {
                Log.e(TAG, "Error reading AudioRecord buffer: " + readBytes);
                return -1;
            }
            double ldb = 0;
            for (int x = 0; x < readBytes - 1; x = x + 2) {
                // it looks like little endian
                double val = buffer[x + 1] << 8 | buffer[x];
                ldb += val * val;
                // dB += Math.abs(buffer[x]);
            }

            ldb /= ((double) readBytes / 2);
            dB = (20.0 * Math.log10(Math.sqrt(ldb)));

        } catch (Exception e) {
            Log.e(TAG, "Exception calculating noise Db!", e);
            return -1;
        }

        return dB;
    }
}
