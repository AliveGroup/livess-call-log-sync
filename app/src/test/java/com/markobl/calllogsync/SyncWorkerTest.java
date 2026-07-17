package com.markobl.calllogsync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SyncWorkerTest {

    @Test
    public void retryableResponsesIncludeTransientHttpFailures() {
        assertTrue(SyncWorker.isRetryable(new SyncResult("timeout", 408)));
        assertTrue(SyncWorker.isRetryable(new SyncResult("rate limited", 429)));
        assertTrue(SyncWorker.isRetryable(new SyncResult("server error", 500)));
        assertTrue(SyncWorker.isRetryable(new SyncResult("unavailable", 503)));
    }

    @Test
    public void retryableResponsesExcludePermanentHttpFailuresAndSuccess() {
        assertFalse(SyncWorker.isRetryable(new SyncResult("bad request", 400)));
        assertFalse(SyncWorker.isRetryable(new SyncResult("unauthorized", 401)));
        assertFalse(SyncWorker.isRetryable(new SyncResult("ok", 10L, false, 1)));
        assertFalse(SyncWorker.isRetryable(new SyncResult()));
    }
}
