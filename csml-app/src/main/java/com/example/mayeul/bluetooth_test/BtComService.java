/*
 *   BtComService.java contains the background service that handles
 *   communication with the bluetooth adapter.
 *   Copyright (C) 2015-2017  Mayeul Cantan <mayeul.cantan@gmail.com>
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
 package com.example.mayeul.bluetooth_test;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import android.app.IntentService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Created by mayeul on 30/10/15.
 */
public class BtComService extends IntentService{
    private static BluetoothAdapter mBluetoothAdapter = null;
    private static BluetoothSocket btSocket = null;
    private static OutputStream outStream = null;
    private static InputStream inStream = null;

    private static final boolean D = false; // D for Debug
    private static final String TAG = "CSMLSPPSERVICE";
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Generic spp UUID
    private static boolean running = false;

    private static String received_string;
    private boolean nextIsPwmValue=false;
    private boolean messageIsReady = false;

    private void reportStatus(String status) {
        Intent localIntent = new Intent(getString(R.string.BROADCAST_RECEIVER_INFO))
                .putExtra(getString(R.string.EXTENDED_DATA_STATUS), status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private char readChar()
    {
        char tmp;
        try {
            tmp= (char)inStream.read();
            if(D)
                Log.i(TAG, "Received " + String.valueOf(tmp));
        }
        catch (IOException e){
            running = false; // simple workaround for now, should be broadcasting this failure
            // TODO : after a failure is detected, investigate the cause & try to reconnect
            Log.e(TAG, "IN RECEIVER THREAD : read failed", e);
            reportStatus("DISCONNECTED");
            running=false;
            return 0;
        }
        return tmp;
    }

    private void processChar(char next_char)
    {
	// TODO: add a checksum mechanism
        if(nextIsPwmValue)
        {
            nextIsPwmValue=false;
            received_string += String.valueOf((int)next_char); // TODO: check if this is a valid cast (unsigned and such)
            messageIsReady = true;
        }
        else if(next_char == 'A' || next_char == 'R') // Alert or regular read
        {
            nextIsPwmValue=true; // next byte contains the pwmValue
            received_string += String.valueOf(next_char);
        }
    }

    private void sendString()
    {
        Intent localIntent = new Intent(getString(R.string.BROADCAST_RECEIVER_INFO))
                .putExtra(getString(R.string.EXTENDED_DATA_STATUS), "R")
                .putExtra(getString(R.string.EXTENDED_DATA_DATA), received_string);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        received_string = "";
    }



    @Override
    protected void onHandleIntent(Intent workIntent){
        String dataString = workIntent.getDataString();
        if (D)
            Log.e(TAG, "+ SERVICE RECEIVED:" + dataString +" +");
        if(dataString.startsWith("START")) // "START=", followed by the MAC address
        {
            if(running){
                if(D)
                    Log.e(TAG,"Service is already running");
                return; // Service is already running
            }

            if( startCOM( dataString.substring("START=".length()) ) <0)
                return;

            while(running)
            {
                char nextChar=readChar();
                if(!running)
                    return;

                processChar(nextChar);

                if(messageIsReady) // Correctly received the whole string
                {
                    sendString();
                    messageIsReady =false;
                }
            }
        }
        if(dataString.contentEquals("STOP"))
            closeConnection();
    }

    private int startCOM(String address) {
        if(mBluetoothAdapter==null)
            return -6;
        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "+ startCOM: BLUETOOTH NOT ENABLED, ABORTING +");
            Intent localIntent = new Intent(getString(R.string.BROADCAST_RECEIVER_INFO))
                    .putExtra(getString(R.string.EXTENDED_DATA_STATUS), "ASK");
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            return -1;
        }
        if (D) {
            Log.e(TAG, "+ startCOM: SERVICE STARTING +");
            Log.e(TAG, "+ startCOM: ABOUT TO ATTEMPT CLIENT CONNECT +");
        }

        // Lookup the device with its mac address

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        // This UUID corresponds to a bluetooth SPP service
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            Log.e(TAG, "startCOM: Socket creation failed.", e);
            return -6;
        }

        // Cancels discovery for now; as it's useless
        mBluetoothAdapter.cancelDiscovery();

        try {
            btSocket.connect();
            Log.e(TAG, "startCOM: BT connection established, data transfer link open.");
        } catch (IOException e) {
            try {
                btSocket.close();
                return -4;
            } catch (IOException e2) {
                Log.e(TAG,
                        "startCOM: Unable to close socket during connection failure", e2);
                return -5;
            }
        }

        // Create a data stream so we can talk to the module
        if (D)
            Log.e(TAG, "+ startCOM ABOUT TO SAY SOMETHING TO SERVER +");

        try {
            outStream = btSocket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "startCOM: Output stream creation failed.", e);
            return -2;
        }

        try {
            inStream = btSocket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "startCOM: Input stream creation failed.", e);
            return -2;
        }

        received_string = "";

        String message = "L"; // For "Listening"
        byte[] msgBuffer = message.getBytes();
        try {
            outStream.write(msgBuffer);
        } catch (IOException e) {
            Log.e(TAG, "startCOM: Exception during write.", e);
            return -3;
        }

        running=true;
        reportStatus("CONNECTED");
        return 0;
    }
    public BtComService(){
        super("BtComService");

        if (D)
            Log.e(TAG, "+++ SERVICE CONSTRUCTOR +++");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this,
                    "Bluetooth is not available.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            if (D)
                Log.e(TAG, "+++ DONE IN SERVICE CONSTRUCTOR, DIDN'T GET LOCAL BT ADAPTER +++");
        } else {
            if (D)
                Log.e(TAG, "+++ DONE IN SERVICE CONSTRUCTOR, GOT LOCAL BT ADAPTER +++");
        }


    }

    private void closeConnection() {
        reportStatus("DISCONNECTED");
        running=false;
        if (mBluetoothAdapter==null || !mBluetoothAdapter.isEnabled()) {
            return;
        }
        if (D)
            Log.e(TAG, "- Closing connection -");

        String message = "C"; // For "Closing"
        byte[] msgBuffer = message.getBytes();
        if (outStream != null) {
            try {
                outStream.write(msgBuffer);
            } catch (IOException e) {
                Log.e(TAG, "Closing connection: Exception during write.", e);
            }
        }

        if (outStream != null) {
            try {
                outStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Closing connection: Couldn't flush output stream.", e);
            }
        }

        if (inStream != null) {
            try {
                inStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Closing connection: Couldn't close input stream.", e);
            }
        }
        if(btSocket!=null) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                Log.e(TAG, "Closing connection: Unable to close socket.", e2);
            }
        }
    }
}

