package it.scognito.abraboxabra;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private final String TAG = "ABRABOXABRA";

    ImageView ivPushButton, ivSettings, ivStatus, ivInfo, ivShutdown;
    TextView tvLog;
    Switch autoPushOnConnect;

    final int REQUEST_ENABLE_BT = 0;
    final int STATUS_DISCONNECTED = 0;
    final int STATUS_CONNTECTED = 1;
    final int STATUS_BUSY = 2;

    final String myUuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee";
    String btServerAddr = "00:11:22:98:76:54";

    final int DEBUG_MSG_ERROR = 0, DEBUG_MSG_INFO = 1;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice btDevice = null;
    boolean autoPush = false;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private Handler messageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            setStatus(msg.what);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        getAbraboxabraAddress();
        setupViews();
        getAutoPushOnConnect();
        puppaLog(DEBUG_MSG_INFO, "App start!");

        if (!btInit())
            return;

        startBT();
    }

    private void startBT() {
        if (btServerAddr == null)
            selectBtDevice();
        else {
            if (btQueryPairedDevices())
                btStartService();
        }
    }

    private boolean btInit() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            puppaLog(DEBUG_MSG_INFO, "Device does not support Bluetooth!");
            return false;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        return true;
    }

    private boolean btQueryPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            puppaLog(DEBUG_MSG_INFO, "Found " + pairedDevices.size() + " previously paired devices");
            for (BluetoothDevice device : pairedDevices) {
                // if (device.getName().equalsIgnoreCase(btServerName)) {
                if (device.getAddress().equalsIgnoreCase(btServerAddr)) {
                    puppaLog(DEBUG_MSG_INFO, "Found device: " + device.getName() + " -- " + device.getAddress());
                    btDevice = device;
                    return true;
                } else
                    puppaLog(DEBUG_MSG_INFO, "Device " + device.getName() + " is not an abraboxabra server");
            }
        } else
            puppaLog(DEBUG_MSG_INFO, "No previously paired device found");

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                puppaLog(DEBUG_MSG_INFO, "Enabled OK!");
                startBT();
            } else {
                puppaLog(DEBUG_MSG_INFO, "ERROR: " + resultCode);
            }
        }
    }

    public void setupViews() {

        ivPushButton = (ImageView) findViewById(R.id.ivPushButton);
        ivStatus = (ImageView) findViewById(R.id.ivStatus);
        ivSettings = (ImageView) findViewById(R.id.ivSettings);
        ivInfo = (ImageView) findViewById(R.id.ivInfo);
        ivShutdown = (ImageView) findViewById(R.id.ivShutdown);

        tvLog = (TextView) findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setVisibility(View.INVISIBLE);

        autoPushOnConnect = (Switch) findViewById(R.id.switch1);
        autoPushOnConnect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    setAutoPushOnConnect(true);
                } else {
                    setAutoPushOnConnect(false);
                }
            }
        });

        ivPushButton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                ivPushButton.setSelected(arg1.getAction() == MotionEvent.ACTION_DOWN);
                if (arg1.getAction() == MotionEvent.ACTION_DOWN) {
                    if (mConnectedThread != null) {
                        mConnectedThread.write("openclose".getBytes());
                        puppaLog(DEBUG_MSG_INFO, "Sending command");
                    }
                }
                return true;
            }
        });

        ivSettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectBtDevice();
            }
        });

        ivInfo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (tvLog.getVisibility() == View.VISIBLE)
                    tvLog.setVisibility(View.INVISIBLE);
                else
                    tvLog.setVisibility(View.VISIBLE);
            }
        });

        ivShutdown.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askShutdown();
            }
        });


        ivStatus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // (new Thread(new workerThread("lightOff"))).start();
                puppaLog(DEBUG_MSG_INFO, "RESTARTING server");
                if (btQueryPairedDevices()) {
                    btStartService();
                }
            }
        });
    }

    private void setBtServer(String addr) {
        btServerAddr = addr;
        if (btQueryPairedDevices()) {
            btStartService();
        }
    }

    public void getAbraboxabraAddress() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        btServerAddr = prefs.getString("btaddress", null);
    }

    public void setAbraboxabraAddress(String addr) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString("btaddress", addr);
        edit.apply();
    }

    public void getAutoPushOnConnect() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        autoPush = prefs.getBoolean("autopush", false);
        autoPushOnConnect.setChecked(autoPush);
    }

    public void setAutoPushOnConnect(boolean autostart) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putBoolean("autopush", autostart);
        edit.apply();

        autoPush = autostart;
    }

    private void setStatus(int status) {

        switch (status) {
            case STATUS_CONNTECTED:
                ivStatus.setImageResource(android.R.drawable.presence_online);
                break;
            case STATUS_DISCONNECTED:
                ivStatus.setImageResource(android.R.drawable.presence_busy);
                break;
            case STATUS_BUSY:
                ivStatus.setImageResource(android.R.drawable.presence_away);
                break;
            default:
                break;
        }
    }

    private void askShutdown() {
        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if(mBluetoothAdapter == null){
            Toast.makeText(this, R.string.device_no_bt, Toast.LENGTH_SHORT).show();
            return;
        }

        builder.setTitle(getResources().getString(R.string.ask_shutdown));

        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (mConnectedThread != null) {
                    mConnectedThread.write("shutdown".getBytes());
                    puppaLog(DEBUG_MSG_INFO, "Sending command");
                }
            }
        });


        dialog = builder.create();
        dialog.show();
    }

    private void selectBtDevice() {

        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if(mBluetoothAdapter == null){
            Toast.makeText(this, R.string.device_no_bt, Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() == 0) {
            builder.setTitle(getResources().getString(R.string.warning));
            builder.setMessage(getResources().getString(R.string.no_paired_devices_found));
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            dialog = builder.create();
            dialog.show();
            return;
        } else
            puppaLog(DEBUG_MSG_INFO, "Found " + pairedDevices.size() + " previously paired devices");

        final String[] btDevicesNameArray = new String[pairedDevices.size()];
        final String[] btDevicesAddrArray = new String[pairedDevices.size()];

        int i = 0;
        for (BluetoothDevice device : pairedDevices) {
            btDevicesNameArray[i] = device.getName();
            btDevicesAddrArray[i] = device.getAddress();
            i++;
        }

        builder.setTitle(getResources().getString(R.string.select_device));
        builder.setSingleChoiceItems(btDevicesNameArray, 0, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                puppaLog(DEBUG_MSG_INFO, "Selected:" + btDevicesNameArray[item] + "(" + btDevicesAddrArray[item] + ")");
                setBtServer(btDevicesAddrArray[item]);
                setAbraboxabraAddress(btDevicesAddrArray[item]);
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        puppaLog(DEBUG_MSG_INFO, "Stop bt service!");
        btStopService();
    }

    public synchronized void connected(BluetoothSocket socket) {

        Log.d(TAG, "Starting mConnectedThread");
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, messageHandler);
        mConnectedThread.start();
    }

    public synchronized void btStartService() {
        Log.d(TAG, "Start bluetooth service");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {

            Log.d(TAG, "Thread mConnectThread exists, canceling it");
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            Log.d(TAG, "Thread mConnectEDThread exists, canceling it");
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        Log.d(TAG, "Creating ConnectThread...");
        setStatus(STATUS_BUSY);
        mConnectThread = new ConnectThread(btDevice, messageHandler);
        mConnectThread.start();
    }

    public synchronized void btStopService() {
        puppaLog(DEBUG_MSG_INFO, "Stop bluetooth service");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setStatus(STATUS_DISCONNECTED);
    }

    public void puppaLog(int type, String puppa) {

        if (type == DEBUG_MSG_ERROR)
            Log.e(TAG, puppa);
        else
            Log.i(TAG, puppa);

        if (tvLog != null)
            tvLog.append("\n" + puppa);
    }

    /* ALTRE CLASSI */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        //private final BluetoothDevice mmDevice;
        private Handler parentHandler;

        public ConnectThread(BluetoothDevice device, Handler parentHandler) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
         //   mmDevice = device;
            this.parentHandler = parentHandler;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(myUuid));
                Log.d(TAG, "ConnectThread Constructor created :)");
            } catch (IOException e) {
                Log.e(TAG, "Error creating socket! " + e.toString());
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                parentHandler.sendEmptyMessage(STATUS_BUSY);
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
                Log.d(TAG, "RFCOMM channel created :)");
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                // setStatus(STATUS_DISCONNECTED);
                parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
                try {
                    mmSocket.close();
                    Log.e(TAG, "IOError opening socket!");
                } catch (IOException closeException) {
                    Log.e(TAG, "IOError closing socket!");
                }

                Log.e(TAG, "IOError connecting to socket!");
                return;
            }

            // Do work to manage the connection (in a separate thread)
            connected(mmSocket); //, mmDevice);
        }

        public void cancel() {
            parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
            try {
                Log.d(TAG, "ConnectThread.cancel...");
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "IOError on ConnectThread.cancel! " + e.toString());
            }
        }
    }

    /*
     *
     * THREAD CONNESSIONE
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private Handler parentHandler;

        public ConnectedThread(BluetoothSocket socket, Handler parentHandler) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            this.parentHandler = parentHandler;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                parentHandler.sendEmptyMessage(STATUS_CONNTECTED);
            } catch (IOException e) {
                parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024]; // buffer store for the stream
            int bytes; // bytes returned from read()

            if(autoPush)
                write("openclose".getBytes());

            // Keep listening to the InputStream until an exception occurs
            Log.d(TAG, "THE FUN BEGINS!");
            while (true) {

                try { // Read from the InputStream bytes =
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI activity //
                    String readMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "Received message: " + readMessage);

                } catch (IOException e) {
                    parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
                    Log.e(TAG, "IOException: " + e.toString());
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
                Log.e(TAG, "Write error: " + e.toString());
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
            try {
                Log.d(TAG, "ConnectedThread.cancel...");
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread.cancel exception " + e.toString());
            }
        }
    }
}