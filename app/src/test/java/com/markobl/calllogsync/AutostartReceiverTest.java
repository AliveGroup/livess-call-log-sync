package com.markobl.calllogsync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

public class AutostartReceiverTest {

    @Test
    public void reconcilesAfterPackageUpdateWithoutOpeningTheApp() {
        assertTrue(AutostartReceiver.isReconcileAction(Intent.ACTION_MY_PACKAGE_REPLACED));
    }

    @Test
    public void reconcilesAfterBoot() {
        assertTrue(AutostartReceiver.isReconcileAction(Intent.ACTION_BOOT_COMPLETED));
    }

    @Test
    public void ignoresUnrelatedBroadcasts() {
        assertFalse(AutostartReceiver.isReconcileAction(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        assertFalse(AutostartReceiver.isReconcileAction(null));
    }
}
