package com.smart.price.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Vendedor;
import com.smart.price.repository.VendedorRepository;

/**
 * Cache de 24h da reputação de vendedores do Mercado Livre e a regra de corte por nível mínimo.
 */
@Service
public class VendedorService {

    private static final Logger logger = LoggerFactory.getLogger(VendedorService.class);

    static final int HORAS_CACHE = 24;

    private final VendedorRepository vendedorRepository;

    /** Nível mínimo aceito, no formato do ML ("4_light_green"). Só o número na frente importa. */
    @Value("${monitoramento.vendedor.nivel-minimo:4_light_green}")
    private String nivelMinimo = "4_light_green";

    public VendedorService(VendedorRepository vendedorRepository) {
        this.vendedorRepository = vendedorRepository;
    }

    /** Vendedor consultado nas últimas 24h, se houver. Falha no banco é tratada como cache vazio. */
    public Optional<Vendedor> buscarEmCache(String vendedorId) {
        if (vendedorId == null || vendedorId.isBlank()) {
            return Optional.empty();
        }
        try {
            LocalDateTime limite = LocalDateTime.now().minusHours(HORAS_CACHE);
            return vendedorRepository.findById(vendedorId)
                    .filter(v -> v.getAtualizadoEm() != null && v.getAtualizadoEm().isAfter(limite));
        } catch (Exception e) {
            logger.debug("VendedorService: Falha ao ler cache do vendedor {}: {}", vendedorId, e.getMessage());
            return Optional.empty();
        }
    }

    public Vendedor salvar(String vendedorId, String nivelReputacao, String powerSeller) {
        Vendedor vendedor = new Vendedor(vendedorId, nivelReputacao, powerSeller, LocalDateTime.now());
        try {
            return vendedorRepository.save(vendedor);
        } catch (Exception e) {
            logger.debug("VendedorService: Falha ao gravar cache do vendedor {}: {}", vendedorId, e.getMessage());
            return vendedor;
        }
    }

    /** Vendedor sem nível (conta nova) ou abaixo do mínimo configurado é descartado. */
    public boolean atendeNivelMinimo(Vendedor vendedor) {
        return vendedor != null && nivel(vendedor.getNivelReputacao()) >= nivel(nivelMinimo);
    }

    /** "5_green" → 5, "4_light_green" → 4. Nulo ou formato desconhecido → 0. */
    public static int nivel(String levelId) {
        if (levelId == null || levelId.isBlank() || !Character.isDigit(levelId.charAt(0))) {
            return 0;
        }
        return Character.getNumericValue(levelId.charAt(0));
    }
}
