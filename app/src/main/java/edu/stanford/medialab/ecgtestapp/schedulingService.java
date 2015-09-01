package edu.stanford.medialab.ecgtestapp;

/**
 * Created by prpl on 9/1/15.
 */

import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class schedulingService extends Service{
    public final static UUID TETHYS_SERVICE_UUID = UUID.fromString(ECGAttributes.SERVICE_UUID);
    public final static UUID UUID_LISTED_DEVICE_INDICATE = UUID.fromString(ECGAttributes.CHARACTERISTIC_UUID);
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private boolean mScanning;
    private ArrayList<BluetoothDevice> mLeDevices;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 15000;
    private static String TAG = "Serverservice";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private Handler mHandler2;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattService mHelenaService;
    private boolean flag;
    private static final long CONNECTION_PERIOD = 30000;
    JSONObject jsonObj;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
        mLeDevices = new ArrayList<BluetoothDevice>();
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stuff
        clear();
        scanLeDevice(true);
      //  stopSelf();
        return START_STICKY;
    }


    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            Log.e("Scheduling","scanLeDevice start scan");
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    processServers();
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothAdapter.startLeScan( mLeScanCallback);

        } else {
            Log.e("Scheduling","scanLeDevice stop scan");
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    public void addDevice(BluetoothDevice device) {
        if(!mLeDevices.contains(device)) {
            Log.e("Scheduling","addDevice. new device add to list with device name "+ device.getName()+ " and address "+device.getAddress());
            mLeDevices.add(device);
        }
    }

    public BluetoothDevice getDevice(int position) {
        return mLeDevices.get(position);
    }

    public void clear() {
        mLeDevices.clear();
    }

    public int getCount() {
        return mLeDevices.size();
    }

    public Object getItem(int i) {
        return mLeDevices.get(i);
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    /*
                    if (parseUUIDs(scanRecord).contains(TETHYS_SERVICE_UUID))

                        */
                    addDevice(device);
                }
            };

    public void processServers() {
        int listSize = getCount();
        Log.e("Scheduling", "listSize: "+ listSize);
        if (listSize == 0) return;
        final BluetoothDevice device = (BluetoothDevice) getItem(0);
        if (device.equals(null)) return;
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        mHandler2 = new Handler();
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        mDeviceName = device.getName();
        mDeviceAddress = device.getAddress();
        Log.e(TAG, "onHandleIntent called mDeviceName: "+ mDeviceName + " mDeviceAddress: "+mDeviceAddress);
        if(mDeviceAddress != null) {
            Log.e(TAG, "about to call connectLeDevice");

            mHandler2.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mBluetoothLeService != null)
                        mBluetoothLeService.disconnect();
                    unregisterReceiver(mGattUpdateReceiver);
                    unbindService(mServiceConnection);
                    mBluetoothLeService = null;
                }
            }, CONNECTION_PERIOD);

            if (mBluetoothLeService != null) {
                try {
                    final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                    Log.e(TAG, "Connect request result= " + result);
                } catch (Exception e) {
                    Log.e(TAG, "Connect Exception encountered");
                }
            } else {
                Log.e(TAG,"mBluetoothLeService not intialized yet.");
            }
        }

    }

    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit,
                                    mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                            continue;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }
        return uuids;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.e(TAG, "mBluetoothLeService intialized");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                stopSelf();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "onServiceDisconnected called");
            unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
            Log.e(TAG, "onServiceDisconnected returned. receivers unregistered and unbinded");
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.e("Broadcase Receiver", "action ACTION_GATT_CONNECTED received");
                mConnected = true;


            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.e("Broadcase Receiver", "action ACTION_GATT_DISCONNECTED received");
                mConnected = false;
                //pushToServer();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                Log.e("Broadcase Receiver", "action ACTION_GATT_SERVICES_DISCOVERED received");
                checkServices(mBluetoothLeService.getSupportedGattServices());


            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.e("Broadcase Receiver", "action ACTION_DATA_AVAILABLE received");
                try {
                    dealWithData(intent.getByteArrayExtra(BluetoothLeService.BYTE_DATA));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    private void dealWithData(byte[] data) throws UnsupportedEncodingException{

        final StringBuilder stringBuilder = new StringBuilder(data.length);
        for(byte byteChar : data)
            stringBuilder.append(String.format("%02X ", byteChar));

        String str =  stringBuilder.toString();
        Log.e("Debug", "Data saved: " + str);



    }

    private void checkServices(List<BluetoothGattService> gattServices) {

        if (gattServices == null) return;
        String uuid;
        // Loops through available GATT Services.

        for (BluetoothGattService gattService : gattServices) {

            uuid = gattService.getUuid().toString();

            if(uuid.equals(TETHYS_SERVICE_UUID.toString())){
                mHelenaService = gattService;
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    //only interested in this characteristic
                    if(gattCharacteristic.getUuid().toString().equals(UUID_LISTED_DEVICE_INDICATE.toString())){
                        final int charaProp = gattCharacteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                            //Initiate notifications
                            mNotifyCharacteristic = gattCharacteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    gattCharacteristic, true);
                            Log.e(TAG, "mNotifyCharacteristic assigned.");


                        }
                    }

                }

            } else {
                Log.d(TAG, "Skipping other services");
            }
        }
    }

    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }



}