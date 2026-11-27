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

        // First try the normal length-prefixed Continuity message layout.
        int offset = 0;
        while (offset + 2 <= data.length) {
            int type = data[offset] & 0xFF;
            int length = data[offset + 1] & 0xFF;
            int payloadStart = offset + 2;
            int messageEnd = payloadStart + length;

            if (messageEnd > data.length) {
                break;
            }

            if (type == PROXIMITY_TYPE
                    && length >= 9
                    && (data[payloadStart] & 0xFF) == PUBLIC_PREFIX) {
                return parsePublicProximityMessage(data, payloadStart, length);
            }

            if (messageEnd <= offset) {
                break;
            }
            // Other Continuity messages may be shorter than the proximity
            // message; skip them by their advertised length.
            offset = messageEnd;
        }

        // Some older Bluetooth stacks expose manufacturer bytes with an
        // unexpected prefix or a shifted boundary. As a compatibility fallback,
        // look for the complete 07 19 01 proximity signature inside the block.
        for (int i = 0; i + 11 <= data.length; i++) {
            if ((data[i] & 0xFF) == PROXIMITY_TYPE
                    && (data[i + 1] & 0xFF) == PROXIMITY_LENGTH
                    && (data[i + 2] & 0xFF) == PUBLIC_PREFIX
                    && i + 2 + PROXIMITY_LENGTH <= data.length) {
                return parsePublicProximityMessage(data, i + 2, PROXIMITY_LENGTH);
            }
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
    public static String getAirPodsProximityMessageHex(byte[] scanRecord) {
        String podsMessage = getAirPodsPodsMessageHex(scanRecord);
        if (podsMessage.length() > 0) {
            return podsMessage;
        }
        return getAirPodsCaseMessageHex(scanRecord);
    }

    /**
     * Returns the complete 07 19 advertisement emitted by an AirPod.
     * This format contains the cleartext wear-state fields followed by
     * the encrypted 16-byte tail.
     */
    public static String getAirPodsPodsMessageHex(byte[] scanRecord) {
        return getSpecificProximityMessageHex(scanRecord, 0x19, 0x01);
    }

    /**
     * Returns the complete 07 11 06 advertisement emitted by the case.
     * The remaining 16 bytes are encrypted and do not expose wear state.
     */
    public static String getAirPodsCaseMessageHex(byte[] scanRecord) {
        return getSpecificProximityMessageHex(scanRecord, 0x11, 0x06);
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
            int end = offset + 2 + length;
            if (end > data.length) {
                break;
            }
            if (type == PROXIMITY_TYPE
                    && length == expectedLength
                    && (data[offset + 2] & 0xFF) == expectedPayloadPrefix) {
                return toHex(data, offset, end);
            }
            if (end <= offset) {
                break;
            }
            offset = end;
        }

        // Compatibility with Android stacks that prepend or shift bytes.
        for (int i = 0; i + 3 <= data.length; i++) {
            if ((data[i] & 0xFF) == PROXIMITY_TYPE
                    && (data[i + 1] & 0xFF) == expectedLength
                    && (data[i + 2] & 0xFF) == expectedPayloadPrefix
                    && i + 2 + expectedLength <= data.length) {
                return toHex(data, i, i + 2 + expectedLength);
            }
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

        // This build is configured for the user's AirPods Pro 3. Do not
        // reject otherwise valid Apple proximity messages just because a
        // model identifier is new, absent from our table, or clone-specific.
        String modelName = getModelName(modelCode);
        if (modelName == null) {
            modelName = "AirPods Pro 3 (מצב תאימות)";
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
