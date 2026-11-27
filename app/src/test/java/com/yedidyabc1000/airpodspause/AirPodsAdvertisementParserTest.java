package com.yedidyabc1000.airpodspause;

import org.junit.Test;

import static org.junit.Assert.*;

public class AirPodsAdvertisementParserTest {
    @Test
    public void parsesAirPodsProOneWithBothPodsInEar() {
        byte[] data = new byte[]{
                0x07, 0x19, 0x01, 0x0E, 0x20, 0x2B,
                (byte) 0xAA, (byte) 0xB5, 0x31
        };
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(data);

        assertNotNull(result);
        assertEquals("AirPods Pro דור 1", result.modelName);
        assertTrue(result.leftInEar);
        assertTrue(result.rightInEar);
        assertTrue(result.areBothInEar());
    }

    @Test
    public void parsesOnePodRemoved() {
        byte[] data = new byte[]{
                0x07, 0x19, 0x01, 0x0E, 0x20, 0x22,
                (byte) 0xAA, (byte) 0xB5, 0x31
        };
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(data);

        assertNotNull(result);
        assertTrue(result.leftInEar);
        assertFalse(result.rightInEar);
        assertFalse(result.areBothInEar());
    }

    @Test
    public void marksAdvertisementsFromPodInsideCase() {
        byte[] data = new byte[]{
                0x07, 0x19, 0x01, 0x0E, 0x20, 0x54,
                (byte) 0xAA, (byte) 0xB5, 0x31
        };
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(data);

        assertNotNull(result);
        assertTrue(result.broadcasterIsInCase);
    }

    @Test
    public void ignoresUnknownModelsAndMalformedFrames() {
        assertNull(AirPodsAdvertisementParser.parseManufacturerData(
                new byte[]{0x07, 0x19, 0x01, 0x55, 0x55, 0x2B, 0, 0, 0}));
        assertNull(AirPodsAdvertisementParser.parseManufacturerData(
                new byte[]{0x07, 0x19, 0x01}));
    }

    @Test
    public void extractsAppleManufacturerSectionFromScanRecord() {
        byte[] record = new byte[]{
                0x02, 0x01, 0x06,
                0x0C, (byte) 0xFF, 0x4C, 0x00,
                0x07, 0x19, 0x01, 0x0E, 0x20, 0x2B,
                (byte) 0xAA, (byte) 0xB5, 0x31
        };
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseScanRecord(record);

        assertNotNull(result);
        assertEquals(0x0E20, result.modelCode);
        assertTrue(result.areBothInEar());
    }
}
