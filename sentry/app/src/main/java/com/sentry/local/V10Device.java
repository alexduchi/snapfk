package com.sentry.local;

import java.util.ArrayList;
import java.util.List;

final class V10Device {
    final String id;
    String name = "Appareil inconnu";
    String type = "unknown";
    String vendor = "Non déterminé";
    String address = "—";
    String source = "Bluetooth";
    String room = "Auto";
    String hostname = "";
    String mac = "";
    String ports = "";
    String serviceUuids = "";
    String manufacturerData = "";
    String gattSummary = "";
    int rssi = -100;
    int txPower = Integer.MIN_VALUE;
    int confidence = 30;
    int battery = -1;
    int bondState = 0;
    int seenCount = 0;
    long firstSeen = 0L;
    long lastSeen = 0L;
    boolean online = false;
    float screenX;
    float screenY;
    float screenRadius;
    final List<V10TrailPoint> trail = new ArrayList<>();

    V10Device(String id) {
        this.id = id;
    }

    void addTrail(int value, long timestamp) {
        trail.add(new V10TrailPoint(timestamp, value));
        while (trail.size() > 180) trail.remove(0);
    }
}

final class V10TrailPoint {
    final long time;
    final int value;

    V10TrailPoint(long time, int value) {
        this.time = time;
        this.value = value;
    }
}

final class V10WifiNetwork {
    String ssid;
    String bssid;
    String capabilities;
    int rssi;
    int frequency;
    int channel;
}

final class V10Capabilities {
    boolean bluetoothLe;
    boolean wifiRtt;
    boolean uwb;
    boolean arCore;
    boolean gyroscope;
    boolean accelerometer;
    boolean magnetometer;
    boolean barometer;
    boolean stepCounter;
    boolean camera;
    int glEsVersion;
}
