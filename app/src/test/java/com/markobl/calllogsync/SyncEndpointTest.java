package com.markobl.calllogsync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.net.URL;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(RobolectricTestRunner.class)
public class SyncEndpointTest {

    @Test
    public void endpointTestSendsOnlySyntheticEmptyPayload() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Config config = Config.newConfig(context);

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();
            config.endpoint = server.url("/test").url();

            SyncResult result = Sync.testEndpointBlocking(config);
            RecordedRequest request = server.takeRequest();

            assertEquals(SyncResultType.SUCCESS, result.syncResultType);
            assertEquals("[]", request.getBody().readUtf8());
            assertEquals("1", request.getHeader("Test-Run"));
        }
    }

    @Test
    public void productionAllowsOnlyHttpsEndpoints() throws Exception {
        assertTrue(Sync.isAllowedEndpoint(new URL("https://api.example.com"), false));
        assertFalse(Sync.isAllowedEndpoint(new URL("http://api.example.com"), false));
        assertTrue(Sync.isAllowedEndpoint(new URL("http://localhost"), true));
    }

    @Test
    public void everyTwoHundredResponseIsSuccessful() {
        assertTrue(Sync.isSuccessfulHttpCode(200));
        assertTrue(Sync.isSuccessfulHttpCode(204));
        assertTrue(Sync.isSuccessfulHttpCode(299));
        assertFalse(Sync.isSuccessfulHttpCode(300));
    }
}
