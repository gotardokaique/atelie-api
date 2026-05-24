package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.gestao.api.entities.Servico;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.enuns.StatusPagamento;

public record ServicoHistoricoDTO(
        Long id,
        String descricao,
        BigDecimal valor,
        StatusServico statusServico,
        StatusPagamento statusPagamento,
        boolean urgente,
        LocalDateTime dataCriacao,
        LocalDate dataEntregaPrevista,
        LocalDate dataConclusao,
        long diasAtraso
) {

    public static ServicoHistoricoDTO refactor(Servico s) {
        long diasAtraso = calcularDiasAtraso(s);
        return new ServicoHistoricoDTO(
                s.getId(),
                s.getDescricao(),
                s.getValor(),
                s.getStatusServico(),
                s.getStatusPagamento(),
                s.isUrgente(),
                s.getDataCadastro(),
                s.getDataEntregaPrevista(),
                s.getDataFinalizacao(),
                diasAtraso
        );
    }

    public static List<ServicoHistoricoDTO> refactor(List<Servico> servicos) {
        return servicos.stream()
                .filter(Objects::nonNull)
                .map(ServicoHistoricoDTO::refactor)
                .collect(Collectors.toList());
    }

    /**
     * Dias de atraso:
     * - Negativo  → ainda dentro do prazo (diasAtraso = dias restantes * -1)
     * - Zero      → entrega hoje
     * - Positivo  → atrasado (dias passados desde o prazo)
     * - 0 se não houver prazo definido
     */
    private static long calcularDiasAtraso(Servico s) {
        if (s.getDataEntregaPrevista() == null) return 0L;

        LocalDate referencia = (s.getDataFinalizacao() != null)
                ? s.getDataFinalizacao()
                : LocalDate.now();

        return ChronoUnit.DAYS.between(s.getDataEntregaPrevista(), referencia);
    }
}
