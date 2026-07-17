package com.markobl.calllogsync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
public class SyncSchedulerTest {

    private Context context;
    private WorkManager workManager;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);
        workManager = WorkManager.getInstance(context);
        workManager.cancelAllWork().getResult().get();
        Config.getSharedPreferences(context).edit().clear().commit();
    }

    @After
    public void tearDown() throws Exception {
        workManager.cancelAllWork().getResult().get();
        Config.getSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void enabledReconciliationCreatesOnePeriodicAndOneImmediateWork() throws Exception {
        Sync.setEnabled(context, true);

        SyncWorker.reconcile(context);
        UUID periodicId = unfinishedWorkId(SyncWorker.PERIODIC_WORK_NAME);
        SyncWorker.reconcile(context);

        assertEquals(1, unfinishedWork(SyncWorker.PERIODIC_WORK_NAME));
        assertEquals(1, unfinishedWork(SyncWorker.IMMEDIATE_WORK_NAME));
        assertEquals(periodicId, unfinishedWorkId(SyncWorker.PERIODIC_WORK_NAME));
    }

    @Test
    public void reconciliationReplacesRetryingImmediateWorkAndRemainsUnique() throws Exception {
        Sync.setEnabled(context, true);
        OneTimeWorkRequest retrying = new OneTimeWorkRequest.Builder(RetryWorker.class).build();
        workManager.enqueueUniqueWork(
                SyncWorker.IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, retrying).getResult().get();

        WorkInfo retryInfo = workManager.getWorkInfoById(retrying.getId()).get();
        assertEquals(WorkInfo.State.ENQUEUED, retryInfo.getState());
        assertEquals(1, retryInfo.getRunAttemptCount());

        SyncWorker.reconcile(context);

        assertEquals(1, unfinishedWork(SyncWorker.IMMEDIATE_WORK_NAME));
        assertFalse(retrying.getId().equals(unfinishedWorkId(SyncWorker.IMMEDIATE_WORK_NAME)));
    }

    @Test
    public void disabledReconciliationCancelsScheduledWork() throws Exception {
        Sync.setEnabled(context, true);
        SyncWorker.reconcile(context);

        Sync.setEnabled(context, false);
        SyncWorker.reconcile(context);

        assertEquals(0, unfinishedWork(SyncWorker.PERIODIC_WORK_NAME));
        assertEquals(0, unfinishedWork(SyncWorker.IMMEDIATE_WORK_NAME));
        assertFalse(Sync.isEnabled(context));
    }

    private int unfinishedWork(String uniqueName) throws Exception {
        List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork(uniqueName).get();
        int unfinished = 0;
        for (WorkInfo workInfo : workInfos) {
            if (!workInfo.getState().isFinished())
                unfinished++;
        }
        return unfinished;
    }

    private UUID unfinishedWorkId(String uniqueName) throws Exception {
        for (WorkInfo workInfo : workManager.getWorkInfosForUniqueWork(uniqueName).get()) {
            if (!workInfo.getState().isFinished())
                return workInfo.getId();
        }
        return null;
    }

    public static class RetryWorker extends Worker {
        public RetryWorker(Context context, WorkerParameters workerParameters) {
            super(context, workerParameters);
        }

        @Override
        public ListenableWorker.Result doWork() {
            return ListenableWorker.Result.retry();
        }
    }
}
