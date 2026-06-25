package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.exception.NotFoundException;
import com.gen.core.security.exception.BusinessException;
import com.gestao.api.bo.EstoqueBO;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.FechamentoResumoDTO;
import com.gestao.api.controllers.DTOs.FinalizarServicoRequestDTO;
import com.gestao.api.controllers.DTOs.MaterialConsumidoDTO;
import com.gestao.api.controllers.DTOs.MovimentacaoDTO;
import com.gestao.api.controllers.DTOs.ServicoProdutoDTO;
import com.gestao.api.controllers.DTOs.ServicoProdutoRequestDTO;
import com.gestao.api.entities.FichaTecnica;
import com.gestao.api.entities.Insumo;
import com.gestao.api.entities.MovimentacaoEstoque;
import com.gestao.api.entities.Produto;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.ServicoProduto;
import com.gestao.api.enuns.StatusServico;

@Service
public class ServicoProdutoService {

    private final DAOController dao;
    private final ProdutoService produtoService;
    private final InsumoService insumoService;
    private final MovimentacaoService movimentacaoService;
    private final EstoqueBO estoqueBO;
    private final Clock clock;

    public ServicoProdutoService(DAOController dao, ProdutoService produtoService, InsumoService insumoService,
            MovimentacaoService movimentacaoService, EstoqueBO estoqueBO, Clock clock) {
        this.dao = dao;
        this.produtoService = produtoService;
        this.insumoService = insumoService;
        this.movimentacaoService = movimentacaoService;
        this.estoqueBO = estoqueBO;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------
    // Produtos vendidos no serviço
    // ---------------------------------------------------------------------

    @Transactional
    public ServicoProdutoDTO vincularProduto(Long servicoId, ServicoProdutoRequestDTO req) {
        Servico servico = buscarServicoEntity(servicoId);
        exigirNaoFinalizado(servico);

        Produto produto = produtoService.buscarEntity(req.produtoId());

        ServicoProduto sp = new ServicoProduto();
        sp.setServico(servico);
        sp.setProduto(produto);
        sp.setQuantidade(req.quantidade());
        sp.setPrecoVendaSnapshot(produto.getPrecoVenda());

        return ServicoProdutoDTO.convert(dao.insert(sp));
    }

    @Transactional(readOnly = true)
    public List<ServicoProdutoDTO> listarProdutos(Long servicoId) {
        buscarServicoEntity(servicoId); // valida posse
        return ServicoProdutoDTO.convert(listarServicoProdutoEntity(servicoId));
    }

    @Transactional
    public void removerProduto(Long servicoId, Long servicoProdutoId) {
        Servico servico = buscarServicoEntity(servicoId);
        exigirNaoFinalizado(servico);

        ServicoProduto sp;
        try {
            sp = dao.select()
                    .from(ServicoProduto.class)
                    .join("servico")
                    .where("servico.id", Condicao.EQUAL, servicoId)
                    .where("servico.usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(servicoProdutoId);
        } catch (Exception e) {
            throw new NotFoundException("Produto do serviço não encontrado: " + servicoProdutoId);
        }
        dao.delete(sp);
    }

    // ---------------------------------------------------------------------
    // Materiais: sugestão (explode ficha) + fechamento (gera SAIDAs)
    // ---------------------------------------------------------------------

    /**
     * Explode a ficha técnica dos produtos vinculados em materiais sugeridos:
     * para cada produto, qtdSugerida = ficha.quantidade × servicoProduto.quantidade.
     * Mesmo insumo em produtos diferentes é somado.
     */
    @Transactional(readOnly = true)
    public List<MaterialConsumidoDTO> materiaisSugeridos(Long servicoId) {
        buscarServicoEntity(servicoId);

        Map<Long, BigDecimal> qtdPorInsumo = new LinkedHashMap<>();
        Map<Long, String> descPorInsumo = new LinkedHashMap<>();

        for (ServicoProduto sp : listarServicoProdutoEntity(servicoId)) {
            BigDecimal qtdProduto = sp.getQuantidade() != null ? sp.getQuantidade() : BigDecimal.ONE;

            for (FichaTecnica ficha : produtoService.listarFichaEntity(sp.getProduto().getId())) {
                Insumo insumo = ficha.getInsumo();
                if (insumo == null) {
                    continue;
                }
                BigDecimal sugerido = ficha.getQuantidade().multiply(qtdProduto);

                qtdPorInsumo.merge(insumo.getId(), sugerido, BigDecimal::add);
                descPorInsumo.putIfAbsent(insumo.getId(), insumo.getDescricao());
            }
        }

        List<MaterialConsumidoDTO> sugeridos = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : qtdPorInsumo.entrySet()) {
            sugeridos.add(new MaterialConsumidoDTO(e.getKey(), descPorInsumo.get(e.getKey()), e.getValue()));
        }
        return sugeridos;
    }

    /**
     * Finaliza o serviço: cada material consumido vira uma SAIDA vinculada ao serviço
     * (baixando o saldo do insumo no mesmo @Transactional), depois marca o serviço como
     * FINALIZADO. Se qualquer insumo estiver insuficiente, nada é aplicado (rollback).
     */
    @Transactional
    public FechamentoResumoDTO finalizar(Long servicoId, FinalizarServicoRequestDTO req) {
        Servico servico = buscarServicoEntity(servicoId);
        exigirNaoFinalizado(servico);

        List<MaterialConsumidoDTO> materiais = req != null && req.materiais() != null
                ? req.materiais()
                : List.of();

        for (MaterialConsumidoDTO material : materiais) {
            if (material == null || material.insumoId() == null) {
                continue;
            }
            Insumo insumo = insumoService.buscarEntity(material.insumoId());
            estoqueBO.saida(insumo, material.quantidade(), servico,
                    "Consumo no serviço #" + servicoId);
        }

        servico.setStatusServico(StatusServico.FINALIZADO);
        servico.setDataFinalizacao(LocalDate.now(clock));
        dao.update(servico);

        return resumo(servicoId);
    }

    // ---------------------------------------------------------------------
    // Resumo financeiro: total (produtos + mão de obra) e margem
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FechamentoResumoDTO resumo(Long servicoId) {
        Servico servico = buscarServicoEntity(servicoId);

        BigDecimal totalProdutos = BigDecimal.ZERO;
        for (ServicoProduto sp : listarServicoProdutoEntity(servicoId)) {
            BigDecimal preco = sp.getPrecoVendaSnapshot() != null ? sp.getPrecoVendaSnapshot() : BigDecimal.ZERO;
            BigDecimal qtd = sp.getQuantidade() != null ? sp.getQuantidade() : BigDecimal.ZERO;
            totalProdutos = totalProdutos.add(preco.multiply(qtd));
        }

        BigDecimal valorMaoDeObra = servico.getValor() != null ? servico.getValor() : BigDecimal.ZERO;
        BigDecimal total = totalProdutos.add(valorMaoDeObra);

        List<MovimentacaoEstoque> gastos = movimentacaoService.materiaisGastosPorServico(servicoId);
        BigDecimal custoMateriais = BigDecimal.ZERO;
        for (MovimentacaoEstoque m : gastos) {
            BigDecimal custo = m.getCustoUnitario() != null ? m.getCustoUnitario() : BigDecimal.ZERO;
            BigDecimal qtd = m.getQuantidade() != null ? m.getQuantidade() : BigDecimal.ZERO;
            custoMateriais = custoMateriais.add(custo.multiply(qtd));
        }

        BigDecimal margem = total.subtract(custoMateriais);
        List<MovimentacaoDTO> materiaisGastos = MovimentacaoDTO.convert(gastos);

        return new FechamentoResumoDTO(servicoId, totalProdutos, valorMaoDeObra, total,
                custoMateriais, margem, materiaisGastos);
    }

    // ---------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------

    private Servico buscarServicoEntity(Long servicoId) {
        try {
            return dao.select()
                    .from(Servico.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(servicoId);
        } catch (Exception e) {
            throw new NotFoundException("Serviço não encontrado: " + servicoId);
        }
    }

    private List<ServicoProduto> listarServicoProdutoEntity(Long servicoId) {
        try {
            return dao.select()
                    .from(ServicoProduto.class)
                    .join("servico")
                    .where("servico.id", Condicao.EQUAL, servicoId)
                    .where("servico.usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .list();
        } catch (NotFoundException e) {
            return new ArrayList<>();
        }
    }

    private void exigirNaoFinalizado(Servico servico) {
        if (StatusServico.FINALIZADO.equals(servico.getStatusServico())) {
            throw new BusinessException(
                    "Serviço finalizado: produtos e materiais não podem mais ser alterados.");
        }
    }
}
