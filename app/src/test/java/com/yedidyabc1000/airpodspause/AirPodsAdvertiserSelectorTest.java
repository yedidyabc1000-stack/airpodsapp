package com.yedidyabc1000.airpodspause;

import org.junit.Test;

import static org.junit.Assert.*;

public class AirPodsAdvertiserSelectorTest {
    @Test
    public void requiresRepeatedAdvertisementFromSameAddress() {
        AirPodsAdvertiserSelector selector = new AirPodsAdvertiserSelector();

        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:01", -70, 1000L));
        assertTrue(selector.shouldProcess("AA:BB:CC:DD:EE:01", -72, 1300L));
    }

    @Test
    public void choosesStrongestRecentAdvertiser() {
        AirPodsAdvertiserSelector selector = new AirPodsAdvertiserSelector();

        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:01", -80, 1000L));
        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:02", -55, 1100L));
        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:01", -79, 1200L));
        assertTrue(selector.shouldProcess("AA:BB:CC:DD:EE:02", -56, 1300L));
    }

    @Test
    public void expiresOldAdvertisersAfterTenSeconds() {
        AirPodsAdvertiserSelector selector = new AirPodsAdvertiserSelector();

        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:01", -60, 1000L));
        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:02", -80, 12000L));
        assertTrue(selector.shouldProcess("AA:BB:CC:DD:EE:02", -81, 12500L));
    }

    @Test
    public void resetClearsRecentSamples() {
        AirPodsAdvertiserSelector selector = new AirPodsAdvertiserSelector();

        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:01", -60, 1000L));
        selector.reset();
        assertFalse(selector.shouldProcess("AA:BB:CC:DD:EE:01", -60, 1100L));
        assertTrue(selector.shouldProcess("AA:BB:CC:DD:EE:01", -61, 1200L));
    }
}
