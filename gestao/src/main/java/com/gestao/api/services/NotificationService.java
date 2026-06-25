package com.gestao.api.services;

public interface NotificationService {
    void enviarMensagemServicoFinalizado(String numeroTelefone, String nomeCliente, Long servicoId);

}
