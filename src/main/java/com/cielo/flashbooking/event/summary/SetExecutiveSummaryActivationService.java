package com.cielo.flashbooking.event.summary;

import org.springframework.stereotype.Service;

@Service
public class SetExecutiveSummaryActivationService {

    private final ExecutiveSummaryControlStore controlStore;

    public SetExecutiveSummaryActivationService(ExecutiveSummaryControlStore controlStore) {
        this.controlStore = controlStore;
    }

    public ExecutiveSummaryActivation setEnabled(boolean enabled) {
        return controlStore.setEnabled(enabled);
    }
}
