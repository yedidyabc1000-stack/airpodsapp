package com.yedidyabc1000.airpodspause;

import java.util.Arrays;

/**
 * Reads Apple's public BLE proximity advertisement.
 *
 * Manufacturer data is carried under Apple company identifier 0x004C.
 * The 0x07/0x19/0x01 header is followed by the model code and public status byte.
 */
public final class AirPodsAdvertisementParser {
    private static final int APPLE_COMPANY_ID = 0x004C;
    private static final int PROXIMITY_TYPE = 0x07;
    private static final int PROXIMITY_LENGTH = 0x19;
    private static final int PUBLIC_PREFIX = 0x01;

    private AirPodsAdvertisementParser() {
    }

    public static Result parseScanRecord(byte[] scanRecord) {
        byte[] manufacturerData = findAppleManufacturerData(scanRecord);
        if (manufacturerData == null) {
            return null;
        }
        return parseManufacturerData(manufacturerData);
    }

    public static Result parseManufacturerData(byte[] data) {
        if (data == null || data.length < 9) {
            return null;
        }
        if ((data[0] & 0xFF) != PROXIMITY_TYPE
                || (data[1] & 0xFF) != PROXIMITY_LENGTH
                || (data[2] & 0xFF) != PUBLIC_PREFIX) {
            return null;
        }

        int modelCode = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
        String modelName = getModelName(modelCode);
        if (modelName == null) {
            return null;
        }

        int status = data[5] & 0xFF;
        boolean broadcasterIsInCase = (status & (1 << 6)) != 0;
        boolean primaryIsLeft = (status & (1 << 5)) != 0;
        boolean valuesFlipped = !primaryIsLeft;
        boolean inverted = valuesFlipped ^ broadcasterIsInCase;

        int leftBit = inverted ? 3 : 1;
        int rightBit = inverted ? 1 : 3;
        boolean leftInEar = (status & (1 << leftBit)) != 0;
        boolean rightInEar = (status & (1 << rightBit)) != 0;

        return new Result(modelCode, modelName, status, broadcasterIsInCase,
                leftInEar, rightInEar);
    }

    private static byte[] findAppleManufacturerData(byte[] record) {
        if (record == null) {
            return null;
        }

        int position = 0;
        while (position < record.length) {
            int fieldLength = record[position] & 0xFF;
            if (fieldLength == 0) {
                break;
            }

            int fieldEnd = position + 1 + fieldLength;
            if (fieldEnd > record.length || fieldLength < 1) {
                break;
            }

            int fieldType = record[position + 1] & 0xFF;
            if (fieldType == 0xFF && fieldLength >= 4) {
                int companyId = (record[position + 2] & 0xFF)
                        | ((record[position + 3] & 0xFF) << 8);
                if (companyId == APPLE_COMPANY_ID) {
                    return Arrays.copyOfRange(record, position + 4, fieldEnd);
                }
            }
            position = fieldEnd;
        }
        return null;
    }

    private static String getModelName(int code) {
        switch (code) {
            case 0x0220: return "AirPods דור 1";
            case 0x0F20: return "AirPods דור 2";
            case 0x1320: return "AirPods דור 3";
            case 0x1920: return "AirPods דור 4";
            case 0x1B20: return "AirPods דור 4 ANC";
            case 0x3620: return "AirPods דור 5";
            case 0x0E20: return "AirPods Pro דור 1";
            case 0x1420: return "AirPods Pro דור 2";
            case 0x2420: return "AirPods Pro 2 USB-C";
            case 0x2720: return "AirPods Pro דור 3";
            default: return null;
        }
    }

    public static final class Result {
        public final int modelCode;
        public final String modelName;
        public final int statusByte;
        public final boolean broadcasterIsInCase;
        public final boolean leftInEar;
        public final boolean rightInEar;

        Result(int modelCode, String modelName, int statusByte,
               boolean broadcasterIsInCase, boolean leftInEar, boolean rightInEar) {
            this.modelCode = modelCode;
            this.modelName = modelName;
            this.statusByte = statusByte;
            this.broadcasterIsInCase = broadcasterIsInCase;
            this.leftInEar = leftInEar;
            this.rightInEar = rightInEar;
        }

        public boolean areBothInEar() {
            return leftInEar && rightInEar;
        }
    }
}
