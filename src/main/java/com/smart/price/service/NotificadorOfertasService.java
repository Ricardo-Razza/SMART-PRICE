package com.smart.price.service;

import java.util.List;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

public interface NotificadorOfertasService {

    /**
     * Notifica a lista de ofertas selecionadas de um determinado nicho.
     *
     * @param nicho   Nicho ao qual as ofertas pertencem
     * @param ofertas Lista das melhores ofertas curadas
     */
    void notificar(Nicho nicho, List<OfertaDescoberta> ofertas);
}
