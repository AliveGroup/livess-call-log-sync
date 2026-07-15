package com.markobl.calllogsync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SyncPersistenceTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Config.getSharedPreferences(context).edit().clear().commit();
    }

    @After
    public void tearDown() {
        Config.getSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void enabledStateIsImmediatelyReadable() {
        assertTrue(Sync.setEnabled(context, true));
        assertTrue(Sync.isEnabled(context));

        assertTrue(Sync.setEnabled(context, false));
        assertFalse(Sync.isEnabled(context));
    }

    @Test
    public void cursorIsCommittedBeforeReturning() {
        assertTrue(Config.setLastCallLogId(context, 42L));
        assertEquals(42L, Config.getLastCallLogId(context));
    }
}
