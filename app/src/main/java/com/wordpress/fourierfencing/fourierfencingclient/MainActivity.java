package com.wordpress.fourierfencing.fourierfencingclient;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static com.wordpress.fourierfencing.fourierfencingclient.R.id.timer;


public class MainActivity extends AppCompatActivity {

    int scoreTeamA = 0;
    int scoreTeamB = 0;


    public static final int STOPPED = 1;
    public static final int PAUSED = 2;
    public static int mCurrentState = 1;
    private Handler customHandler = new Handler();
    private ToggleButton buttonStartTime;
    private TextView textViewShowTime, textString, textStringLength;
    private CountDownTimer countDownTimer; // built in android class
    // CountDownTimer
    private long totalTimeCountInMilliseconds; // total count down time in
    // milliseconds
    private long timeSwapBuff;
    private long updatedTime;
    private long startTime;
    private long pausedTimeCountInMilliseconds;
    private long timeBlinkInMilliseconds; // start time of start blinking
    private boolean blink; // controls the blinking .. on and off


    private static final String TAG = "BlueTest5-MainActivity";
    private int mMaxChars = 50000;//Default
    private UUID mDeviceUUID;
    private BluetoothSocket mBTSocket;
    private ReadInput mReadThread = null;

    private boolean mIsUserInitiatedDisconnect = false;

    private TextView mTxtReceive;
    private EditText mEditSend;
    private Button mBtnDisconnect;
    private Button mBtnSend;
    private Button mBtnClear;
    private Button mBtnClearInput;
    private ScrollView scrollView;
    private CheckBox chkScroll;
    private CheckBox chkReceiveText;

    private boolean mIsBluetoothConnected = false;

    private BluetoothDevice mDevice;

    private ProgressDialog progressDialog;

    Handler bluetoothIn;
    final int handlerState = 0;                         //used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // String for MAC address
    private static String address;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityHelper.initialize(this);

        Intent intent = getIntent();
        Bundle b = intent.getExtras();
        mDevice = b.getParcelable(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        mDeviceUUID = UUID.fromString(b.getString(DeviceListActivity.DEVICE_UUID));
        mMaxChars = b.getInt(DeviceListActivity.BUFFER_SIZE);

        buttonStartTime = (ToggleButton) findViewById(R.id.reset);
        textViewShowTime = (TextView) findViewById(timer);
        mTxtReceive = (TextView) findViewById(R.id.textString);
        textStringLength = (TextView) findViewById(R.id.testView1);


        mTxtReceive.setMovementMethod(new ScrollingMovementMethod());
    }

    private class ReadInput implements Runnable {

        private boolean bStop = false;
        private Thread t;

        public ReadInput() {
            t = new Thread(this, "Input Thread");
            t.start();
        }

        public boolean isRunning() {
            return t.isAlive();
        }

        @Override
        public void run() {
            InputStream inputStream;

            try {
                inputStream = mBTSocket.getInputStream();
                while (!bStop) {
                    byte[] buffer = new byte[256];
                    if (inputStream.available() > 0) {
                        inputStream.read(buffer);
                        int i = 0;
						/*
						 * This is needed because new String(buffer) is taking the entire buffer i.e. 256 chars on Android 2.3.4 http://stackoverflow.com/a/8843462/1287554
						 */
                        for (i = 0; i < buffer.length && buffer[i] != 0; i++) {
                        }
                        final String strInput = new String(buffer, 0, i);

						/*
						 * If checked then receive text, better design would probably be to stop thread if unchecked and free resources, but this is a quick fix
						 */


                            mTxtReceive.post(new Runnable() {
                                @Override
                                public void run() {
                                    mTxtReceive.append(strInput);
                                    //Uncomment below for testing
                                    mTxtReceive.append("\n");
                                    mTxtReceive.append("Chars: " + strInput.length() + " Lines: " + mTxtReceive.getLineCount() + "\n");

                                    int txtLength = mTxtReceive.getEditableText().length();
                                    if(txtLength > mMaxChars){
                                        mTxtReceive.getEditableText().delete(0, txtLength - mMaxChars);
                                    }

                                    /*if (chkScroll.isChecked()) { // Scroll only if this is checked
                                        scrollView.post(new Runnable() { // Snippet from http://stackoverflow.com/a/4612082/1287554
                                            @Override
                                            public void run() {
                                                scrollView.fullScroll(View.FOCUS_DOWN);
                                            }
                                        });
                                    }*/
                                }
                            });


                    }
                    Thread.sleep(500);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        public void stop() {
            bStop = true;
        }

    }

    private class DisConnectBT extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(Void... params) {

            if (mReadThread != null) {
                mReadThread.stop();
                while (mReadThread.isRunning())
                    ; // Wait until it stops
                mReadThread = null;

            }

            try {
                mBTSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mIsBluetoothConnected = false;
            if (mIsUserInitiatedDisconnect) {
                finish();
            }
        }

    }

    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        if (mBTSocket != null && mIsBluetoothConnected) {
            new DisConnectBT().execute();
        }
        Log.d(TAG, "Paused");
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mBTSocket == null || !mIsBluetoothConnected) {
            new ConnectBT().execute();
        }
        Log.d(TAG, "Resumed");
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "Stopped");
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean mConnectSuccessful = true;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(MainActivity.this, "Hold on", "Connecting");// http://stackoverflow.com/a/11130220/1287554
        }

