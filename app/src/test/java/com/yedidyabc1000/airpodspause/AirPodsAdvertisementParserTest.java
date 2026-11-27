package com.yedidyabc1000.airpodspause;

import org.junit.Test;

import static org.junit.Assert.*;

public class AirPodsAdvertisementParserTest {
    private byte[] message(int modelCode, int status) {
        byte[] data = new byte[27];
        data[0] = 0x07;
        data[1] = 0x19;
        data[2] = 0x01;
        data[3] = (byte) ((modelCode >> 8) & 0xFF);
        data[4] = (byte) (modelCode & 0xFF);
        data[5] = (byte) status;
        return data;
    }

    @Test
    public void parsesAirPodsPro3WithBothPodsInEar() {
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(message(0x2720, 0x2B));

        assertNotNull(result);
        assertEquals("AirPods Pro 3", result.modelName);
        assertEquals(0x2B, result.statusByte);
        assertTrue(result.leftInEar);
        assertTrue(result.rightInEar);
        assertTrue(result.areBothInEar());
    }

    @Test
    public void parsesOnePodRemoved() {
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(message(0x2720, 0x22));

        assertNotNull(result);
        assertTrue(result.leftInEar);
        assertFalse(result.rightInEar);
        assertFalse(result.areBothInEar());
    }

    @Test
    public void marksAdvertisementsFromPodInsideCase() {
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(message(0x2720, 0x54));

        assertNotNull(result);
        assertTrue(result.broadcasterIsInCase);
    }

    @Test
    public void acceptsValidPublicMessageWithoutRequiringKnownModelCode() {
        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(message(0x5555, 0x2B));

        assertNotNull(result);
        assertEquals("AirPods Pro 3 (מצב תאימות)", result.modelName);
        assertTrue(result.areBothInEar());
    }

    @Test
    public void ignoresMalformedFrames() {
        assertNull(AirPodsAdvertisementParser.parseManufacturerData(
                new byte[]{0x07, 0x19, 0x01, 0x27, 0x20, 0x2B}));
        assertNull(AirPodsAdvertisementParser.parseManufacturerData(
                new byte[]{0x07, 0x19, 0x02, 0x27, 0x20, 0x2B}));
    }

    @Test
    public void extractsFullAppleManufacturerSectionFromScanRecord() {
        byte[] payload = message(0x2720, 0x2B);
        byte[] record = new byte[3 + 1 + 1 + 2 + payload.length];
        record[0] = 0x02;
        record[1] = 0x01;
        record[2] = 0x06;
        record[3] = 0x1E; // AD length: type + company ID + 27-byte message
        record[4] = (byte) 0xFF;
        record[5] = 0x4C;
        record[6] = 0x00;
        System.arraycopy(payload, 0, record, 7, payload.length);

        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseScanRecord(record);

        assertNotNull(result);
        assertEquals(0x2720, result.modelCode);
        assertTrue(result.areBothInEar());
    }

    @Test
    public void skipsNonProximityAppleMessageAndFindsProximityMessage() {
        byte[] first = message(0x2720, 0x2B);
        byte[] data = new byte[first.length + 4];
        data[0] = 0x06;
        data[1] = 0x02;
        data[2] = 0x11;
        data[3] = 0x22;
        System.arraycopy(first, 0, data, 4, first.length);

        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(data);

        assertNotNull(result);
        assertEquals(0x2720, result.modelCode);
        assertTrue(result.areBothInEar());
    }
}
