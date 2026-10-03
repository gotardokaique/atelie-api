package com.gestao.api.push;

public record PushReceipt(String id, String erro, String mensagem) {

    public boolean isOk() {
        return erro == null;
    }

    public boolean isDispositivoInvalido() {
        return PushTicket.DEVICE_NOT_REGISTERED.equals(erro);
    }
}
