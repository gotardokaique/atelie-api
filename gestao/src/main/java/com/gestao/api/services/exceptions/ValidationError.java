package com.gestao.api.services.exceptions;

import java.time.Instant;
import java.util.List;

public record ValidationError(
    Instant timestamp,
    int status, 
    String error,
    String message,
    String path,
    List<FieldError> errors
) {}