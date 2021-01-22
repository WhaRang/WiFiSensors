package com.example.wifidevices;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";

    private Switch transmissionSwitch;
    private Switch receiverSwitch;

    private SensorManager sensorManager;
    public static Sensor accelerometerSensor;
    public static Sensor magnetometerSensor;
    private static Vibrator vibrator;

    private static TextView compassText;
    private static ImageView compassImage;
    private static TextView xTextView;
    private static TextView yTextView;
    private static TextView zTextView;

    private static float[] lastAccelerometer = new float[3];
    private static float[] lastMagnetometer = new float[3];
    private static float[] rotationMatrix = new float[9];
    private static float[] orientation = new float[3];

    static boolean isLastAccelerometerArrayCopied = false;
    static boolean isLastMagnetometerArrayCopied = false;

    static long lastUpdatedTime = 0;
    static float currentDegree = 0f;

    private static float currentX;
    private static float currentY;
    private static float currentZ;
    private static float lastX;
    private static float lastY;
    private static float lastZ;
    private static float xDifference;
    private static float yDifference;
    private static float zDifference;
    private static float shakeThreshold = 5f;

    private static boolean itIsNotFirstTime = false;
    public static boolean transmissionMode = false;
    public static boolean receiverMode = false;

    private WifiManager wifiManager;
    private ConnectivityManager connManager;

    private ConnectivityManager.NetworkCallback callback;
    private static DatagramSocket sendSocket;

    private PacketReceiver receiver;
    private Thread receiverThread;

    public static Handler handler;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        compassText = findViewById(R.id.compassText);
        compassImage = findViewById(R.id.compassImage);

        xTextView = findViewById(R.id.xTextView);
        yTextView = findViewById(R.id.yTextView);
        zTextView = findViewById(R.id.zTextView);

        transmissionSwitch = findViewById(R.id.transmissionSwitch);
        receiverSwitch = findViewById(R.id.receiverSwitch);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        receiverSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (receiverSwitch.isChecked()) {
                    receiverMode = true;
                    transmissionMode = false;
                    transmissionSwitch.setChecked(false);
                } else {
                    receiverMode = false;
                }
            }
        });

        transmissionSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (transmissionSwitch.isChecked()) {
                    transmissionMode = true;
                    receiverMode = false;
                    receiverSwitch.setChecked(false);
                } else {
                    transmissionMode = false;
                }
            }
        });

        checkWifiAvailability();
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onStop() {
        connManager.unregisterNetworkCallback(callback);
        super.onStop();
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("SetTextI18n")
    private void checkWifiAvailability() {
        if (!wifiManager.isWifiEnabled()) {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Wifi is turned off,\nplease turn it on", Toast.LENGTH_SHORT);
            toast.show();
            sendNetworkRequest();
        } else {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Wifi is turned on,\nplease ensure connection to internet",
                    Toast.LENGTH_SHORT);
            toast.show();
            sendNetworkRequest();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void sendNetworkRequest() {
        NetworkRequest request =
                new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();

        callback = new ConnectivityManager.NetworkCallback() {

            @SuppressLint("SetTextI18n")
            @Override
            public void onLost(Network network) {
                checkWifiAvailability();
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onAvailable(Network network) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Connected via Wi-fi", Toast.LENGTH_SHORT);
                toast.show();
                testWifi();
            }

        };

        connManager.registerNetworkCallback(request, callback);
    }


    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void testWifi() {
        WifiInfo wifiInfo;
        wifiInfo = wifiManager.getConnectionInfo();

        String gateWay = "null";

        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        if (dhcpInfo != null) {
            int tmp = dhcpInfo.gateway;
            gateWay = String.format("%d.%d.%d.%d", (tmp & 0xff), (tmp >> 8 & 0xff),
                    (tmp >> 16 & 0xff), (tmp >> 24 & 0xff));
        }

        receiver = new PacketReceiver(Toast.makeText(getApplicationContext(),
                "", Toast.LENGTH_SHORT));
        receiverThread = new Thread(receiver);
        receiverThread.start();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!receiverMode) {
            processSensorEvent(event.sensor, event.values);
        }
    }


    public static void processSensorEvent(Sensor sensor, float[] values) {
        if (sensor == accelerometerSensor) {
            System.arraycopy(values, 0, lastAccelerometer, 0, values.length);
            isLastAccelerometerArrayCopied = true;

            xTextView.setText("xValue: " + values[0] + "m/s2");
            yTextView.setText("yValue: " + values[1] + "m/s2");
            zTextView.setText("zValue: " + values[2] + "m/s2");

            currentX = values[0];
            currentY = values[1];
            currentZ = values[2];

            if (itIsNotFirstTime) {
                xDifference = Math.abs(lastX - currentX);
                yDifference = Math.abs(lastY - currentY);
                zDifference = Math.abs(lastZ - currentZ);

                if ((xDifference > shakeThreshold && yDifference > shakeThreshold) ||
                        (xDifference > shakeThreshold && zDifference > shakeThreshold) ||
                        (yDifference > shakeThreshold && zDifference > shakeThreshold)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(300);
                    }
                }
            }

            lastX = currentX;
            lastY = currentY;
            lastZ = currentZ;

            itIsNotFirstTime = true;
        } else if (sensor == magnetometerSensor) {
            System.arraycopy(values, 0, lastMagnetometer, 0, values.length);
            isLastMagnetometerArrayCopied = true;
        }

        if (transmissionMode) {
            StringBuilder packetToSend = new StringBuilder();
            if (sensor == accelerometerSensor) {
                packetToSend = new StringBuilder("1");
            } else if (sensor == magnetometerSensor) {
                packetToSend = new StringBuilder("2");
            }
            for (float f : values) {
                packetToSend.append(" ");
                packetToSend.append(f);
            }
            sendBroadcast(String.valueOf(packetToSend));
        }

        if (isLastAccelerometerArrayCopied && isLastMagnetometerArrayCopied
                && System.currentTimeMillis() - lastUpdatedTime > 250) {
            SensorManager.getRotationMatrix(rotationMatrix, null,
                    lastAccelerometer, lastMagnetometer);
            SensorManager.getOrientation(rotationMatrix, orientation);

            float azimuthInRadians = orientation[0];
            float azimuthInDegree = (float) Math.toDegrees(azimuthInRadians);

            RotateAnimation rotateAnimation =
                    new RotateAnimation(currentDegree, -azimuthInDegree,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setDuration(250);
            rotateAnimation.setFillAfter(true);
            compassImage.startAnimation(rotateAnimation);

            currentDegree = -azimuthInDegree;
            lastUpdatedTime = System.currentTimeMillis();

            int x = (int) azimuthInDegree;
            compassText.setText(x + "°");
        }
    }


    public static void sendBroadcast(String messageStr) {
        String inetAddrStr = "255.255.255.255";
        int port = 54000;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        byte[] sendData = messageStr.getBytes();
        try {
            sendSocket = new DatagramSocket(null);
            sendSocket.setReuseAddress(true);
            sendSocket.setBroadcast(true);

            try {
                Log.i("MainActivity", messageStr);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(inetAddrStr), port);
                sendSocket.send(sendPacket);
            } catch (Exception ignored) {
            }
        } catch (IOException ignored) {
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(this, accelerometerSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometerSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }


    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this, accelerometerSensor);
        sensorManager.unregisterListener(this, magnetometerSensor);
    }


    public static void passToUi(Runnable runnable){
        final Handler UIHandler = new Handler(Looper.getMainLooper());
        UIHandler.post(runnable);
    }
}