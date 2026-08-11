/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.nexo.model.HostStatus;
import com.bilt.pos.nexo.model.POIStatus;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of {@link Terminal#diagnose()}.
 */
public final class DiagnosisResult {

    private final POIStatus poiStatus;
    private final List<HostStatus> hostStatuses;

    public DiagnosisResult(POIStatus poiStatus, List<HostStatus> hostStatuses) {
        this.poiStatus = poiStatus;
        this.hostStatuses = hostStatuses == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(hostStatuses);
    }

    /** Terminal device status (card reader, printer, security), or {@code null}. */
    public POIStatus getPoiStatus() {
        return poiStatus;
    }

    /** Reachability of the acquirer/loyalty hosts; empty if not reported. */
    public List<HostStatus> getHostStatuses() {
        return hostStatuses;
    }
}
