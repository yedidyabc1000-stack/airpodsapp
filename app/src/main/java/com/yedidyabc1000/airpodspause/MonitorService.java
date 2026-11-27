package com.yedidyabc1000.airpodspause;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.List;

/**
 * Persistent BLE monitor. API 18's legacy startLeScan is intentionally used so the
 * application can run on Android 4.4 / API 19.
 */
public class MonitorService extends Service {
    public static final String ACTION_STATUS = "com.yedidyabc1000.airpodspause.STATUS";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_EAR_STATE = "ear_state";
    public static final String EXTRA_CONNECTED = "connected";
    public static final String EXTRA_MODEL = "model";
    public static final String EXTRA_SCAN_DIAGNOSTICS = "scan_diagnostics";
    public static final String EXTRA_SCAN_RAW_HEX = "scan_raw_hex";

    private static final String CHANNEL_ID = "airpause_monitor";
    private static final int NOTIFICATION_ID = 4107;
    private static final Object LOCK = new Object();
    private static final int STATE_CONFIRMATION_ADVERTISEMENTS = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothProfile a2dpProfile;
    private boolean scanStarted;
    private boolean receiverRegistered;
    private boolean hasEarBaseline;
    private boolean lastBothInEar;
    private boolean candidateBothInEar;
    private int candidateSampleCount;
    private boolean pausedByAirPause;
    private String currentMessage = "מאתחל ניטור";
    private String currentEarState = "UNKNOWN";
    private String currentModel = "";
    private PowerManager.WakeLock wakeLock;
    private Handler mainHandler;
    private volatile long lastBleCallbackAt;
    private long scanStartedAt;
    private int blePacketCount;
    private int applePacketCount;
    private int airPodsPacketCount;
    private int lastRssi;
    private int scanRecoveryCount;
    private boolean scanRestartPending;
    private String latestAppleHex = "אין עדיין פרסומת Apple";

