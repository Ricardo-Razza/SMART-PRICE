package com.smart.price.service.provedor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.smart.price.enums.LojaEnum;

/**
 * Hub central que gerencia todos os provedores de e-commerce disponíveis no sistema.
 * Permite que a Curadoria descubra ofertas de múltiplas lojas de forma transparente.
 */
@Component
public class ProvedorLojaHub {

    private final Map<LojaEnum, ProvedorLojaService> provedoresPorLoja;

    public ProvedorLojaHub(List<ProvedorLojaService> provedores) {
        this.provedoresPorLoja = provedores.stream()
                .collect(Collectors.toMap(
                        ProvedorLojaService::getLoja,
                        p -> p,
                        (existente, substituto) -> substituto
                ));
    }

    public List<ProvedorLojaService> getProvedoresAtivos() {
        return provedoresPorLoja.values().stream()
                .filter(ProvedorLojaService::isHabilitado)
                .collect(Collectors.toList());
    }

    public Optional<ProvedorLojaService> getProvedor(LojaEnum loja) {
        return Optional.ofNullable(provedoresPorLoja.get(loja));
    }

    public String formatarLinkAfiliado(LojaEnum loja, String urlOriginal) {
        return getProvedor(loja)
                .map(p -> p.formatarLinkAfiliado(urlOriginal))
                .orElse(urlOriginal);
    }
}
