# WiFiSensors
Android sensors and WiFi UDP data transmission.
An Android application that reads data from the accelerometer and magnetometer.
Based on this, it makes a conclusion about the shaking of the phone and causes vibration, in case of shaking.
There are two modes of work: receiver and sender. 
In receive mode, the device will intercept and analyze UDP packets,
  that contain information from sensors from other devices
  and will show the result of processing this information to its screen.
In the sender's work-mode, the program will broadcast the information read from the sensors vie WiFi.
