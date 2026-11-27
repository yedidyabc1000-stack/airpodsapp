package com.yedidyabc1000.airpodspause;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    public static final String PREFS = "airpause_preferences";
    public static final String PREF_MONITORING = "monitoring";
    public static final String PREF_AUTO_RESUME = "auto_resume";

    private static final int REQUEST_LOCATION = 41;
    private static final int REQUEST_BLUETOOTH = 42;

    private TextView statusText;
    private TextView connectionText;
    private TextView earText;
    private TextView modelText;
    private TextView scanText;
    private TextView rawScanText;
    private TextView bleRawText;
    private TextView podsScanText;
    private TextView caseScanText;
    private TextView proximityRawText;
    private TextView podsInfoText;
    private TextView caseInfoText;
    private Button monitorButton;
    private Button resumeButton;
    private SharedPreferences preferences;
    private boolean monitoring;
    private boolean autoResume = true;
    private boolean receiverRegistered;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(MonitorService.EXTRA_MESSAGE);
            String ear = intent.getStringExtra(MonitorService.EXTRA_EAR_STATE);
            String model = intent.getStringExtra(MonitorService.EXTRA_MODEL);
            String diagnostics = intent.getStringExtra(MonitorService.EXTRA_SCAN_DIAGNOSTICS);
            String rawHex = intent.getStringExtra(MonitorService.EXTRA_SCAN_RAW_HEX);
            String bleRawHex = intent.getStringExtra(MonitorService.EXTRA_BLE_RAW_HEX);
            String podsHex = intent.getStringExtra(MonitorService.EXTRA_PODS_HEX);
            String caseHex = intent.getStringExtra(MonitorService.EXTRA_CASE_HEX);
            String proximityRawHex = intent.getStringExtra(MonitorService.EXTRA_PROXIMITY_RAW_HEX);
            String podsInfo = intent.getStringExtra(MonitorService.EXTRA_PODS_INFO);
            String caseInfo = intent.getStringExtra(MonitorService.EXTRA_CASE_INFO);
            boolean connected = intent.getBooleanExtra(MonitorService.EXTRA_CONNECTED, false);
            if (message != null) statusText.setText(message);
            connectionText.setText(connected ? "מחובר: התקן שמע Bluetooth"
                    : "לא זוהה חיבור שמע פעיל");
            if ("WORN".equals(ear)) {
                earText.setText("שתי האוזניות באוזניים");
                earText.setTextColor(Color.rgb(50, 214, 180));
            } else if ("OUT".equals(ear)) {
                earText.setText("אחת או שתיהן מחוץ לאוזן");
                earText.setTextColor(Color.rgb(255, 177, 105));
            } else {
                earText.setText("ממתין לזיהוי ראשוני");
                earText.setTextColor(Color.rgb(181, 193, 212));
            }
            modelText.setText(model == null || model.length() == 0
                    ? "דגם: עדיין לא זוהה" : "דגם: " + model);
            if (diagnostics != null) {
                scanText.setText(diagnostics);
            }
            if (rawHex != null) {
                rawScanText.setText("Apple HEX: " + rawHex);
            }
            if (bleRawHex != null) {
                bleRawText.setText("BLE HEX אחרון: " + bleRawHex);
            }
            if (podsHex != null) {
                podsScanText.setText("אוזניות 07 19 HEX: " + podsHex);
            }
            if (caseHex != null) {
                caseScanText.setText("שידור 07 11 06 HEX: " + caseHex);
            }
            if (proximityRawHex != null) {
                proximityRawText.setText("הודעת Apple 07 האחרונה: " + proximityRawHex);
            }
            if (podsInfo != null) {
                podsInfoText.setText("אוזניות 07 19 — " + podsInfo);
            }
            if (caseInfo != null) {
                caseInfoText.setText("07 11 06 — " + caseInfo);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (Build.VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            window.setStatusBarColor(Color.rgb(8, 15, 28));
            window.setNavigationBarColor(Color.rgb(8, 15, 28));
        }

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        monitoring = preferences.getBoolean(PREF_MONITORING, false);
        autoResume = preferences.getBoolean(PREF_AUTO_RESUME, true);
        buildInterface();
        updateButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            registerReceiver(statusReceiver, new IntentFilter(MonitorService.ACTION_STATUS));
            receiverRegistered = true;
        }
        monitoring = preferences.getBoolean(PREF_MONITORING, false);
        autoResume = preferences.getBoolean(PREF_AUTO_RESUME, true);
        updateButtons();
    }

    @Override
    protected void onPause() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(statusReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
        super.onPause();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 18, 32));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(22));
        if (Build.VERSION.SDK_INT >= 17) {
            root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        scroll.addView(root);

        TextView title = text("AirPause", 31, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());
        TextView subtitle = text("עצירה חכמה למוזיקה עם AirPods", 15,
                Color.rgb(157, 174, 198), false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = fullWidth();
        subtitleParams.topMargin = dp(4);
        root.addView(subtitle, subtitleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(Color.rgb(22, 34, 56), 18, 0, 0));
        LinearLayout.LayoutParams cardParams = fullWidth();
        cardParams.topMargin = dp(24);
        root.addView(card, cardParams);

        TextView section = text("מצב המערכת", 14, Color.rgb(146, 164, 190), true);
        card.addView(section, fullWidth());

        statusText = text("מוכן להפעלה", 20, Color.WHITE, true);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(12);
        card.addView(statusText, statusParams);

        connectionText = text("לא זוהה חיבור שמע פעיל", 14,
                Color.rgb(181, 193, 212), false);
        LinearLayout.LayoutParams connectionParams = fullWidth();
        connectionParams.topMargin = dp(9);
        card.addView(connectionText, connectionParams);

        earText = text("ממתין לזיהוי ראשוני", 16,
                Color.rgb(181, 193, 212), true);
        LinearLayout.LayoutParams earParams = fullWidth();
        earParams.topMargin = dp(16);
        card.addView(earText, earParams);

        modelText = text("דגם: עדיין לא זוהה", 13,
                Color.rgb(146, 164, 190), false);
        LinearLayout.LayoutParams modelParams = fullWidth();
        modelParams.topMargin = dp(8);
        card.addView(modelText, modelParams);

        scanText = text("BLE חבילות: 0 | Apple: 0 | AirPods: 0",
                11, Color.rgb(126, 147, 176), false);
        LinearLayout.LayoutParams scanParams = fullWidth();
        scanParams.topMargin = dp(8);
        card.addView(scanText, scanParams);

        bleRawText = text("BLE HEX אחרון: ממתין לשידור",
                10, Color.rgb(190, 190, 210), false);
        bleRawText.setSingleLine(false);
        if (Build.VERSION.SDK_INT >= 17) {
            bleRawText.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        bleRawText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bleRawParams = fullWidth();
        bleRawParams.topMargin = dp(5);
        card.addView(bleRawText, bleRawParams);

        rawScanText = text("Apple HEX: ממתין לפרסומת Apple",
                10, Color.rgb(126, 147, 176), false);
        rawScanText.setSingleLine(false);
        LinearLayout.LayoutParams rawParams = fullWidth();
        rawParams.topMargin = dp(5);
        card.addView(rawScanText, rawParams);

        podsScanText = text(
                "אוזניות 07 19 HEX: עדיין לא נקלט שידור Proximity תקין",
                10, Color.rgb(164, 210, 190), false);
        podsScanText.setSingleLine(false);
        LinearLayout.LayoutParams podsParams = fullWidth();
        podsParams.topMargin = dp(5);
        card.addView(podsScanText, podsParams);

        podsInfoText = text("אוזניות 07 19 — טרם נקלט שידור כזה",
                10, Color.rgb(164, 210, 190), false);
        podsInfoText.setSingleLine(false);
        LinearLayout.LayoutParams podsInfoParams = fullWidth();
        podsInfoParams.topMargin = dp(5);
        card.addView(podsInfoText, podsInfoParams);

        caseScanText = text(
                "מסגרת 07 11 06 (לא מאומתת): אינה משמשת לזיהוי AirPods",
                10, Color.rgb(164, 190, 210), false);
        caseScanText.setSingleLine(false);
        LinearLayout.LayoutParams caseParams = fullWidth();
        caseParams.topMargin = dp(5);
        card.addView(caseScanText, caseParams);

        caseInfoText = text("07 11 06 — פורמט לא מאומת, אינו משמש לזיהוי מצב הלבישה",
                10, Color.rgb(164, 190, 210), false);
        caseInfoText.setSingleLine(false);
        LinearLayout.LayoutParams caseInfoParams = fullWidth();
        caseInfoParams.topMargin = dp(5);
        card.addView(caseInfoText, caseInfoParams);

        proximityRawText = text("הודעת Apple 07 האחרונה: טרם נקלטה",
                10, Color.rgb(190, 190, 210), false);
        proximityRawText.setSingleLine(false);
        LinearLayout.LayoutParams proximityRawParams = fullWidth();
        proximityRawParams.topMargin = dp(5);
        card.addView(proximityRawText, proximityRawParams);

        monitorButton = new Button(this);
        monitorButton.setTextSize(18);
        monitorButton.setTextColor(Color.rgb(7, 24, 29));
        monitorButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        monitorButton.setGravity(Gravity.CENTER);
        monitorButton.setMinHeight(dp(54));
        monitorButton.setPadding(dp(12), dp(8), dp(12), dp(8));
        monitorButton.setFocusable(true);
        monitorButton.setFocusableInTouchMode(false);
        monitorButton.setBackground(statefulButton(
                Color.rgb(50, 214, 180), Color.rgb(255, 255, 255)));
        LinearLayout.LayoutParams buttonParams = fullWidth();
        buttonParams.topMargin = dp(22);
        root.addView(monitorButton, buttonParams);
        monitorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (monitoring) {
                    stopMonitoring();
                } else {
                    requestStart();
                }
            }
        });

        resumeButton = new Button(this);
        resumeButton.setTextSize(15);
        resumeButton.setTextColor(Color.WHITE);
        resumeButton.setGravity(Gravity.CENTER);
        resumeButton.setMinHeight(dp(48));
        resumeButton.setPadding(dp(10), dp(6), dp(10), dp(6));
        resumeButton.setFocusable(true);
        resumeButton.setFocusableInTouchMode(false);
        resumeButton.setBackground(statefulButton(
                Color.rgb(37, 53, 79), Color.rgb(50, 214, 180)));
        LinearLayout.LayoutParams resumeParams = fullWidth();
        resumeParams.topMargin = dp(10);
        root.addView(resumeButton, resumeParams);
        resumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                autoResume = !autoResume;
                preferences.edit().putBoolean(PREF_AUTO_RESUME, autoResume).apply();
                updateButtons();
            }
        });

        TextView instructions = text(
                "איך מתחילים?\n"
                        + "1. חבר את ה-AirPods ב-Bluetooth כאוזניות שמע.\n"
                        + "2. הפעל ניטור. הזיהוי מתחיל מיד עם קבלת שידור Bluetooth תקין מה-AirPods.\n"
                        + "3. לאחר שהאפליקציה רואה את שתי האוזניות באוזניים, הוצאה של אחת מהן תשלח פקודת עצירה.\n\n"
                        + "ב-Android 6 ומעלה יש לאשר הרשאת מיקום לסריקת Bluetooth. האפליקציה אינה משתמשת במיקום.",
                14, Color.rgb(172, 187, 207), false);
        instructions.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams infoParams = fullWidth();
        infoParams.topMargin = dp(22);
        root.addView(instructions, infoParams);

        TextView keypad = text("ניווט במקשים: חצים למעלה/למטה ואישור OK או Enter",
                12, Color.rgb(111, 130, 157), false);
        keypad.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams keypadParams = fullWidth();
        keypadParams.topMargin = dp(22);
        root.addView(keypad, keypadParams);

        setContentView(scroll);
        monitorButton.requestFocus();
    }

    private void requestStart() {
        if (!getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            statusText.setText("המכשיר אינו תומך Bluetooth LE");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            statusText.setText("אין במכשיר Bluetooth");
            return;
        }
        if (!adapter.isEnabled()) {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                        REQUEST_BLUETOOTH);
            } catch (RuntimeException e) {
                statusText.setText("יש להפעיל Bluetooth בהגדרות המכשיר");
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION);
            return;
        }

        startMonitoring();
    }

    private void startMonitoring() {
        preferences.edit().putBoolean(PREF_MONITORING, true).apply();
        monitoring = true;
        updateButtons();

        Intent service = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        statusText.setText("מפעיל סריקת AirPods...");
    }

    private void stopMonitoring() {
        preferences.edit().putBoolean(PREF_MONITORING, false).apply();
        monitoring = false;
        stopService(new Intent(this, MonitorService.class));
        statusText.setText("הניטור הופסק");
        earText.setText("ממתין לזיהוי ראשוני");
        updateButtons();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitoring();
            } else {
                statusText.setText("כדי לסרוק Bluetooth יש לאשר הרשאת מיקום");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BLUETOOTH) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                requestStart();
            } else {
                statusText.setText("Bluetooth עדיין כבוי");
            }
        }
    }

    private void updateButtons() {
        if (monitorButton != null) {
            monitorButton.setText(monitoring ? "עצור ניטור" : "הפעל ניטור");
        }
        if (resumeButton != null) {
            resumeButton.setText(autoResume
                    ? "חידוש נגינה בהחזרה: פעיל"
                    : "חידוש נגינה בהחזרה: כבוי");
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= 17) {
            view.setTextDirection(View.TEXT_DIRECTION_RTL);
        }
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private StateListDrawable statefulButton(int normalColor, int focusColor) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                rounded(focusColor, 14, 2, Color.WHITE));
        states.addState(new int[]{android.R.attr.state_pressed},
                rounded(focusColor, 14, 2, Color.WHITE));
        states.addState(new int[]{},
                rounded(normalColor, 14, 1, Color.rgb(65, 86, 115)));
        return states;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
