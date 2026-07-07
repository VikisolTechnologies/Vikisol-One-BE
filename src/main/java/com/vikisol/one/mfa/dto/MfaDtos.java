package com.vikisol.one.mfa.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class MfaDtos {

    private MfaDtos() {
    }

    public record SetupResponse(String qrCodeDataUri, String manualEntryKey) {}

    public record VerifyCodeRequest(@NotBlank String code) {}

    public record EnableResponse(List<String> backupCodes) {}

    public record DisableRequest(@NotBlank String password) {}

    public record StatusResponse(boolean enabled) {}
}
