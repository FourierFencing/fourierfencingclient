package com.wordpress.fourierfencing.fourierfencingclient;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class DeviceListActivity extends Activity {

    private Button mBtnSearch;
    private Button mBtnConnect;
    private ListView mLstDevices;

    private BluetoothAdapter mBTAdapter;

    // Debugging for LOGCAT
    private static final String TAG = "DeviceListActivity";
    private static final boolean D = true;
    private static final int BT_ENABLE_REQUEST = 10; // This is the code we use for BT Enable
    private static final int SETTINGS = 20;

    private UUID mDeviceUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // declare button for launching website and textview for connection status

    // EXTRA string to send on to mainactivity
    private int mBufferSize = 50000;
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String DEVICE_UUID = "com.blueserial.uuid";
    public static final String BUFFER_SIZE = "com.blueserial.buffersize";
    private static final String DEVICE_LIST = "com.blueserial.devicelist";
    private static final String DEVICE_LIST_SELECTED = "com.blueserial.devicelistselected";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_device_list);
        ActivityHelper.initialize(this);

        mBtnSearch = (Button) findViewById(R.id.btnSearch);
        mBtnConnect = (Button) findViewById(R.id.btnConnect);

        mLstDevices = (ListView) findViewById(R.id.listView);

        if (savedInstanceState != null) {
            ArrayList<BluetoothDevice> list = savedInstanceState.getParcelableArrayList(DEVICE_LIST);
            if (list != null) {
                initList(list);
                MyAdapter adapter = (MyAdapter) mLstDevices.getAdapter();
                int selectedIndex = savedInstanceState.getInt(DEVICE_LIST_SELECTED);
                if (selectedIndex != -1) {
                    adapter.setSelectedIndex(selectedIndex);
                    mBtnConnect.setEnabled(true);
                }
            } else {
                initList(new ArrayList<BluetoothDevice>());
            }

        } else {
            initList(new ArrayList<BluetoothDevice>());
        }

        mBtnSearch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                mBTAdapter = BluetoothAdapter.getDefaultAdapter();

                if (mBTAdapter == null) {
                    Toast.makeText(getApplicationContext(), "Bluetooth not found", Toast.LENGTH_SHORT).show();
                } else if (!mBTAdapter.isEnabled()) {
                    Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBT, BT_ENABLE_REQUEST);
                } else {
                    new SearchDevices().execute();
                }
            }
        });

        mBtnConnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                BluetoothDevice device = ((MyAdapter) (mLstDevices.getAdapter())).getSelectedItem();
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.putExtra(EXTRA_DEVICE_ADDRESS, device);
                intent.putExtra(DEVICE_UUID, mDeviceUUID.toString());
                intent.putExtra(BUFFER_SIZE, mBufferSize);
                startActivity(intent);
            }
        });
    }


    /**
     * Called when the screen rotates. If this isn't handled, data already generated is no longer available
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        MyAdapter adapter = (MyAdapter) (mLstDevices.getAdapter());
        ArrayList<BluetoothDevice> list = (ArrayList<BluetoothDevice>) adapter.getEntireList();

        if (list != null) {
            outState.putParcelableArrayList(DEVICE_LIST, list);
            int selectedIndex = adapter.selectedIndex;
            outState.putInt(DEVICE_LIST_SELECTED, selectedIndex);
        }
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BT_ENABLE_REQUEST:
                if (resultCode == RESULT_OK) {
                    msg("Bluetooth Enabled successfully");
                    new SearchDevices().execute();
                } else {
                    msg("Bluetooth couldn't be enabled");
                }

                break;
            case SETTINGS: //If the settings have been updated
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                String uuid = prefs.getString("prefUuid", "Null");
                mDeviceUUID = UUID.fromString(uuid);
                Log.d(TAG, "UUID: " + uuid);
                String bufSize = prefs.getString("prefTextBuffer", "Null");
                mBufferSize = Integer.parseInt(bufSize);

                String orientation = prefs.getString("prefOrientation", "Null");
                Log.d(TAG, "Orientation: " + orientation);
                if (orientation.equals("Landscape")) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else if (orientation.equals("Portrait")) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else if (orientation.equals("Auto")) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                }
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Quick way to call the Toast
     *
     * @param str
     */
    private void msg(String str) {
        Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
    }

    /**
     * Initialize the List adapter
     *
     * @param objects
     */
    private void initList(List<BluetoothDevice> objects) {
        final MyAdapter adapter = new MyAdapter(getApplicationContext(), R.layout.device_name, R.id.listContent, objects);
        mLstDevices.setAdapter(adapter);
        mLstDevices.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                adapter.setSelectedIndex(position);
                mBtnConnect.setEnabled(true);
            }
        });
    }

    private class SearchDevices extends AsyncTask<Void, Void, List<BluetoothDevice>> {

        @Override
        protected List<BluetoothDevice> doInBackground(Void... params) {
            Set<BluetoothDevice> pairedDevices = mBTAdapter.getBondedDevices();
            List<BluetoothDevice> listDevices = new ArrayList<BluetoothDevice>();
            for (BluetoothDevice device : pairedDevices) {
                listDevices.add(device);
            }
            return listDevices;

        }

        @Override
        protected void onPostExecute(List<BluetoothDevice> listDevices) {
            super.onPostExecute(listDevices);
            if (listDevices.size() > 0) {
                MyAdapter adapter = (MyAdapter) mLstDevices.getAdapter();
                adapter.replaceItems(listDevices);
            } else {
                msg("No paired devices found, please pair your serial BT device and try again");
            }
        }

    }

  /*  @Override
    public void onResume()
    {
        super.onResume();
        /*/

    /***************
     checkBTState();

     textView1 = (TextView) findViewById(R.id.connecting);
     textView1.setTextSize(40);
     textView1.setText(" ");

     // Initialize array adapter for paired devices
     mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

     // Find and set up the ListView for paired devices
     ListView pairedListView = (ListView) findViewById(R.id.listView);
     pairedListView.setAdapter(mPairedDevicesArrayAdapter);
     pairedListView.setOnItemClickListener(mDeviceClickListener);

     // Get the local Bluetooth adapter
     mBTAdapter = BluetoothAdapter.getDefaultAdapter();

     // Get a set of currently paired devices and append to 'pairedDevices'
     Set<BluetoothDevice> pairedDevices = mBTAdapter.getBondedDevices();

     // Add previosuly paired devices to the array
     if (pairedDevices.size() > 0) {
     findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);//make title viewable
     for (BluetoothDevice device : pairedDevices) {
     mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
     }
     } else {
     String noDevices = getResources().getText(R.string.none_paired).toString();
     mPairedDevicesArrayAdapter.add(noDevices);
     }
     }

     // Set up on-click listener for the list (nicked this - unsure)
     private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
     public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

     textView1.setText("Connecting...");
     // Get the device MAC address, which is the last 17 chars in the View
     //            String info = ((TextView) v).getText().toString();
     //            String address = info.substring(info.length() - 17);
     //
     //            // Make an intent to start next activity while taking an extra which is the MAC address.
     //            Intent i = new Intent(DeviceListActivity.this, MainActivity.class);
     //            i.putExtra(EXTRA_DEVICE_ADDRESS, address);
     ////            startActivity(new Intent(DeviceListActivity.this, DeviceListActivity2.class));
     //            startActivity(i);
     BluetoothDevice device = ((MyAdapter) (mLstDevices.getAdapter())).getSelectedItem();
     Intent intent = new Intent(getApplicationContext(), MainActivity.class);
     intent.putExtra(EXTRA_DEVICE_ADDRESS, device);
     intent.putExtra(DEVICE_UUID, mDeviceUUID.toString());
     intent.putExtra(BUFFER_SIZE, mBufferSize);
     startActivity(intent);
     }
     };
     */

    private class MyAdapter extends ArrayAdapter<BluetoothDevice> {
        private int selectedIndex;
        private Context context;
        private int selectedColor = Color.parseColor("#abcdef");
        private List<BluetoothDevice> myList;

        public MyAdapter(Context ctx, int resource, int textViewResourceId, List<BluetoothDevice> objects) {
            super(ctx, resource, textViewResourceId, objects);
            context = ctx;
            myList = objects;
            selectedIndex = -1;
        }

        public void setSelectedIndex(int position) {
            selectedIndex = position;
            notifyDataSetChanged();
        }

        public BluetoothDevice getSelectedItem() {
            return myList.get(selectedIndex);
        }

        @Override
        public int getCount() {
            return myList.size();
        }

        @Override
        public BluetoothDevice getItem(int position) {
            return myList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private class ViewHolder {
            TextView tv;
        }

        public void replaceItems(List<BluetoothDevice> list) {
            myList = list;
            notifyDataSetChanged();
        }

        public List<BluetoothDevice> getEntireList() {
            return myList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View vi = convertView;
            ViewHolder holder;
            if (convertView == null) {
                vi = LayoutInflater.from(context).inflate(R.layout.device_name, null);
                holder = new ViewHolder();

                holder.tv = (TextView) vi.findViewById(R.id.listContent);

                vi.setTag(holder);
            } else {
                holder = (ViewHolder) vi.getTag();
            }

            if (selectedIndex != -1 && position == selectedIndex) {
                holder.tv.setBackgroundColor(selectedColor);
            } else {
                holder.tv.setBackgroundColor(Color.BLACK);
            }
            BluetoothDevice device = myList.get(position);
            holder.tv.setText(device.getName() + "\n   " + device.getAddress());

            return vi;
        }

    }


}