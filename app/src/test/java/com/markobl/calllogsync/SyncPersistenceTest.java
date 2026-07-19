package com.markobl.calllogsync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    @Test
    public void newInstallUsesLivessProductionEndpoint() {
        Config config = Config.newConfig(context);

        assertEquals(Config.DEFAULT_ENDPOINT, config.endpoint.toString());
    }

    @Test
    public void existingInstallWithoutEndpointIsMigrated() {
        Config config = Config.newConfig(context);
        config.endpoint = null;
        assertTrue(config.save(context));

        Config loaded = Config.load(context);

        assertEquals(Config.DEFAULT_ENDPOINT, loaded.endpoint.toString());
    }

    @Test
    public void missingTokenIsGeneratedAndPersisted() {
        Config config = Config.newConfig(context);
        config.deviceToken = "";
        assertTrue(config.save(context));

        Config loaded = Config.load(context);
        String firstToken = loaded.deviceToken;
        assertNotNull(firstToken);
        assertTrue(!firstToken.trim().isEmpty());

        Config loadedAgain = Config.load(context);
        assertEquals(firstToken, loadedAgain.deviceToken);
    }
}
