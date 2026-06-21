package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.WhereDB;
import com.gen.core.db.exception.NotFoundException;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.DespesaDTO;
import com.gestao.api.controllers.DTOs.DespesaTotalDTO;
import com.gestao.api.entities.Despesa;
import com.gestao.api.entities.Usuario;

@Service
public class DespesaService {

    @Autowired
    private DAOController dao;

    @Transactional
    public void adicionarDespesa(DespesaDTO dto) {
        Despesa despesa = new Despesa();
        despesa.setDescricao(dto.descricao());
        despesa.setValor(dto.valor());
        despesa.setMes(dto.mes());
        despesa.setAno(dto.ano());

        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        despesa.setUsuario(usuarioRef);

        dao.insert(despesa);
    }

    @Transactional(readOnly = true)
    public List<DespesaDTO> listarDespesas() {
        try {
            List<Despesa> list = dao.select()
                    .from(Despesa.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .orderBy("ano", false)
                    .orderBy("mes", false)
                    .list();
            return DespesaDTO.convert(list);
        } catch (NotFoundException e) {
            return new ArrayList<>();
        }
    }

    @Transactional(readOnly = true)
    public DespesaDTO buscarPorId(Long id) {
        try {
            Despesa despesa = dao.select()
                    .from(Despesa.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
            return DespesaDTO.convert(despesa);
        } catch (NotFoundException e) {
            return null;
        }
    }

    @Transactional
    public boolean atualizarDespesa(Long id, DespesaDTO dto) {
        Despesa despesa;
        try {
            despesa = dao.select()
                    .from(Despesa.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
        } catch (NotFoundException e) {
            return false;
        }

        despesa.setDescricao(dto.descricao());
        despesa.setValor(dto.valor());
        despesa.setMes(dto.mes());
        despesa.setAno(dto.ano());

        dao.update(despesa);
        return true;
    }

    @Transactional
    public void deletarDespesa(Long id) {
        try {
            Despesa despesa = dao.select()
                    .from(Despesa.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
            dao.delete(despesa);
        } catch (NotFoundException e) {
            // não encontrado, ignorar silenciosamente
        }
    }

    @Transactional
    public void deletarPorServico(Long servicoId) {
        if (servicoId == null) return;
        try {
            List<Despesa> despesas = dao.select()
                    .from(Despesa.class)
                    .join("servico")
                    .where("servico.id", Condicao.EQUAL, servicoId)
                    .list();
            for (Despesa d : despesas) {
                dao.delete(d);
            }
        } catch (NotFoundException e) {
            // ignorar
        }
    }

    @Transactional(readOnly = true)
    public DespesaTotalDTO calcularTotalMes(String mesAno) {
        int mes;
        int ano;

        if (mesAno != null && !mesAno.isBlank()) {
            String[] parts = mesAno.split("/");
            mes = Integer.parseInt(parts[0]);
            ano = Integer.parseInt(parts[1]);
        } else {
            LocalDate now = LocalDate.now();
            mes = now.getMonthValue();
            ano = now.getYear();
        }

        try {
            WhereDB where = new WhereDB();
            where.add("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario());
            where.add("mes", Condicao.EQUAL, mes);
            where.add("ano", Condicao.EQUAL, ano);

            List<Despesa> list = dao.select()
                    .from(Despesa.class)
                    .join("usuario")
                    .where(where)
                    .list();

            BigDecimal total = list.stream()
                    .map(Despesa::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new DespesaTotalDTO(total, list.size());
        } catch (NotFoundException e) {
            return new DespesaTotalDTO(BigDecimal.ZERO, 0);
        }
    }
}
