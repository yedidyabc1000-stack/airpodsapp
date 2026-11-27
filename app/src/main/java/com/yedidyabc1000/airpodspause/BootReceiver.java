package com.yedidyabc1000.airpodspause;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** Restarts the monitor after reboot when the user previously enabled it. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        SharedPreferences preferences =
                context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!preferences.getBoolean(MainActivity.PREF_MONITORING, false)) {
            return;
        }

        Intent service = new Intent(context, MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
