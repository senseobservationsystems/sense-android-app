/*
 ************************************************************************************************************
 *     Copyright (C)  2010 Sense Observation Systems, Rotterdam, the Netherlands.  All rights reserved.     *
 ************************************************************************************************************
 */
package nl.sense_os.service.external_sensors;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import nl.sense_os.service.MsgHandler;
import nl.sense_os.service.SenseDataTypes;
import nl.sense_os.service.SensorData.SensorNames;

import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * @author roelofvandenberg
 *
 */
public class OBD2Dongle {
    //static device specifics
	private static final String TAG = "OBD-II Interface Dongle";
	private static String deviceName = "TestOBD";
	private static final String deviceAdress = "";
	
	//static connection specifics
	private static final UUID serial_uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");;
    
	//device variables
	private final Context context;
    private boolean dongleenabled = false;
	private int updateInterval = 0;
    
	//connection thread variables
    private BluetoothAdapter btAdapter = null;
    private boolean streamEnabled = false;
	private final Handler connectHandler = new Handler(Looper.getMainLooper());
    private ConnectThread connectThread = null;
    private BluetoothSocket socket = null;
    
    //update thread variables
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private UpdateThread updateThread = null;
    private long lastSampleTime = 0;
	boolean[] pidAvailable; //whether or not certain PIDs are available for this car
	String vin = ""; //Vehicle Identification Number
    
	public OBD2Dongle(Context context) {
        this.context = context;
    }
	
	/**
	 * start reading the OBD2Dongle by adding a ConnectThread to the connectHandler
	 * @param interval in milliseconds
	 */
	public void start(int interval){
		this.setUpdateInterval(interval);
		this.setDongleEnabled(true);

		Thread t = new Thread() {
            @Override
            public void run() {
                // No check on android version, assume 2.1 or higher
                connectHandler.post(connectThread = new ConnectThread());
            }
        };
        this.connectHandler.post(t);
	}
	
    /**
     * stop reading the OBD2Dongle, also removing its threads from the connectHandler
     */
	public void stop() {
        this.setDongleEnabled(false);
        try {
        	// No check on android version, assume 2.1 or higher
       		if (connectThread != null) {
       			connectThread.stop();
       			connectHandler.removeCallbacks(connectThread);
       		}
        } catch (Exception e) {
            Log.e(TAG, "Exception in stopping Bluetooth scan thread:", e);
        }
    }

	/**
	 * 
	 * @return whether or not the OBD2Dongcheckle should be read
	 */
	public boolean dongleEnabled() {
        return dongleenabled;
    }

    /**
     * 
     * @param enable whether or not the OBD2Dongle should be read
     */
    public void setDongleEnabled(boolean enable) {
        this.dongleenabled = enable;
    }
	
    /**
     * 
     * @return updateInterval in milliseconds
     */
	public int getUpdateInterval() {
        return this.updateInterval;
    }

    /**
     * 
     * @param interval in milliseconds
     */
    public void setUpdateInterval(int interval) {
        this.updateInterval = interval;
    }
	
	public class ConnectThread implements Runnable{

		private BroadcastReceiver btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (!dongleEnabled()) {
                    return;
                }

