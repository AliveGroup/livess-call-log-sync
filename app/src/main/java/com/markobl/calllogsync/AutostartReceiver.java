package com.markobl.calllogsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AutostartReceiver extends BroadcastReceiver
{
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();
        if (isReconcileAction(action)) {
            PendingResult pendingResult = goAsync();
            Context applicationContext = context.getApplicationContext();
            new Thread(() -> {
                try {
                    SyncWorker.reconcileBlocking(applicationContext);
                } finally {
                    pendingResult.finish();
                }
            }, "call-sync-boot-reconcile").start();
        }
    }

    static boolean isReconcileAction(String action) {
        return Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
    }
}
