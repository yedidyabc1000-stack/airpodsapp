package com.yedidyabc1000.airpodspause;

import java.util.Arrays;

/**
 * Reads Apple's public BLE proximity advertisement.
 *
 * Apple manufacturer data (company id 0x004C) contains one or more
 * Continuity messages. The AirPods public proximity message starts with
 * 07 19 01, followed by model code and the public status byte.
 *
 * Ear-state decoding follows the public status-bit layout used by the
 * DualApplePods implementation in CAPod:
 * bit 5 = primary pod is left, bit 6 = this advertiser is in the case,
 * and bits 1/3 = the two pods' in-ear states (orientation-dependent).
 */
public final class AirPodsAdvertisementParser {
    private static final int APPLE_COMPANY_ID = 0x004C;
    private static final int APPLE_FIND_MY_SERVICE_UUID = 0xFD44;
    private static final int PROXIMITY_TYPE = 0x07;
    private static final int PROXIMITY_LENGTH = 0x19;
    private static final int PUBLIC_PREFIX = 0x01;

    private AirPodsAdvertisementParser() {
    }

    /** Returns whether the scan record includes Apple's manufacturer-data section. */
    public static boolean containsAppleManufacturerData(byte[] scanRecord) {
        return findAppleManufacturerData(scanRecord) != null;
    }

    public static Result parseScanRecord(byte[] scanRecord) {
        byte[] manufacturerData = findAppleManufacturerData(scanRecord);
        if (manufacturerData == null) {
            return null;
        }
        return parseManufacturerData(manufacturerData);
    }

    /**
     * Parses a manufacturer-data block. Some Apple blocks may contain more
     * than one Continuity message, so walk the length-prefixed messages
     * instead of assuming the first message is the only one.
     */
    public static Result parseManufacturerData(byte[] data) {
        if (data == null) {
            return null;
        }

        // Decode only length-framed Continuity messages. Do not search arbitrary
        // byte offsets: a random 07 19 01 sequence inside another Apple message
        // is not proof that the frame is an AirPods advertisement.
        int offset = 0;
        while (offset + 2 <= data.length) {
            int type = data[offset] & 0xFF;
            int length = data[offset + 1] & 0xFF;
            int payloadStart = offset + 2;
            int messageEnd = payloadStart + length;

            if (length == 0 || messageEnd > data.length) {
                break;
            }

            if (type == PROXIMITY_TYPE
                    && length == PROXIMITY_LENGTH
                    && (data[payloadStart] & 0xFF) == PUBLIC_PREFIX) {
                return parsePublicProximityMessage(data, payloadStart, length);
            }

            offset = messageEnd;
        }
        return null;
    }

    /**
     * Returns the first Apple Continuity Proximity Pairing (type 0x07) message.
     * Nearby Info (0x10), Handoff (0x0C), AirDrop (0x05) and other Apple
     * messages are intentionally excluded.
     */
    /**
     * Returns only a complete, signature-validated AirPods proximity frame.
     * A random 0x07 byte inside manufacturer data is not sufficient evidence:
     * the Continuity length and the format-specific public prefix must match.
     */
    /**
     * Returns a complete, length-framed public AirPods proximity advertisement.
     * CAPod and OpenPods both validate Continuity type 0x07, payload length 0x19,
     * and public payload prefix 0x01 before interpreting wear-state bits.
     */
    public static String getAirPodsProximityMessageHex(byte[] scanRecord) {
        return getAirPodsPodsMessageHex(scanRecord);
    }

    public static String getAirPodsPodsMessageHex(byte[] scanRecord) {
        return getSpecificProximityMessageHex(scanRecord, PROXIMITY_LENGTH, PUBLIC_PREFIX);
    }

    /**
     * Returns the payload of Apple's 16-bit FD44 Service Data field, if present.
     * This is a separate BLE service format and is not an AirPods ear-state message.
     */
    public static String getAppleFindMyServiceDataHex(byte[] scanRecord) {
        if (scanRecord == null) {
            return "";
        }
        int position = 0;
        while (position < scanRecord.length) {
            int fieldLength = scanRecord[position] & 0xFF;
            if (fieldLength == 0) {
                break;
            }
            int fieldEnd = position + 1 + fieldLength;
            if (fieldEnd > scanRecord.length || fieldLength < 1) {
                break;
            }
            int fieldType = scanRecord[position + 1] & 0xFF;
            if (fieldType == 0x16 && fieldLength >= 3) {
                int serviceUuid = (scanRecord[position + 2] & 0xFF)
                        | ((scanRecord[position + 3] & 0xFF) << 8);
                if (serviceUuid == APPLE_FIND_MY_SERVICE_UUID) {
                    return toHex(scanRecord, position + 2, fieldEnd);
                }
            }
            position = fieldEnd;
        }
        return "";
    }

    /**
     * 07 11 06 is not the public AirPods Proximity format used to decode wear
     * state. Keep this compatibility method returning empty so older UI code
     * cannot mistake an unverified Apple Continuity frame for the charging case.
     */
    public static String getAirPodsCaseMessageHex(byte[] scanRecord) {
        return "";
    }

    private static String getSpecificProximityMessageHex(byte[] scanRecord,
                                                          int expectedLength,
                                                          int expectedPayloadPrefix) {
        byte[] data = findAppleManufacturerData(scanRecord);
        if (data == null) {
            return "";
        }

        int offset = 0;
        while (offset + 2 <= data.length) {
            int type = data[offset] & 0xFF;
            int length = data[offset + 1] & 0xFF;
            int payloadStart = offset + 2;
            int end = payloadStart + length;

            if (length == 0 || end > data.length) {
                break;
            }
            if (type == PROXIMITY_TYPE
                    && length == expectedLength
                    && (data[payloadStart] & 0xFF) == expectedPayloadPrefix) {
                return toHex(data, offset, end);
            }
            offset = end;
        }
        return "";
    }

    private static String toHex(byte[] data, int start, int end) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (out.length() > 0) out.append(' ');
            out.append(String.format("%02X", data[i] & 0xFF));
        }
        return out.toString();
    }

    /** Returns a short hex sample of Apple's manufacturer section for diagnostics. */
    public static String getAppleManufacturerDataHex(byte[] scanRecord) {
        byte[] data = findAppleManufacturerData(scanRecord);
        if (data == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        // Preserve the complete Apple manufacturer block so a later
        // Continuity message is not lost in the diagnostics view.
        int count = Math.min(data.length, 31);
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append(' ');
            out.append(String.format("%02X", data[i] & 0xFF));
        }
        if (data.length > count) out.append(" ...");
        return out.toString();
    }

    private static Result parsePublicProximityMessage(byte[] data,
                                                        int payloadStart,
                                                        int payloadLength) {
        // Public payload layout: prefix, model(2), status, battery(3), lid,
        // color, suffix. At least through suffix is required for this parser.
        if (payloadLength < 9) {
            return null;
        }

        int modelCode = ((data[payloadStart + 1] & 0xFF) << 8)
                | (data[payloadStart + 2] & 0xFF);

        // This app is configured for AirPods Pro 3 only. Never interpret an
        // unknown or different Apple model as the user's target headphones.
        if (modelCode != 0x2720) {
            return null;
        }
        String modelName = getModelName(modelCode);
        if (modelName == null) {
            return null;
        }

        int status = data[payloadStart + 3] & 0xFF;
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
            case 0x2720: return "AirPods Pro 3";
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
