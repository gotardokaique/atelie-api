package com.gestao.api.config.lia.tools;

import java.util.Arrays;
import java.util.Optional;

/**
 * Fonte única de verdade das tools da Lia.
 * Substitui as ~20 constantes String soltas do código antigo.
 * isWrite() classifica leitura vs escrita sem espalhar if/else pelo código.
 */
public enum LiaTool {

    BUSCAR_CLIENTES("buscar_clientes", false),
    BUSCAR_ORDENS_SERVICO("buscar_ordens_servico", false),
    CRIAR_ORDEM_SERVICO("criar_ordem_servico", true),
    EDITAR_ORDEM_SERVICO("editar_ordem_servico", true),
    CADASTRAR_CLIENTE("cadastrar_cliente", true);

    private final String wireName;
    private final boolean write;

    LiaTool(String wireName, boolean write) {
        this.wireName = wireName;
        this.write = write;
    }

    public String wireName() {
        return wireName;
    }

    public boolean isWrite() {
        return write;
    }

    public static Optional<LiaTool> fromWireName(String name) {
        return Arrays.stream(values())
                .filter(t -> t.wireName.equals(name))
                .findFirst();
    }
}
