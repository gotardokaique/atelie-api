package com.gestao.api.push;

public record PushTicket(String token, String id, String erro, String mensagem) {

    public static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    public static PushTicket ok(String token, String id) {
        return new PushTicket(token, id, null, null);
    }

    public static PushTicket falha(String token, String erro, String mensagem) {
        return new PushTicket(token, null, erro, mensagem);
    }

    public boolean isOk() {
        return erro == null && id != null;
    }

    public boolean isDispositivoInvalido() {
        return DEVICE_NOT_REGISTERED.equals(erro);
    }
}