        @Override
        protected Void doInBackground(Void... devices) {

            try {
                if (mBTSocket == null || !mIsBluetoothConnected) {
                    mBTSocket = mDevice.createInsecureRfcommSocketToServiceRecord(mDeviceUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    mBTSocket.connect();
                }
            } catch (IOException e) {
                // Unable to connect to device
                e.printStackTrace();
                mConnectSuccessful = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (!mConnectSuccessful) {
                Toast.makeText(getApplicationContext(), "Could not connect to device. Is it a Serial device? Also check if the UUID is correct in the settings", Toast.LENGTH_LONG).show();
                finish();
            } else {
                msg("Connected to device");
                mIsBluetoothConnected = true;
                mReadThread = new ReadInput(); // Kick off input reader
            }

            progressDialog.dismiss();
        }

    }




    public void displayForTeamA(int score) {
        TextView scoreView = (TextView) findViewById(R.id.team_a_score);
        scoreView.setText(String.valueOf(score));
    }

    public void displayForTeamB(int score) {
        TextView scoreView = (TextView) findViewById(R.id.team_b_score);
        scoreView.setText(String.valueOf(score));
    }

    public void addOneForFencerA(View v) {
        scoreTeamA = scoreTeamA + 1;
        displayForTeamA(scoreTeamA);
    }

    public void resetScore(View v) {
        boolean on = ((ToggleButton) v).isChecked();
        if (on) {
            switch (mCurrentState) {
                case PAUSED:
                    countDownTimer.resume();
                    //resumeTimer();
                    break;
                case STOPPED:
                    setTimer();
                    startTimer();
                    break;
            }
        } else {
            enterState(PAUSED);
            countDownTimer.pause();
            //pauseTimer();
        }
    }

    private void enterState(int state) {
        if (mCurrentState != state) {

            // update preference for widget, notification


            mCurrentState = state;

        }
    }

    private void pauseTimer() {
        countDownTimer.pause();
        //countDownTimer.cancel();
    }

    private void resumeTimer() {
        countDownTimer = new CountDownTimer(countDownTimer.resume(), 500) {
            @Override
            public void onTick(long leftTimeInMilliseconds) {
                long seconds = leftTimeInMilliseconds / 1000;

                textViewShowTime.setText(String.format("%02d", seconds / 60)
                        + ":" + String.format("%02d", seconds % 60));
            }

            @Override
            public void onFinish() {
                textViewShowTime.setText("Time up!");

            }
        };
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(totalTimeCountInMilliseconds, 500) {
            // 500 means, onTick function will be called at every 500
            // milliseconds

            @Override
            public void onTick(long leftTimeInMilliseconds) {
                long seconds = leftTimeInMilliseconds / 1000;

                if (leftTimeInMilliseconds < timeBlinkInMilliseconds) {
                    textViewShowTime.setTextAppearance(getApplicationContext(),
                            R.style.blinkText);
                    // change the style of the textview .. giving a red
                    // alert style

                    if (blink) {
                        textViewShowTime.setVisibility(View.VISIBLE);
                        // if blink is true, textview will be visible
                    } else {
                        textViewShowTime.setVisibility(View.INVISIBLE);
                    }

                    blink = !blink; // toggle the value of blink
                }

                textViewShowTime.setText(String.format("%02d", seconds / 60)
                        + ":" + String.format("%02d", seconds % 60));
                // format the textview to show the easily readable format
            }

            @Override
            public void onFinish() {
                // this function will be called when the timecount is finished
                textViewShowTime.setText("Time up!");
                /*textViewShowTime.setVisibility(View.VISIBLE);
                buttonStartTime.setVisibility(View.VISIBLE);
                buttonStopTime.setVisibility(View.GONE);
                edtTimerValue.setVisibility(View.VISIBLE);*/
            }

        }.start();

    }

    private void setTimer() {
//        int time = 3;
        /*if (!edtTimerValue.getText().toString().equals("")) {
            time = Integer.parseInt(edtTimerValue.getText().toString());
        } else
            Toast.makeText(MainActivity.this, "Please Enter Minutes...",
                    Toast.LENGTH_LONG).show();*/

        totalTimeCountInMilliseconds = 60 * 3 * 1000;

        timeBlinkInMilliseconds = 30 * 1000;
    }

    public void addOneForFencerB(View v) {
        scoreTeamB = scoreTeamB + 1;
        displayForTeamB(scoreTeamB);
    }

    public void increaseFencerA(View v) {
        scoreTeamA = scoreTeamA + 1;
        displayForTeamA(scoreTeamA);
    }

    public void decreaseFencerA(View v) {
        scoreTeamA = scoreTeamA - 1;
        displayForTeamA(scoreTeamA);
    }

    public void increaseFencerB(View v) {
        scoreTeamB = scoreTeamB + 1;
        displayForTeamB(scoreTeamB);
    }

    public void decreaseFencerB(View v) {
        scoreTeamB = scoreTeamB - 1;
        displayForTeamB(scoreTeamB);
    }

    public void addBothFencers(View v) {
        scoreTeamA = scoreTeamA + 1;
        scoreTeamB = scoreTeamB + 1;
        displayForTeamA(scoreTeamA);
        displayForTeamB(scoreTeamB);
    }
}
