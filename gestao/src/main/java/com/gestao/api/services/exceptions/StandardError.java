package com.gestao.api.services.exceptions;

import java.time.Instant;

public record StandardError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path
) {}