    private final Runnable scanWatchdog = new Runnable() {
        @Override
        public void run() {
            if (mainHandler == null) {
                return;
            }

            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                if (!scanStarted) {
                    startBleScan();
                } else {
                    long now = SystemClock.elapsedRealtime();
                    long reference = lastBleCallbackAt == 0
                            ? scanStartedAt : lastBleCallbackAt;
                    if (now - reference >= 15000 && !scanRestartPending) {
                        scanRestartPending = true;
                        scanRecoveryCount++;
                        setMessage("Qin S1+: לא התקבלו שידורי BLE במשך 15 שניות — מאתחל סריקה");
                        stopBleScan();
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                scanRestartPending = false;
                                startBleScan();
                            }
                        }, 1000);
                    } else {
                        publishState();
                    }
                }
            }

            mainHandler.postDelayed(this, 10000);
        }
    };

    private final BluetoothProfile.ServiceListener profileListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = proxy;
                        publishState();
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = null;
                        publishState();
                    }
                }
            };

    private final BluetoothAdapter.LeScanCallback scanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    long now = SystemClock.elapsedRealtime();
                    lastBleCallbackAt = now;
                    AirPodsAdvertisementParser.Result result =
                            AirPodsAdvertisementParser.parseScanRecord(scanRecord);
                    synchronized (LOCK) {
                        blePacketCount++;
                        lastRssi = rssi;
                        if (AirPodsAdvertisementParser.containsAppleManufacturerData(scanRecord)) {
                            applePacketCount++;
                            latestAppleHex =
                                    AirPodsAdvertisementParser.getAppleManufacturerDataHex(scanRecord);
                        }
                        if (result != null) {
                            airPodsPacketCount++;
                        }
                    }
                    if (result == null) {
                        return;
                    }

                    // Do not wait for A2DP profile initialization before processing
                    // an ear-state advertisement.
                    processAdvertisement(result);
                }
            };

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    startBleScan();
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    stopBleScan();
                    resetEarTracking();
                    setMessage("Bluetooth כבוי");
                }
            } else {
                publishState();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        resetEarTracking();
        mainHandler.postDelayed(scanWatchdog, 10000);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("ממתין לחיבור AirPods"));

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "AirPause:BleMonitor");
            wakeLock.setReferenceCounted(false);
            try {
                wakeLock.acquire();
            } catch (RuntimeException ignored) {
                wakeLock = null;
            }
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        registerReceiver(bluetoothReceiver, filter);
        receiverRegistered = true;

        if (bluetoothAdapter == null) {
            setMessage("המכשיר אינו כולל Bluetooth");
            return;
        }

        try {
            bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP);
        } catch (RuntimeException ignored) {
            // The scan can still run; connection state will be checked through the adapter.
        }
        startBleScan();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startBleScan();
        return START_STICKY;
    }

    private void startBleScan() {
        if (bluetoothAdapter == null) {
            return;
        }
        if (!getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            setMessage("המכשיר אינו תומך Bluetooth LE");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            setMessage("נדרשת הרשאת מיקום לסריקת Bluetooth");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setMessage("הפעל Bluetooth כדי להתחיל");
            return;
        }
        if (scanStarted) {
            return;
        }

        try {
            scanStarted = bluetoothAdapter.startLeScan(scanCallback);
            if (scanStarted) {
                scanStartedAt = SystemClock.elapsedRealtime();
                lastBleCallbackAt = 0;
                setMessage("סריקת BLE פעילה — מחפש AirPods Pro 3");
            } else {
                setMessage("סריקת Bluetooth לא התחילה; נסה להפעיל Bluetooth מחדש");
            }
        } catch (SecurityException e) {
            setMessage("אין הרשאה לסריקת Bluetooth");
        } catch (RuntimeException e) {
            setMessage("שגיאה בהפעלת סריקת Bluetooth");
        }
    }

    private void stopBleScan() {
        if (bluetoothAdapter != null && scanStarted) {
            try {
                bluetoothAdapter.stopLeScan(scanCallback);
            } catch (RuntimeException ignored) {
            }
        }
        scanStarted = false;
    }

    private boolean isA2dpAudioConnected() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }

        try {
            if (a2dpProfile != null) {
                List<BluetoothDevice> connected = a2dpProfile.getConnectedDevices();
                if (connected != null && !connected.isEmpty()) {
                    return true;
                }
            }
            return bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP)
                    == BluetoothProfile.STATE_CONNECTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void resetEarTracking() {
        synchronized (LOCK) {
            hasEarBaseline = false;
            lastBothInEar = false;
            candidateBothInEar = false;
            candidateSampleCount = 0;
            currentEarState = "UNKNOWN";
            currentModel = "";
            currentMessage = "סורק לאיתור AirPods Pro 3";
            pausedByAirPause = false;
        }
    }

    private void processAdvertisement(AirPodsAdvertisementParser.Result result) {
        // When an AirPod inside the case advertises, its two ear-state bits
        // describe a different physical broadcaster. Use only out-of-case frames.
        if (result.broadcasterIsInCase) {
            return;
        }

        String oldMessage;
        String oldEarState;
        String oldModel;
        synchronized (LOCK) {
            oldMessage = currentMessage;
            oldEarState = currentEarState;
            oldModel = currentModel;
            currentModel = result.modelName;
            boolean bothInEar = result.areBothInEar();

            // Establish the baseline from the very first valid BLE packet. We no
            // longer force the user to wait until both buds have first been seen
            // in-ear before the app can report that the AirPods were detected.
            if (!hasEarBaseline) {
                hasEarBaseline = true;
                lastBothInEar = bothInEar;
                candidateBothInEar = bothInEar;
                candidateSampleCount = 0;
                currentEarState = bothInEar ? "WORN" : "OUT";
                currentMessage = bothInEar
                        ? "AirPods Pro 3 זוהו — שתי האוזניות באוזניים"
                        : "AirPods Pro 3 זוהו — הכנס את שתי האוזניות לאוזניים";
            } else if (bothInEar == lastBothInEar) {
                // A frame matching the stable state cancels a transient candidate.
                candidateSampleCount = 0;
                currentEarState = lastBothInEar ? "WORN" : "OUT";
            } else {
                // Confirm a real change with two consecutive public advertisements.
                // This filters isolated BLE frames without adding a long polling delay.
                if (candidateSampleCount == 0 || candidateBothInEar != bothInEar) {
                    candidateBothInEar = bothInEar;
                    candidateSampleCount = 1;
                } else {
                    candidateSampleCount++;
                }

                if (candidateSampleCount >= STATE_CONFIRMATION_ADVERTISEMENTS) {
                    boolean wasBothInEar = lastBothInEar;
                    lastBothInEar = bothInEar;
                    candidateSampleCount = 0;
                    currentEarState = bothInEar ? "WORN" : "OUT";

                    if (wasBothInEar && !bothInEar) {
                        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                        boolean wasPlaying = audio != null && audio.isMusicActive();
                        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
                        pausedByAirPause = wasPlaying;
                        currentMessage = "אוזנייה הוצאה — נשלחה פקודת עצירה";
                    } else if (!wasBothInEar && bothInEar) {
                        boolean autoResume = getSharedPreferences(MainActivity.PREFS,
                                MODE_PRIVATE).getBoolean(MainActivity.PREF_AUTO_RESUME, true);
                        if (pausedByAirPause && autoResume) {
                            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                            currentMessage = "האוזניות הוחזרו — המוזיקה חודשה";
                        } else {
                            currentMessage = "שתי האוזניות זוהו באוזניים";
                        }
                        pausedByAirPause = false;
                    }
                } else {
                    currentEarState = bothInEar ? "WORN" : "OUT";
                    currentMessage = "ממתין לאימות שינוי במצב הלבישה";
                }
            }
        }

        boolean changed;
        synchronized (LOCK) {
            changed = !oldMessage.equals(currentMessage)
                    || !oldEarState.equals(currentEarState)
                    || !oldModel.equals(currentModel);
        }
        if (changed) {
            publishState();
        }
    }

    private void sendMediaKey(int keyCode) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now,
                    KeyEvent.ACTION_DOWN, keyCode, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(now + 1, now + 1,
                    KeyEvent.ACTION_UP, keyCode, 0));
        } catch (RuntimeException ignored) {
        }
    }

    private void setMessage(String message) {
        synchronized (LOCK) {
            currentMessage = message;
        }
        publishState();
    }

    private void publishState() {
        final String message;
        final String earState;
        final String model;
        final String diagnostics;
        final String rawHex;
        synchronized (LOCK) {
            message = currentMessage;
            earState = currentEarState;
            model = currentModel;
            diagnostics = "BLE חבילות: " + blePacketCount
                    + " | Apple: " + applePacketCount
                    + " | AirPods: " + airPodsPacketCount
                    + " | RSSI: " + lastRssi
                    + " | אתחולי סריקה: " + scanRecoveryCount;
            rawHex = latestAppleHex;
        }

        boolean connected = isA2dpAudioConnected();
        Intent update = new Intent(ACTION_STATUS);
        update.setPackage(getPackageName());
        update.putExtra(EXTRA_MESSAGE, message);
        update.putExtra(EXTRA_EAR_STATE, earState);
        update.putExtra(EXTRA_MODEL, model);
        update.putExtra(EXTRA_CONNECTED, connected);
        update.putExtra(EXTRA_SCAN_DIAGNOSTICS, diagnostics);
        update.putExtra(EXTRA_SCAN_RAW_HEX, rawHex);
        sendBroadcast(update);

        if (mainHandler != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    NotificationManager manager =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, buildNotification(message));
                    }
                }
            });
        }
    }

    private Notification buildNotification(String message) {
        Intent openApp = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openApp, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AirPause פעיל")
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "ניטור AirPods", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("התראה קבועה בזמן זיהוי מצב האוזניות");
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(scanWatchdog);
        }
        stopBleScan();
        if (receiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bluetoothAdapter != null && a2dpProfile != null) {
            try {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile);
            } catch (RuntimeException ignored) {
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (RuntimeException ignored) {
            }
        }
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putBoolean(MainActivity.PREF_MONITORING, false).apply();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
