package com.smart.price.enums;

public enum LojaEnum {
    MERCADO_LIVRE("Mercado Livre", true),
    AMAZON("Amazon", false),
    SHOPEE("Shopee", false),
    KABUM("KaBuM!", false);

    private final String nomeExibicao;
    private final boolean habilitadaPadrao;

    LojaEnum(String nomeExibicao, boolean habilitadaPadrao) {
        this.nomeExibicao = nomeExibicao;
        this.habilitadaPadrao = habilitadaPadrao;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public boolean isHabilitadaPadrao() {
        return habilitadaPadrao;
    }
}