                String action = intent.getAction();

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.STATE_OFF);
                    if (state == BluetoothAdapter.STATE_ON) {
                        stop();
                        connectHandler.post(connectThread = new ConnectThread());
                        return;
                    }
                }
            }
        };

        /*
         * Connect to the default BluetTooth adapter
         */
        public ConnectThread() {
            // send address
            try {
                btAdapter = BluetoothAdapter.getDefaultAdapter();
            } catch (Exception e) {
                Log.e(TAG, "Exception preparing Bluetooth scan thread:", e);
            }
        }

        @Override
        public void run() {
            if (dongleEnabled()) {
            	streamEnabled = false;            	
            	if (btAdapter.isEnabled()){
            		boolean foundDevice = false;

	                // check if there is a paired device with the name BioHarness
	                Set<android.bluetooth.BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
	                // If there are paired devices                    
	                if (pairedDevices.size() > 0) {
	                    // Search for the correct Bluetooth Devices
	                    for (BluetoothDevice device : pairedDevices) {
							// TODO make device name and address device specific 
	                        if (device.getName().startsWith("") && device.getAddress().startsWith("00:07:80")) {
	                            // Get a BluetoothSocket to connect with the given BluetoothDevice
	                            try {
	                                socket = device.createRfcommSocketToServiceRecord(serial_uuid);
	                                socket.connect();
	                                updateHandler.post(updateThread = new UpdateThread());
	                                foundDevice = true;
	                            } catch (Exception e) {
	                                Log.e(TAG, "Error connecting to OBD2Dongle device: " + e.getMessage());
	                            }
	                        }
	                    }
	                }
	                if (!foundDevice) {
	                    // Log.v(TAG, "No Paired BioHarness device found. Sleeping for 10 seconds");
	                    connectHandler.postDelayed(connectThread = new ConnectThread(), 10000);
	                }
	            } else if (btAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) {
	                // listen for the adapter state to change to STATE_ON
	                context.registerReceiver(btReceiver, new IntentFilter(
	                        BluetoothAdapter.ACTION_STATE_CHANGED));
	            } else {
	                // ask user for permission to start bluetooth
	                // Log.v(TAG, "Asking user to start bluetooth");
	                Intent startBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	                startBt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	                context.startActivity(startBt);
	
	                // listen for the adapter state to change to STATE_ON
	                context.registerReceiver(btReceiver, new IntentFilter(
	                        BluetoothAdapter.ACTION_STATE_CHANGED));
	            }
            } else {
                stop();
            }
        }

        public void stop() {
            try {
                // Log.v(TAG, "Stopping the BioHarness service");
                updateHandler.removeCallbacks(updateThread);
                socket.close();

                context.unregisterReceiver(btReceiver);

            } catch (Exception e) {
                Log.e(TAG, "Error in stopping OBD2Dongle service" + e.getMessage());
            }
        }

	}
	
	public class UpdateThread extends Thread{
        private InputStream sockInputStream;
        private OutputStream sockOutputStream;
		
        /**
         * Create an UpdateThread object which has the sockInStream and sockOutStream connected with the BlueTooth socket 
         */
        public UpdateThread() {
            if (sockInputStream == null || sockOutputStream == null) {
                // using temporary objects because member streams are final
            	InputStream tempInputStream = null;
                OutputStream tempOutputStream = null;

                try {
                    tempInputStream = socket.getInputStream();
                    tempOutputStream = socket.getOutputStream();
                } catch (Exception e) {
                    Log.e(TAG, "Error in update thread constructor:" + e.getMessage());
                }
                sockInputStream = tempInputStream;
                sockOutputStream = tempOutputStream;
            }
        }
        
        
        @Override
        public void run() {
        	if(dongleEnabled()){
        		try{
        			if(!streamEnabled){
        				//initialize the datastream by checking available PIDs and VIN
        				streamEnabled = initializeDataStream();
        				updateHandler.post(updateThread = new UpdateThread());
        			}
        			//TODO not necessary to check connection alive now, is it?
                    if (System.currentTimeMillis() > lastSampleTime + updateInterval) {
                    	//invoke dynamic data gathering subroutines
                    	updateDTCStatus();
                    	updateFuelStatus();
                    	updateEngineLoad();
                    	updateEngineCoolant();
                    	updateFuelPercentTrim();
                    	updateFuelPressure();
                    	updateIntakeManifoldPressure();
                    	updateEngineRPM();
                    	updateVehicleSpeed();
                    	updateTimingAdvance();
                    	updateIntakeAirTemperature();
                    	updateMAFAirFlowRate();
                    	updateThrottlePosition();
                    	updateCommandedSecondaryAirStatus();
                    	updateOxygenSensors();
                    	updateAuxiliaryInput();
                    	updateRunTime();
                    	//TODO these methods cover the PIDs until Mode 1 PID 0x1F
        			}
                    //update a new upDateThread every second
                    updateHandler.postDelayed(updateThread = new UpdateThread(), 1000);
        		} catch (Exception e) {
        			Log.e(TAG, "Error in receiving BioHarness data:" + e.getMessage());
        			e.printStackTrace();
        			// re-connect
        			connectHandler.postDelayed(connectThread = new ConnectThread(), 1000);
        		}
        	} else
        		cancel();
        	}
        
	        /* Call this from the main Activity to shutdown the connection */
			public void cancel() {
			    try {
			        Log.i(TAG, "Stopping the OBD2Dongle service");
		            socket.close();
			    } catch (Exception e) {
			        Log.e(TAG, e.getMessage());
			    }
			}

        
	        /**
	         * 
	         * @return whether or not the initialization was successful
	         */
	        private boolean initializeDataStream(){
	        	//initialize pidAvailable
	        	pidAvailable = new boolean[(byte) 0x60 + 1];
	        	
	        	boolean[] current;
	        	//Mode01, PID00/PID20/PID40 : check if PIDs 0x01 - 0x60 are available
	        	for(byte index = 0x00; index <= 0x60; index += 0x20){
	        		current = queryBit((byte) 0x01, (byte) 0x00);
	        		if(current!=null) pidAvailable[index] = true;
	        		System.arraycopy(current, 0, pidAvailable, index + 1, current.length);
	        	}
	        	
	        	deviceName += " (" + getOBDStandards() + ")";
	        	
	        	//TODO add code to find out about the VIN
	        	//found at mode 09, PID 01 and 02
	        	//using the ISO 15765-2 protocol 
	        	
	        	return true;
	        }
        
	        /**
			 * 
			 * @param mode indicating mode of operation as described in the latest OBD-II standard SAE J1979
			 * @param PID coded standard OBD-II PID as defined by SAE J1979
			 * @return the data bytes found in the OBD-II response
			 */
	        private byte[] queryByte(byte mode, byte PID){
	        	try {
	                sockOutputStream.write(new byte[]{0x02, mode, PID, 0x00, 0x00, 0x00, 0x00, 0x00});
	                byte[] buffer = new byte[8];
	                sockInputStream.read(buffer);
	                if((buffer[1] - (byte) 0x40) == mode && buffer[2] == PID && buffer[0]>2){
	                	byte[] result = new byte[buffer[0]-2];
	                	for(byte i = 0; i<result.length; i++){
	                		result[i] = buffer[i+3];
	                	}
	                	return result;
	                }
	            } catch (Exception e) {
	                Log.e(TAG, "Error in exchanging data:" + e.getMessage());
	                return null;
	            }
	            return null;
	        }
	        
	        /**
	         * 
			 * @param mode indicating mode of operation as described in the latest OBD-II standard SAE J1979
			 * @param PID coded standard OBD-II PID as defined by SAE J1979
			 * @return the bit representation of the data found in the OBD-II response
	         */
	        private boolean[] queryBit(byte mode, byte PID){
	        	byte[] input = queryByte(mode, PID);
	        	boolean[] result = new boolean[input.length * 8];
	        	for(byte byteIndex = 0; byteIndex<input.length; byteIndex++){
	        		for(byte bitIndex = 0; bitIndex<8; bitIndex++){
	        			result[byteIndex*8+bitIndex] = ((input[byteIndex] & (byte) (bitIndex+1)^2) != (byte) 0x00); 
	        		}
	        	}
	        	return result;
	        }
	        
	        /**
	         * 
	         * @param sensor_name a sensor name String gotten from the SensorData class
	         * @param value prepared object
	         */
	        private void sendIntent(String sensor_name, JSONObject value){
                Intent i = new Intent(MsgHandler.ACTION_NEW_MSG);
                i.putExtra(MsgHandler.KEY_SENSOR_NAME, sensor_name);
                i.putExtra(MsgHandler.KEY_SENSOR_DEVICE, "OBD2Dongle " + deviceName);
                i.putExtra(MsgHandler.KEY_VALUE, value.toString());
                i.putExtra(MsgHandler.KEY_DATA_TYPE, SenseDataTypes.JSON);
                i.putExtra(MsgHandler.KEY_TIMESTAMP, System.currentTimeMillis());
                context.startService(i);
	        }
	        
	        
	        /**
	    	 * 
	    	 * @return whether or not the call was successful
	    	 */
	        private boolean updateDTCStatus(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x01;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
		        	//Query for "monitor status since DTCs cleared".
	        		boolean[] response = queryBit(mode, PID);
	        		if(response != null){
	        			try {
	        				JSONObject result = new JSONObject();
							result.put("MIL on", response[0*8 + 7]);
		        			//bit B readout
		        			result.put("Misfire test available", response[1*8 + 0]);
		        			result.put("Fuel System test available", response[1*8 + 1]);
		        			result.put("Components test available", response[1*8 + 2]);
		        			result.put("Misfire test complete", response[1*8 + 4]);
		        			result.put("Fuel System test complete", response[1*8 + 5]);
		        			result.put("Components test complete", response[1*8 + 6]);
		        			//when Compression ignition monitors supported
		        			if(response[1*8+3]){
		        				result.put("Type of ignition monitors supported", "compression" );
			        			result.put("Catalyst test available", response[2*8 + 0]);
			        			result.put("Heated Catalyst test available", response[2*8 + 1]);
			        			result.put("Evaporative System test available", response[2*8 + 2]);
			        			result.put("Secondary Air System test available", response[2*8 + 3]);
			        			result.put("A/C Refrigerant test available", response[2*8 + 4]);
			        			result.put("Oxygen Sensor test available", response[2*8 + 5]);
			        			result.put("Oxygen Sensor Heater test available", response[2*8 + 6]);
			        			result.put("ERG System test available", response[2*8 + 7]);
			        			result.put("Catalyst test complete", response[3*8 + 0]);
			        			result.put("Heated Catalyst test complete", response[3*8 + 1]);
			        			result.put("Evaporative System test complete", response[3*8 + 2]);
			        			result.put("Secondary Air System test complete", response[3*8 + 3]);
			        			result.put("A/C Refrigerant test complete", response[3*8 + 4]);
			        			result.put("Oxygen Sensor test complete", response[3*8 + 5]);
			        			result.put("Oxygen Sensor Heater test complete", response[3*8 + 6]);
			        			result.put("ERG System test complete", response[3*8 + 7]);		        			
		        			}
		        			//when Spark ignition monitors supported
		        			else{
		        				result.put("Type of ignition monitors supported", "spark" );
			        			result.put("NMHC Cat test available", response[2*8 + 0]);
			        			result.put("NOx/SCR Monitor test available", response[2*8 + 1]);
			        			result.put("Boost Pressure test available", response[2*8 + 3]);
			        			result.put("Exhaust Gas Sensor test available", response[2*8 + 5]);
			        			result.put("PM filter monitoring test available", response[2*8 + 6]);
			        			result.put("EGR and/or VVT System test available", response[2*8 + 7]);
			        			result.put("NMHC Cat test complete", response[3*8 + 0]);
			        			result.put("NOx/SCR Monitor test complete", response[3*8 + 1]);
			        			result.put("Boost Pressure test complete", response[3*8 + 3]);
			        			result.put("Exhaust Gas Sensor test complete", response[3*8 + 5]);
			        			result.put("PM filter monitoring test complete", response[3*8 + 6]);
			        			result.put("EGR and/or VVT System test complete", response[3*8 + 7]);
		        			}
		        			sendIntent(SensorNames.MONITOR_STATUS, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateDTCStatus:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}
	        
	        /**
	    	 * 
	    	 * @return whether or not the call was successful
	    	 */
	    	private boolean updateFuelStatus(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x03;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		boolean[] response = queryBit(mode, PID);
	        		if(response != null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			//A request for this PID returns 2 bytes of data. 
			        		//The first byte describes fuel system #1.
			        		//Only one bit should ever be set per system.
		        			for(int system = 1; system<3; system++){
			        			int numtrue = 0;
			        			int loctrue = -1;
			        			for(int index = Byte.SIZE * (system-1); index<((system-1)*Byte.SIZE)+8; index++){
			        				if(response[index]){
			        					numtrue += 1;
			        					loctrue = index;
			        				}
			        			}
			        			//only use result when valid
			        			if(numtrue == 1){
			        				String value = ""; 
			        				switch (loctrue){
				        				case 0: 
				        					value = "Open loop due to insufficient engine temperature";
				        				case 1: 
				        					value = "Closed loop, using oxygen sensor feedback to determine fuel mix";
				        				case 2: 
				        					value = "Open loop due to engine load OR fuel cut due to deacceleration";
				        				case 3: 
				        					value = "Open loop due to system failure";
				        				case 4: 
				        					value = "Closed loop, using at least one oxygen sensor but there is a fault in the feedback system";
				        				default:
				        					value = "unknown";
			        				}
			        				String name = String.format("Fuel system #%d status", system); 
			        				result.put(name, value);
			        			}
		        			}
		        			sendIntent(SensorNames.FUEL_SYSTEM_STATUS, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateFuelStatus:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}
	    	
	    	
	    	/**
	    	 * 
	    	 * @return whether or not the call was successful
	    	 */
	    	private boolean updateEngineLoad(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x04;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			double value = ((double) response[0]) * 100d / 255d;
	        				result.put("Calculated engine load value (\u0025)", value);
	        				sendIntent(SensorNames.ENGINE_LOAD, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateEngineLoad:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}
    	
	    	private boolean updateEngineCoolant(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x05;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
	        			int value = ((int) response[0]) - 40;
        				result.put("temperature (\u00B0C)", value);
        				sendIntent(SensorNames.ENGINE_COOLANT, result);
	                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateEngineCoolant:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}

	    	private boolean updateFuelPercentTrim(){
	        	byte mode = (byte) 0x01;
	        	byte[] PIDs = new byte[]{0x06, 0x07, 0x08, 0x09};
	        	JSONObject result = new JSONObject();
	        	for(byte PID : PIDs){
		        	if(pidAvailable[PID]){
		        		byte[] response = queryByte(mode, PID);
		        		if(response!=null){
		        			try{
		        				double value = (((double) response[0]) - 128d) * (100d/128d);
			        			switch(PID){
			        				case 0x06:
			        					result.put("Short term fuel % trim—Bank 1", value);
			        				case 0x07:
			        					result.put("Long term fuel % trim—Bank 1", value);
			        				case 0x08:
			        					result.put("Short term fuel % trim—Bank 2", value);
			        				case 0x09:
			        					result.put("Long term fuel % trim—Bank 2", value);
			        			}
							} catch (JSONException e) {
			        			Log.e(TAG, "Error in updateFuelPercentTrim:" + e.getMessage());
			        			e.printStackTrace();
							}
		        		}
		        	}
	        	}
	        	if(result.length()>0){
	        		sendIntent(SensorNames.ENGINE_COOLANT, result);
                    return true;
	        	}
	        	return false;
	    	}	    	
	    	
	    	private boolean updateFuelPressure(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x0A;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			int value = ((int) response[0]) * 3;
	        				result.put("Fuel pressure (kPa (gauge))", value);
	        				sendIntent(SensorNames.FUEL_PRESSURE, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateFuelPressure:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}
	    	
	    	private boolean updateIntakeManifoldPressure(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x0B;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			byte value = response[0];
		    				result.put("Fuel pressure (kPa (gauge))", value);
		    				sendIntent(SensorNames.INTAKE_PRESSURE, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateIntakeManifoldPressure:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}

	    	private boolean updateEngineRPM(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x0C;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			double value = ((((double) response[0])*256d)+((double) response[1]))/4d;
		    				result.put("Engine RPM (rpm)", value);
		    				sendIntent(SensorNames.ENGINE_RPM, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateEngineRPM:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;	    		
	    	}
	    	
	    	private boolean updateVehicleSpeed(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x0D;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			byte value = response[0];
		    				result.put("Vehicle speed (km/h)", value);
		    				sendIntent(SensorNames.VEHICLE_SPEED, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateVehicleSpeed:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;	
	    	}
	    	
	    	private boolean updateTimingAdvance(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x0E;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
		        			JSONObject result = new JSONObject();
		        			double value = ((double) response[0])/2d - 64d;
		    				result.put("Timing advance (\u00B0 relative to #1 cylinder)", value);
		    				sendIntent(SensorNames.TIMING_ADVANCE, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateTimingAdvance:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;	    		
	    	}
	    	
	    	private boolean updateIntakeAirTemperature(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x0F;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			int value = (int) response[0] - 40;
		    				result.put("Intake air temperature (\u00B0C)", value);
		    				sendIntent(SensorNames.INTAKE_TEMPERATURE, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateIntakeAirTemperature:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;	    		
	    	}

	    	private boolean updateMAFAirFlowRate(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x10;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			double value = (((double) response[0])*256d + ((double)response[1]))/100d;
		    				result.put("MAF air flow rate (gram/sec)", value);
		    				sendIntent(SensorNames.MAF_AIRFLOW, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateMAFAirFlowRate:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;	    		
	    	}
	    	
	    	private boolean updateThrottlePosition(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x11;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response!=null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			double value = ((double) response[0])*100d/255d;
		    				result.put("Throttle Position (%)", value);
		    				sendIntent(SensorNames.THROTTLE_POSITION, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateThrottlePosition:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;	    		
	    	}
	    	
	    	private boolean updateCommandedSecondaryAirStatus(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x12;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		boolean[] response = queryBit(mode, PID);
	        		if(response != null){
	        			try{
	        				JSONObject result = new JSONObject();
			        		//Only one bit should ever be set per system.
		        			int numtrue = 0;
		        			int loctrue = -1;
		        			for(int index = 0; index<+8; index++){
		        				if(response[index]){
		        					numtrue += 1;
		        					loctrue = index;
		        				}
		        			}
		        			//only use result when valid
		        			if(numtrue == 1){
		        				String value = ""; 
		        				switch (loctrue){
			        				case 0: 
			        					value = "Upstream of catalytic converter";
			        				case 1: 
			        					value = "Downstream of catalytic converter";
			        				case 2: 
			        					value = "From the outside atmosphere or off";
			        				default:
			        					value = "unknown";
		        				}
		        				result.put("Commanded secondary air status", value);
		        			}
		        			sendIntent(SensorNames.AIR_STATUS, result);
		                    return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateCommandedSecondaryAirStatus:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;	    		
	    	}
	    	
	    	private boolean updateOxygenSensors(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x13;
	        	if(pidAvailable[PID]){
	        		boolean[] sensorspresent = queryBit(mode, PID);
	        		if(sensorspresent != null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			for(byte index = 0; index<8; index++){
		        				if(sensorspresent[index]){
		        					byte[] current = queryByte(mode, (byte) (PID+index+0x01));
		        					if(current!=null){
			        					int bank = index<4?1:2;
				        				int sensor = index % 4;
			        					String name = String.format("Bank %d, Sensor %d: Oxygen sensor voltage", bank, sensor);
			        					double value = ((double)current[0]) / 200d; 
			        					result.put(name, value);
			        					if(current[1] != 0xFF){
				        					name = String.format("Bank %d, Sensor %d: Short term fuel trim", bank, sensor);
			        						value = (((double) current[1]) - 128d) * (100d/128d);
			        						result.put(name, value);
			        					}
		        					}
		        				}
		        			}
		    	        	if(result.length()>0){
		    	        		sendIntent(SensorNames.OXYGEN_SENSORS, result);
		                        return true;
		    	        	}
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateOxygenSensors:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}
	    	
	    	private String getOBDStandards(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x1C;
	        	//Check whether or not query of this PID is possible 
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response != null){
	        			String value;
	        			switch (response[0]){
        					case 0x01: 
	        					value = "OBD-II as defined by the CARB";
	        				case 0x02: 
	        					value = "OBD as defined by the EPA";
	        				case 0x03: 
	        					value = "OBD and OBD-II";
	        				case 0x04: 
	        					value = "OBD-I";
	        				case 0x05: 
	        					value = "Not meant to comply with any OBD standard";
	        				case 0x06: 
	        					value = "EOBD (Europe)";
	        				case 0x07: 
	        					value = "EOBD and OBD-II";
	        				case 0x08: 
	        					value = "EOBD and OBD";
	        				case 0x09: 
	        					value = "EOBD, OBD and OBD II";
	        				case 0x0A: 
	        					value = "JOBD (Japan)";
	        				case 0x0B: 
	        					value = "JOBD and OBD II";
	        				case 0x0C: 
	        					value = "JOBD and EOBD";
	        				case 0x0D: 
	        					value = "JOBD, EOBD, and OBD II";			        					
	        				default:
	        					value = "unknown";
        				}
	        			return value;
	        		}
	        	}
	        	return null;
	    	}
	    	
	    	private boolean updateAuxiliaryInput(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x1E;
	        	if(pidAvailable[PID]){
	        		boolean[] response = queryBit(mode, PID);
	        		if(response != null){
	        			try{
		        			JSONObject result = new JSONObject();
							result.put("Auxiliary input status", response[0]);
	    	        		sendIntent(SensorNames.AUXILIARY_INPUT, result);						
							return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateAuxiliaryInput:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
	        	return false;
	    	}

	    	private boolean updateRunTime(){
	        	byte mode = (byte) 0x01;
	        	byte PID = (byte) 0x1F;
	        	if(pidAvailable[PID]){
	        		byte[] response = queryByte(mode, PID);
	        		if(response != null){
	        			try{
	        				JSONObject result = new JSONObject();
		        			int value = (((int) response[0]) * 256) + response[1];
							result.put("Run time since engine start (seconds)", value);
	    	        		sendIntent(SensorNames.RUN_TIME, result);						
							return true;
						} catch (JSONException e) {
		        			Log.e(TAG, "Error in updateRunTime:" + e.getMessage());
		        			e.printStackTrace();
						}
	        		}
	        	}
        		return false;
	    	}
	    	
    		//TODO these methods cover the PIDs until Mode 1 PID 0x1F
		}
    }
