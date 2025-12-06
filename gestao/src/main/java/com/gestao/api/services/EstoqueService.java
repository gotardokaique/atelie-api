package com.gestao.api.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.DAOController;
import com.gen.core.security.exception.NotFoundException;
import com.gestao.api.controllers.DTOs.EstoqueDTO;
import com.gestao.api.entities.Estoque;


@Service
public class EstoqueService {

    @Autowired
    private DAOController dao;

    
    private Estoque estoque;

    public void adicionarItemEstoque(EstoqueDTO estoqueDTO) {
    	estoque = new Estoque();
    	
    	estoque.setNomeItem(estoqueDTO.nomeItem());
    	estoque.setQuantidadeComprada(estoqueDTO.quantidadeComprada());
    	estoque.setValorGasto(estoqueDTO.valorGasto());
    	
    	salvar(estoque);
    	  
    }

    @Transactional(readOnly = true)
    public List<EstoqueDTO> listarTodoEstoque() {
    	List<Estoque> estList;
    	
    	try {
    		
    		estList = dao.select()
    				.from(Estoque.class)
    				.list();
    		
    		
    	} catch (NotFoundException not) {
    		estList = new ArrayList<Estoque>();
    	}
    	
        return EstoqueDTO.convert(estList);
    }

    @Transactional(readOnly = true)
    public EstoqueDTO buscarItemEstoquePorId(Long id) {
        try {
            Estoque estoque = dao.select()
                    .from(Estoque.class)
                    .id(id);

            return EstoqueDTO.convert(estoque);
        } catch (NotFoundException e) {
            return null; 
        }
    }

    @Transactional
    public boolean atualizarItemEstoque(Long id, EstoqueDTO estoqueDTO) {
    	
    	try {
    		estoque = dao.select()
    				.from(Estoque.class)
    				.id(id);    
    		
    	} catch (NotFoundException not) {
    		return false;
    	}
        		 
		estoque.setValorGasto(estoqueDTO.valorGasto());
		estoque.setQuantidadeComprada(estoqueDTO.quantidadeComprada());

		salvar(estoque);
		return true;
	}

     @Transactional
    public void deletarItemEstoque(Long id) {
    	 try {
    		 estoque = dao.select()
    				 .from(Estoque.class)
    				 .id(id);
    		 
    		 
    		 dao.delete(estoque);
    		 
    	 } catch (NotFoundException not) {
    		 
    	 }
    }
     
    private void salvar (Estoque estoque) {
    	if (estoque.getId() != null) {
    		dao.update(estoque);
    	} else {
    		dao.insert(estoque);
    	}
    } 
     
}