/*
 *   MainActivity.java contains the main, user-facing activity. It handles
 *   communication with the background service, as wel as the various user
 *   prompts and interfaces.
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.support.v7.widget.Toolbar;
import android.graphics.Color;
import android.view.Window;
import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CSMLSPPAPP";
    private static final String default_address ="98:D3:31:40:3A:73";
    private static final boolean D = false;
    private String temp_message;
    private static final int REQUEST_CODE_ENABLE_BT = 1;
    TextView textView;
    private static ServiceListener mServiceListener = null;
    private static IntentFilter receiverStatusFilter = null;
    private static boolean mIsConnected = false;
    private static boolean asking_Bluetooth=false;
    private static String address;
    List<String> mBonded_addresses;

    private void askToEnableBluetooth() {
        if(asking_Bluetooth)
            return;
        asking_Bluetooth=true;
        Toast.makeText(this,
                "Please enable your Bluetooth",
                Toast.LENGTH_LONG).show();
        if (D)
            Log.e(TAG, "-> ASKED USER TO ENABLE BT");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_CODE_ENABLE_BT);
    }

    public void select_MAC(View view) {
        setContentView(R.layout.content_select_device);
        populateDevicesList();
    }

    private void populateDevicesList(){
       BluetoothAdapter BTA = BluetoothAdapter.getDefaultAdapter();
        if(BTA==null) // No bluetooth
            return;
        Set<BluetoothDevice> pairedDevices = BTA.getBondedDevices();

        if(pairedDevices.size() < 1)
        {
            Toast.makeText(this,
                    "No paired device",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Spinner MAC_spinner = (Spinner) findViewById(R.id.MAC_spinner);
        List<String> bondedDevicesList = new ArrayList<String>();
        mBonded_addresses = new ArrayList<String>();

        for (BluetoothDevice device : pairedDevices){
            bondedDevicesList.add(device.getName());
            mBonded_addresses.add(device.getAddress());
        }

        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, bondedDevicesList);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.select_dialog_item);

        MAC_spinner.setAdapter(spinnerArrayAdapter);

    }
    public void set_MAC(View view){
        int pos = ((Spinner) findViewById(R.id.MAC_spinner)).getSelectedItemPosition();
        String MAC_address = mBonded_addresses.get(pos);
        SharedPreferences.Editor prefs=getPreferences(MODE_PRIVATE).edit();

        prefs.putString(getString(R.string.PREFS_MAC), MAC_address);
        prefs.apply();
        address=MAC_address;

        setContentView(R.layout.activity_main);
        updateToolbar();

        if(D)
            Log.i(TAG,"Changed MAC for "+MAC_address);

        stopListenerService();
        startListenerService();
    }

    public void cancel_MAC(View view){
        setContentView(R.layout.activity_main);
        updateToolbar();
    }
    private void startListenerService()
    {
        Intent COMSERVICEINTENT = new Intent(getBaseContext(),BtComService.class);//previously "this" instead of "basecontext"
        COMSERVICEINTENT.setData(Uri.parse("START="+address));
        this.startService(COMSERVICEINTENT);
    }

    private void stopListenerService()
    {
        Intent COMSERVICEINTENT = new Intent(getBaseContext(),BtComService.class);//previously "this" instead of "basecontext"
        COMSERVICEINTENT.setData(Uri.parse("STOP"));
        this.startService(COMSERVICEINTENT);
    }

    private void bringToForeground()
    {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Needed if starting from a service
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        startActivity(intent);
        // TODO : create a new activity for incoming alerts, and start it there
        // This is currently emulated by a call to the following showNewAlert() function
    }

    private void showNewAlert(String str){
        AlertDialog.Builder alert=new AlertDialog.Builder(this);
        alert.setMessage("Alert: "+str);
        alert.setTitle("Incoming Alert");
        alert.setPositiveButton("OK", null);
        alert.create().show();
    }
    private void updateToolbar()
    {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(mIsConnected)
            toolbar.setTitle("Connected");
        else
            toolbar.setTitle("Not connected");
    }

    private void unlockScreen()
    {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        //lockScreen() must be called to clear the flags, after user interaction.
        // currently put it in onStop()
        bringToForeground();
    }
    private void lockScreen() {
        Window w = getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            asking_Bluetooth = savedInstanceState.getBoolean(getString(R.string.BUNDLE_ASKED_BT));
            mIsConnected = savedInstanceState.getBoolean(getString(R.string.BUNDLE_IS_CONNECTED));

            if(D)
                Log.i(TAG,"Restored bundle : ASK="+String.valueOf(asking_Bluetooth)+" CON="+String.valueOf(mIsConnected));
        }

        SharedPreferences prefs=getPreferences(MODE_PRIVATE);

        address=prefs.getString(getString(R.string.PREFS_MAC), default_address);

        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.textView);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitleTextColor(Color.WHITE);

        if(mIsConnected)
            toolbar.setTitle("Connected");
        else
            toolbar.setTitle("Not connected");

        setSupportActionBar(toolbar);
        updateToolbar();

        if(mServiceListener==null) {
            if(D)
                Log.i(TAG,"Starting ServiceListener");
            mServiceListener = new ServiceListener();
        }
        if(receiverStatusFilter==null) {
            IntentFilter receiverStatusFilter = new IntentFilter(
                    getString(R.string.BROADCAST_RECEIVER_INFO));
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    mServiceListener,
                    receiverStatusFilter);
        }


        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(!mIsConnected&&!asking_Bluetooth)
                    startListenerService();
            }
        },2000, 1000);

        if (D)
            Log.e(TAG, "++ STARTING APPLICATION ++");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == REQUEST_CODE_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                if (D)
                    Log.e(TAG, "-> GOT LOCAL BT ADAPTER");
                startListenerService(); //restart service
            } else {
                if (D)
                    Log.e(TAG, "USER REFUSED TO ENABLE BLUETOOTH, SHUTTING DOWN");
                finish();
                // TODO : Also listen to the ACTION_STATE_CHANGED broadcast intent
            }
            asking_Bluetooth=false;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    public void onStart() {
        super.onStart();
        if (D)
            Log.e(TAG, "++ APPLICATION ON START, connection status="+String.valueOf(mIsConnected)+" ++");
        if(!mIsConnected&&!asking_Bluetooth)
            startListenerService();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (D)
            Log.e(TAG, "-- STOPPING APP --");
        lockScreen();
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(getString(R.string.BUNDLE_ASKED_BT), asking_Bluetooth);
        savedInstanceState.putBoolean(getString(R.string.BUNDLE_IS_CONNECTED), mIsConnected);
        super.onSaveInstanceState(savedInstanceState);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (D)
            Log.e(TAG, "--- DESTROYING APP ---");
        //stopListenerService(); //In fact, this should never be called
    }

    private class ServiceListener extends BroadcastReceiver {
        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().contentEquals(getString(R.string.BROADCAST_RECEIVER_INFO))) {
                if (intent.getStringExtra(getString(R.string.EXTENDED_DATA_STATUS)).contentEquals("R")) // received
                {
                    temp_message = intent.getStringExtra(getString(R.string.EXTENDED_DATA_DATA));
                    if (D)
                        Log.e(TAG, "Received: " + temp_message);
                    char marker = temp_message.charAt(0);
                    // The pwm value, which is the remainder of the message
                    temp_message = "Duty cycle is "+String.valueOf((100*Integer.parseInt(temp_message.substring(1)))/255)+"%";
                    if(marker == 'A') // Only display if that's an actual alert
                    {
                        unlockScreen(); // Unlock screen and bring to foreground
                        showNewAlert(temp_message);
                        temp_message = "Alert! " + temp_message;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.append(temp_message);
                            textView.append("\r\n");
                        }
                    });

                }
                if (intent.getStringExtra(getString(R.string.EXTENDED_DATA_STATUS)).contentEquals("ASK"))
                    askToEnableBluetooth();
                if (intent.getStringExtra(getString(R.string.EXTENDED_DATA_STATUS)).contentEquals("CONNECTED")) {
                    mIsConnected = true;
                    updateToolbar();
                }
                if (intent.getStringExtra(getString(R.string.EXTENDED_DATA_STATUS)).contentEquals("DISCONNECTED")) {
                    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
                    toolbar.setTitle("Not connected");
                    mIsConnected = false;
                }

            }
        }
    }
}
