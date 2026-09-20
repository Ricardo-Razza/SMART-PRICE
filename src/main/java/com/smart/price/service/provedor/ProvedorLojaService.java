package com.smart.price.service.provedor;

import java.util.List;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.enums.LojaEnum;

/**
 * Contrato padrão para provedores de lojas de e-commerce (Mercado Livre, Amazon, Shopee, KaBuM!).
 * Garante que a lógica de mineração e curadoria seja desacoplada dos detalhes técnicos de cada plataforma.
 */
public interface ProvedorLojaService {

    /**
     * Retorna a loja representada por este provedor.
     */
    LojaEnum getLoja();

    /**
     * Indica se este provedor está habilitado para mineração nas configurações.
     */
    boolean isHabilitado();

    /**
     * Realiza a busca no catálogo da loja a partir de um termo de pesquisa e nicho,
     * retornando uma lista de ofertas normalizadas.
     */
    List<OfertaDescoberta> buscarOfertasPorTermo(String termo, Nicho nicho);

    /**
     * Realiza a busca autônoma de ofertas em alta / destaques no catálogo da loja
     * para as categorias associadas ao nicho informado.
     */
    List<OfertaDescoberta> buscarOfertasEmAlta(Nicho nicho);

    /**
     * Aplica os parâmetros oficiais do programa de afiliados da loja na URL do produto.
     */
    String formatarLinkAfiliado(String urlOriginal);
}
