package com.gestao.api.services; // Ou seu pacote de serviços/interfaces

public interface NotificationService {
    /**
     * Envia uma mensagem para o cliente informando que o serviço foi finalizado.
     *
     * @param numeroTelefone O número de telefone do cliente (já formatado se necessário).
     * @param nomeCliente O nome do cliente.
     * @param servicoId O ID do serviço que foi finalizado.
     */
    void enviarMensagemServicoFinalizado(String numeroTelefone, String nomeCliente, Long servicoId);

    // Você pode adicionar outros métodos de notificação aqui no futuro, se necessário
    // Ex: void enviarMensagemLembrete(String numeroTelefone, String nomeCliente, Long servicoId, LocalDate dataEntrega);
}
