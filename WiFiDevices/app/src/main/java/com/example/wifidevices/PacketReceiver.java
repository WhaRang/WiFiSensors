package com.example.wifidevices;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

public class PacketReceiver implements Runnable{

    Toast toast;
    String serverIP;

    public PacketReceiver(Toast toast) {
        this.toast = toast;
    }

    @Override
    public void run() {
        String hostIp = "";
        String inetAddrStr = "0.0.0.0";
        int port = 54000;

        try {
            InetAddress myHostAddr = InetAddress.getByName(inetAddrStr);
            DatagramSocket rcvSocket = new DatagramSocket(null);
            rcvSocket.setReuseAddress(true);
            rcvSocket.bind(new InetSocketAddress(inetAddrStr, port));
            rcvSocket.setBroadcast(true);

            while (true) {
                if (MainActivity.receiverMode) {

                    byte[] recvBuf = new byte[15000];
                    DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                    rcvSocket.receive(packet);

                    hostIp = packet.getAddress().getHostAddress();

                    String data = new String(packet.getData()).trim();

                    String[] tabOfFloatString = data.split(" ");

                    ArrayList<Float> valuesList = new ArrayList<>();
                    for(String s : tabOfFloatString){
                        float res = Float.parseFloat(s);
                        valuesList.add(res);
                    }
                    valuesList.remove(0);

                    float[] values = new float[valuesList.size()];
                    int i = 0;

                    for (Float f : valuesList) {
                        values[i++] = (f != null ? f : Float.NaN);
                    }

                    MainActivity.passToUi(new Runnable() {
                        @Override
                        public void run() {
                            Sensor sensor = null;
                            if (data.charAt(0) == '1') {
                                sensor = MainActivity.accelerometerSensor;
                                MainActivity.processSensorEvent(sensor, values);
                            }
                            else if (data.charAt(0) == 2) {
                                sensor = MainActivity.magnetometerSensor;
                                MainActivity.processSensorEvent(sensor, values);
                            }
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        }
        serverIP = hostIp;
    }
}