package com.gestao.api.security.redefinir.dto;

public record PasswordTokenPublicData(
        String email,
        Long createAtTimestamp) {

}
