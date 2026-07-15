package com.markobl.calllogsync;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {

    public static final String BROADCAST_SYNC_DONE = "com.markobl.calllogsync.sync-done";
    private static final String LEGACY_PERIODIC_WORK_NAME = "sync";
    static final String PERIODIC_WORK_NAME = "call-sync-periodic";
    static final String IMMEDIATE_WORK_NAME = "call-sync-immediate";
    private static final Object SYNC_LOCK = new Object();

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void reconcile(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME);

        if (Sync.isEnabled(context)) {
            ensurePeriodicWork(workManager);
            ensureImmediateWork(workManager);
            return;
        }

        workManager.cancelUniqueWork(PERIODIC_WORK_NAME);
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME);
    }

    public static void syncNow(Context context) {
        if (Sync.isEnabled(context))
            ensureImmediateWork(WorkManager.getInstance(context));
    }

    static void reconcileBlocking(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        try {
            workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME).getResult().get();
            if (Sync.isEnabled(context)) {
                ensurePeriodicWorkBlocking(workManager);
                ensureImmediateWorkBlocking(workManager);
                return;
            }

            workManager.cancelUniqueWork(PERIODIC_WORK_NAME).getResult().get();
            workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME).getResult().get();
        } catch (Exception ignored) {
            // Keep the boot receiver lifecycle-safe even when state inspection fails.
            try {
                if (Sync.isEnabled(context)) {
                    PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                            SyncWorker.class, 15, TimeUnit.MINUTES)
                            .setConstraints(connectedNetworkConstraints())
                            .build();
                    workManager.enqueueUniquePeriodicWork(
                            PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, periodic)
                            .getResult().get(3, TimeUnit.SECONDS);

                    OneTimeWorkRequest immediate = new OneTimeWorkRequest.Builder(SyncWorker.class)
                            .setConstraints(connectedNetworkConstraints())
                            .build();
                    workManager.enqueueUniqueWork(
                            IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, immediate)
                            .getResult().get(3, TimeUnit.SECONDS);
                } else {
                    workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                            .getResult().get(3, TimeUnit.SECONDS);
                    workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                            .getResult().get(3, TimeUnit.SECONDS);
                }
            } catch (Exception ignoredFallback) {
                // A later app open or boot event will retry reconciliation.
            }
        }
    }

    private static void ensurePeriodicWork(WorkManager workManager) {
        ListenableFuture<List<WorkInfo>> workInfos =
                workManager.getWorkInfosForUniqueWork(PERIODIC_WORK_NAME);
        workInfos.addListener(() -> {
            if (hasHealthyWork(workInfos))
                return;

            Constraints constraints = connectedNetworkConstraints();
            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                    SyncWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();
            workManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, work);
        }, Runnable::run);
    }

    private static void ensureImmediateWork(WorkManager workManager) {
        ListenableFuture<List<WorkInfo>> workInfos =
                workManager.getWorkInfosForUniqueWork(IMMEDIATE_WORK_NAME);
        workInfos.addListener(() -> {
            if (hasHealthyWork(workInfos))
                return;

            OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setConstraints(connectedNetworkConstraints())
                    .build();
            workManager.enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, work);
        }, Runnable::run);
    }

    private static void ensurePeriodicWorkBlocking(WorkManager workManager) throws Exception {
        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork(PERIODIC_WORK_NAME).get();
        if (hasHealthyWork(workInfos))
            return;

        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(connectedNetworkConstraints())
                .build();
        workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, work)
                .getResult().get();
    }

    private static void ensureImmediateWorkBlocking(WorkManager workManager) throws Exception {
        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork(IMMEDIATE_WORK_NAME).get();
        if (hasHealthyWork(workInfos))
            return;

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(connectedNetworkConstraints())
                .build();
        workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, work)
                .getResult().get();
    }

    private static Constraints connectedNetworkConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    private static boolean hasHealthyWork(ListenableFuture<List<WorkInfo>> workInfos) {
        try {
            return hasHealthyWork(workInfos.get());
        } catch (Exception ignored) {
            // Replacing is the safe fallback when WorkManager state cannot be read.
        }
        return false;
    }

    private static boolean hasHealthyWork(List<WorkInfo> workInfos) {
        for (WorkInfo workInfo : workInfos) {
            WorkInfo.State state = workInfo.getState();
            if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.BLOCKED)
                return true;
            if (state == WorkInfo.State.ENQUEUED && workInfo.getRunAttemptCount() == 0)
                return true;
        }
        return false;
    }

    public Worker.Result doWork() {
        synchronized (SYNC_LOCK) {
            return performSync();
        }
    }

    private Worker.Result performSync() {

        final Context context = getApplicationContext();
        if(!Sync.isEnabled(context))
            return Worker.Result.success();

        final Config config = Config.load(context);
        final long lastId = Config.getLastCallLogId(context);

        SyncResult syncResult = Sync.syncCallHistoryBlocking(context, config, lastId);

        if (isStopped())
            return ListenableWorker.Result.success();

        final LogItem logItem = new LogItem(context, syncResult);
        LogItem.addLogItem(context, logItem);

        Intent intent = new Intent(BROADCAST_SYNC_DONE);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(intent);

        if (syncResult.syncResultType == SyncResultType.SUCCESS) {
            if (isStopped())
                return ListenableWorker.Result.success();
            if (!Config.setLastCallLogId(context, syncResult.lastCallLogId))
                return ListenableWorker.Result.retry();
            return ListenableWorker.Result.success();
        }

        if (syncResult.syncResultType == SyncResultType.NOTHING_TO_SYNC
                && syncResult.lastCallLogId >= 0) {
            if (isStopped())
                return ListenableWorker.Result.success();
            if (!Config.setLastCallLogId(context, syncResult.lastCallLogId))
                return ListenableWorker.Result.retry();
        }

        if (syncResult.syncResultType == SyncResultType.EXCEPTION || isRetryable(syncResult))
            return ListenableWorker.Result.retry();

        return ListenableWorker.Result.success();
    }

    static boolean isRetryable(SyncResult syncResult) {
        return syncResult.syncResultType == SyncResultType.FAILURE
                && (syncResult.responseCode == 408
                || syncResult.responseCode == 429
                || syncResult.responseCode >= 500);
    }
}
