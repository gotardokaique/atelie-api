package com.gestao.api.BO;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.gen.core.bo.AbstractBO;
import com.gen.core.context.UserContext;
import com.gen.core.db.TransactionDB;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.entities.Servico;

@Component
public class ServicoBO extends AbstractBO<Servico> {

    private Servico servico;

    public ServicoBO(TransactionDB transactionDB, UserContext userContext) {
        super(transactionDB, userContext);
    }

    @Override
    public void instanciar() {
        servico = new Servico();
    }

    @Override
    public void buscarParaAlterar(Integer id) {
        servico = transactionDB.selectById(Servico.class, id);
    }
    
    public void buscarDados (ServicoRequestDTO dto) {
    	LocalDateTime date = transactionDB.getDataHoraAtual();
    	servico.setDataCadastro(date);
    }

    @Override
    public void preencher() {
    	servico.setDescricao("aaaaa");
    	
    	
    }

    @Override
    public void registrar() {
        transactionDB.insert(servico);
    }
}
