package com.cielo.flashbooking.event.summary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"command-api", "all"})
@RequestMapping("/executive-summary/activation")
public class ExecutiveSummaryActivationController {

    private final SetExecutiveSummaryActivationService activationService;

    public ExecutiveSummaryActivationController(SetExecutiveSummaryActivationService activationService) {
        this.activationService = activationService;
    }

    @PutMapping
    ExecutiveSummaryActivation put(@Valid @RequestBody ActivationRequest request) {
        return activationService.setEnabled(request.enabled());
    }

    record ActivationRequest(@NotNull Boolean enabled) {}
}
