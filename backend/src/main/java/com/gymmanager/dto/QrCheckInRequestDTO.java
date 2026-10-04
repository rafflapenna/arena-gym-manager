package com.gymmanager.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /check-ins/qr}: the token scanned off the member's QR card. */
public record QrCheckInRequestDTO(@NotBlank String token) {}
