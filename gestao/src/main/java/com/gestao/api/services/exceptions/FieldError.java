package com.gestao.api.services.exceptions;

public record FieldError(
    String fieldName,
    String message
) {}