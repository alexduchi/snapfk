package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class V10GattInspector {
    interface Callback {
        void onResult(String summary, int battery, boolean completed);
    }

    private static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private V10GattInspector() {}

    static void inspect(Activity activity, String address, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT >= 31 && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            callback.onResult("Permission Bluetooth Connect absente.", -1, true);
            return;
        }
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Activity.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            callback.onResult("Bluetooth indisponible.", -1, true);
            return;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        BluetoothGatt[] holder = new BluetoothGatt[1];
        BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
            private String serviceSummary = "";

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        callback.onResult("Connexion GATT établie, découverte des services…", -1, false);
                        gatt.discoverServices();
                    } catch (SecurityException e) {
                        finish(gatt, "Permission refusée pendant la découverte GATT.", -1);
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !finished.get()) {
                    finish(gatt, status == BluetoothGatt.GATT_SUCCESS ? "Appareil déconnecté avant la fin de l’inspection." : "Connexion GATT impossible, statut " + status + ".", -1);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(gatt, "Découverte GATT échouée, statut " + status + ".", -1);
                    return;
                }
                List<String> services = new ArrayList<>();
                for (BluetoothGattService service : gatt.getServices()) {
                    services.add(shortUuid(service.getUuid()));
                }
                serviceSummary = services.isEmpty() ? "Aucun service GATT publié." : "Services GATT : " + String.join(", ", services);
                BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
                BluetoothGattCharacteristic batteryCharacteristic = batteryService == null ? null : batteryService.getCharacteristic(BATTERY_LEVEL);
                if (batteryCharacteristic == null) {
                    finish(gatt, serviceSummary + "\nService batterie standard absent.", -1);
                    return;
                }
                try {
                    if (!gatt.readCharacteristic(batteryCharacteristic)) {
                        finish(gatt, serviceSummary + "\nLecture batterie non acceptée par l’appareil.", -1);
                    }
                } catch (SecurityException e) {
                    finish(gatt, serviceSummary + "\nPermission refusée pendant la lecture batterie.", -1);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (!BATTERY_LEVEL.equals(characteristic.getUuid())) return;
                int battery = -1;
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    if (value != null) battery = Math.max(0, Math.min(100, value));
                }
                finish(gatt, serviceSummary + (battery >= 0 ? "\nBatterie publiée : " + battery + "%" : "\nBatterie présente mais illisible."), battery);
            }

            private void finish(BluetoothGatt gatt, String summary, int battery) {
                if (!finished.compareAndSet(false, true)) return;
                try { gatt.disconnect(); } catch (Exception ignored) {}
                try { gatt.close(); } catch (Exception ignored) {}
                main.post(() -> callback.onResult(summary, battery, true));
            }
        };

        try {
            holder[0] = adapter.getRemoteDevice(address).connectGatt(activity, false, gattCallback, BluetoothGatt.TRANSPORT_LE);
            callback.onResult("Tentative de connexion GATT…", -1, false);
        } catch (Exception e) {
            callback.onResult("Connexion GATT impossible : " + e.getClass().getSimpleName() + ".", -1, true);
            return;
        }

        main.postDelayed(() -> {
            if (!finished.compareAndSet(false, true)) return;
            BluetoothGatt gatt = holder[0];
            if (gatt != null) {
                try { gatt.disconnect(); } catch (Exception ignored) {}
                try { gatt.close(); } catch (Exception ignored) {}
            }
            callback.onResult("Délai GATT dépassé. L’accessoire ne permet peut-être pas une connexion directe.", -1, true);
        }, 12000);
    }

    private static String shortUuid(UUID uuid) {
        String value = uuid.toString().toLowerCase(Locale.ROOT);
        if (value.endsWith("-0000-1000-8000-00805f9b34fb") && value.startsWith("0000")) {
            return "0x" + value.substring(4, 8).toUpperCase(Locale.ROOT);
        }
        return value;
    }
}
