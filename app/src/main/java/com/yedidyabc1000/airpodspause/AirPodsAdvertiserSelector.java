package com.yedidyabc1000.airpodspause;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Chooses a repeated, strongest AirPods advertiser from a short rolling BLE window.
 *
 * Android's BLE advertiser address may be randomized, so this is a proximity heuristic,
 * not cryptographic proof that the beacon belongs to the paired audio device.
 */
public final class AirPodsAdvertiserSelector {
    private static final long WINDOW_MILLIS = 10000L;
    private static final int MAX_ADVERTISERS = 20;

    private final Map<String, Observation> observations = new HashMap<String, Observation>();

    public synchronized boolean shouldProcess(String address, int rssi, long nowMillis) {
        if (address == null || address.length() == 0) {
            return false;
        }

        Iterator<Map.Entry<String, Observation>> iterator =
                observations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Observation> entry = iterator.next();
            if (nowMillis - entry.getValue().lastSeenAt > WINDOW_MILLIS) {
                iterator.remove();
            }
        }

        Observation current = observations.get(address);
        if (current == null) {
            current = new Observation(rssi, nowMillis);
            observations.put(address, current);
        } else {
            current.samples++;
            current.lastSeenAt = nowMillis;
            current.bestRssi = Math.max(current.bestRssi, rssi);
        }

        while (observations.size() > MAX_ADVERTISERS) {
            String oldestAddress = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<String, Observation> entry : observations.entrySet()) {
                if (entry.getValue().lastSeenAt < oldestAt) {
                    oldestAt = entry.getValue().lastSeenAt;
                    oldestAddress = entry.getKey();
                }
            }
            if (oldestAddress == null) {
                break;
            }
            observations.remove(oldestAddress);
        }

        String strongestAddress = null;
        int strongestRssi = Integer.MIN_VALUE;
        long strongestLastSeen = Long.MIN_VALUE;
        for (Map.Entry<String, Observation> entry : observations.entrySet()) {
            Observation observation = entry.getValue();
            if (observation.bestRssi > strongestRssi
                    || (observation.bestRssi == strongestRssi
                    && observation.lastSeenAt > strongestLastSeen)) {
                strongestRssi = observation.bestRssi;
                strongestLastSeen = observation.lastSeenAt;
                strongestAddress = entry.getKey();
            }
        }

        return address.equals(strongestAddress) && current.samples >= 2;
    }

    public synchronized void reset() {
        observations.clear();
    }

    private static final class Observation {
        int bestRssi;
        int samples = 1;
        long lastSeenAt;

        Observation(int rssi, long nowMillis) {
            bestRssi = rssi;
            lastSeenAt = nowMillis;
        }
    }
}
