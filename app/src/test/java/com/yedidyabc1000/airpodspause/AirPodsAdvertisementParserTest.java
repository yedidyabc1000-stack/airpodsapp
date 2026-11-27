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

    private byte[] caseMessage() {
        byte[] data = new byte[19];
        data[0] = 0x07;
        data[1] = 0x11;
        data[2] = 0x06;
        for (int i = 3; i < data.length; i++) {
            data[i] = (byte) (0x30 + i);
        }
        return data;
    }


    @Test
    public void identifiesAppleFd44ServiceDataWithoutTreatingItAsAirPodsProximity() {
        byte[] record = new byte[]{0x04, 0x16, 0x44, (byte) 0xFD, 0x4C};

        assertEquals("44 FD 4C",
                AirPodsAdvertisementParser.getAppleFindMyServiceDataHex(record));
        assertFalse(AirPodsAdvertisementParser.containsAppleManufacturerData(record));
        assertNull(AirPodsAdvertisementParser.parseScanRecord(record));
    }

    @Test
    public void doesNotTreatMicrosoftManufacturerDataAsApple() {
        byte[] record = new byte[]{0x04, (byte) 0xFF, 0x06, 0x00, 0x01};

        assertFalse(AirPodsAdvertisementParser.containsAppleManufacturerData(record));
        assertEquals("", AirPodsAdvertisementParser.getAppleFindMyServiceDataHex(record));
        assertNull(AirPodsAdvertisementParser.parseScanRecord(record));
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
    public void rejectsUnknownAndNonTargetModelCodes() {
        assertNull(AirPodsAdvertisementParser.parseManufacturerData(message(0x5555, 0x2B)));
        assertNull(AirPodsAdvertisementParser.parseManufacturerData(message(0x1420, 0x2B)));
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
    public void rejectsProximitySignatureNotAtAContinuityMessageBoundary() {
        byte[] message = message(0x2720, 0x2B);
        byte[] data = new byte[message.length + 3];
        data[0] = 0x11;
        data[1] = 0x22;
        data[2] = 0x33;
        System.arraycopy(message, 0, data, 3, message.length);

        AirPodsAdvertisementParser.Result result =
                AirPodsAdvertisementParser.parseManufacturerData(data);

        assertNull(result);
    }

    private byte[] scanRecord(byte[] manufacturerData) {
        byte[] record = new byte[3 + 1 + 1 + 2 + manufacturerData.length];
        record[0] = 0x02;
        record[1] = 0x01;
        record[2] = 0x06;
        record[3] = (byte) (manufacturerData.length + 3);
        record[4] = (byte) 0xFF;
        record[5] = 0x4C;
        record[6] = 0x00;
        System.arraycopy(manufacturerData, 0, record, 7, manufacturerData.length);
        return record;
    }

    @Test
    public void acceptsOnlyVerifiedPublicAirPodsProximityAdvertisements() {
        byte[] podsRecord = scanRecord(message(0x2720, 0x2B));
        byte[] caseRecord = scanRecord(caseMessage());

        assertTrue(AirPodsAdvertisementParser.getAirPodsPodsMessageHex(podsRecord)
                .startsWith("07 19 01 27 20 2B"));
        assertEquals("", AirPodsAdvertisementParser.getAirPodsCaseMessageHex(podsRecord));

        assertEquals("", AirPodsAdvertisementParser.getAirPodsPodsMessageHex(caseRecord));
        assertEquals("", AirPodsAdvertisementParser.getAirPodsCaseMessageHex(caseRecord));
        assertEquals("", AirPodsAdvertisementParser.getAirPodsProximityMessageHex(caseRecord));
        assertNull(AirPodsAdvertisementParser.parseScanRecord(caseRecord));
    }

    @Test
    public void followsReferenceAirPodsPro3WearStateExamples() {
        AirPodsAdvertisementParser.Result bothInEar =
                AirPodsAdvertisementParser.parseManufacturerData(message(0x2720, 0x0B));
        assertNotNull(bothInEar);
        assertTrue(bothInEar.leftInEar);
        assertTrue(bothInEar.rightInEar);

        AirPodsAdvertisementParser.Result oneInCase =
                AirPodsAdvertisementParser.parseManufacturerData(message(0x2720, 0x53));
        assertNotNull(oneInCase);
        assertTrue(oneInCase.broadcasterIsInCase);
        assertTrue(oneInCase.leftInEar);
        assertFalse(oneInCase.rightInEar);
    }

    @Test
    public void doesNotDecodeTheUnverified071106FrameAsAirPods() {
        byte[] frame = caseMessage();
        assertNull(AirPodsAdvertisementParser.parseManufacturerData(frame));
    }

    @Test
    public void rejectsRandom07ByteWithImpossibleLengthAsProximity() {
        byte[] falsePositive = new byte[]{
                0x09, 0x08, 0x13, (byte) 0xF0, (byte) 0xAC, 0x14, 0x0A,
                0x04, 0x1B, 0x58, 0x16, 0x08, 0x00, (byte) 0x94,
                0x30, (byte) 0xC6, 0x64, 0x7C, 0x53, 0x66,
                0x07, 0x38, 0x1F, (byte) 0x9E, 0x1F, (byte) 0xB5,
                (byte) 0xF4, 0x1F, 0x6E
        };
        byte[] record = scanRecord(falsePositive);

        assertEquals("", AirPodsAdvertisementParser.getAirPodsProximityMessageHex(record));
        assertEquals("", AirPodsAdvertisementParser.getAirPodsPodsMessageHex(record));
        assertEquals("", AirPodsAdvertisementParser.getAirPodsCaseMessageHex(record));
        assertNull(AirPodsAdvertisementParser.parseScanRecord(record));
    }

    @Test
    public void genericAppleNearbyInfoIsNotCountedAsAirPodsProximity() {
        byte[] nearbyInfo = new byte[]{
                0x10, 0x06, 0x0B, 0x1D, (byte) 0xE5, 0x25, 0x01, 0x48
        };
        byte[] record = scanRecord(nearbyInfo);

        assertTrue(AirPodsAdvertisementParser.containsAppleManufacturerData(record));
        assertEquals("", AirPodsAdvertisementParser.getAirPodsProximityMessageHex(record));
        assertNull(AirPodsAdvertisementParser.parseScanRecord(record));
    }

    @Test
    public void identifiesAirPodsProximityMessageSeparately() {
        byte[] proximity = message(0x2720, 0x2B);
        byte[] record = scanRecord(proximity);

        String hex = AirPodsAdvertisementParser.getAirPodsProximityMessageHex(record);

        assertTrue(hex.startsWith("07 19 01 27 20 2B"));
        assertNotNull(AirPodsAdvertisementParser.parseScanRecord(record));
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
