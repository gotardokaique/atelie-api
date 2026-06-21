package com.gestao.api.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.FichaTecnicaItemDTO;
import com.gestao.api.controllers.DTOs.ProdutoDTO;
import com.gestao.api.entities.FichaTecnica;
import com.gestao.api.entities.Insumo;
import com.gestao.api.entities.Produto;
import com.gestao.api.entities.Usuario;
import com.gen.core.db.exception.NotFoundException;

@Service
public class ProdutoService {

    private final DAOController dao;
    private final InsumoService insumoService;

    public ProdutoService(DAOController dao, InsumoService insumoService) {
        this.dao = dao;
        this.insumoService = insumoService;
    }

    @Transactional
    public ProdutoDTO criar(ProdutoDTO dto) {
        Produto produto = new Produto();
        produto.setDescricao(dto.descricao());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setAtivo(dto.ativo() == null || dto.ativo());

        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        produto.setUsuario(usuarioRef);

        Produto salvo = dao.insert(produto);
        sincronizarFicha(salvo, dto.ficha());

        return ProdutoDTO.convert(salvo, FichaTecnicaItemDTO.convert(listarFichaEntity(salvo.getId())));
    }

    @Transactional(readOnly = true)
    public List<ProdutoDTO> listar() {
        List<Produto> produtos;
        try {
            produtos = dao.select()
                    .from(Produto.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .orderBy("descricao", true)
                    .list();
        } catch (NotFoundException e) {
            produtos = new ArrayList<>();
        }
        return ProdutoDTO.convert(produtos);
    }

    @Transactional(readOnly = true)
    public ProdutoDTO buscarPorId(Long id) {
        Produto produto = buscarEntity(id);
        List<FichaTecnicaItemDTO> ficha = FichaTecnicaItemDTO.convert(listarFichaEntity(id));
        return ProdutoDTO.convert(produto, ficha);
    }

    @Transactional(readOnly = true)
    public Produto buscarEntity(Long id) {
        try {
            return dao.select()
                    .from(Produto.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
        } catch (Exception e) {
            throw new NotFoundException("Produto não encontrado: " + id);
        }
    }

    @Transactional
    public ProdutoDTO atualizar(Long id, ProdutoDTO dto) {
        Produto produto = buscarEntity(id);
        produto.setDescricao(dto.descricao());
        produto.setPrecoVenda(dto.precoVenda());
        if (dto.ativo() != null) {
            produto.setAtivo(dto.ativo());
        }
        dao.update(produto);

        // ficha == null → não mexe na ficha existente; lista (mesmo vazia) → substitui.
        if (dto.ficha() != null) {
            removerFicha(id);
            sincronizarFicha(produto, dto.ficha());
        }

        return ProdutoDTO.convert(produto, FichaTecnicaItemDTO.convert(listarFichaEntity(id)));
    }

    @Transactional
    public void inativar(Long id) {
        Produto produto = buscarEntity(id);
        produto.setAtivo(false);
        dao.update(produto);
    }

    @Transactional(readOnly = true)
    public List<FichaTecnica> listarFichaEntity(Long produtoId) {
        try {
            return dao.select()
                    .from(FichaTecnica.class)
                    .join("produto")
                    .where("produto.id", Condicao.EQUAL, produtoId)
                    .where("produto.usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .list();
        } catch (NotFoundException e) {
            return new ArrayList<>();
        }
    }

    private void sincronizarFicha(Produto produto, List<FichaTecnicaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        for (FichaTecnicaItemDTO item : itens) {
            if (item == null || item.insumoId() == null) {
                continue;
            }
            Insumo insumo = insumoService.buscarEntity(item.insumoId());

            FichaTecnica ficha = new FichaTecnica();
            ficha.setProduto(produto);
            ficha.setInsumo(insumo);
            ficha.setQuantidade(item.quantidade() != null ? item.quantidade() : BigDecimal.ZERO);
            dao.insert(ficha);
        }
    }

    private void removerFicha(Long produtoId) {
        for (FichaTecnica f : listarFichaEntity(produtoId)) {
            dao.delete(f);
        }
    }
}
