package com.smart.price.service;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Cupom;

/**
 * Serviço de validação automática de cupons via sonda HTTP.
 *
 * Quando o crawler captura um cupom, este serviço faz uma requisição
 * na página de ativação do Mercado Livre para verificar se o cupom
 * ainda está ativo antes de gravar no banco.
 *
 * Resultado:
 *  - Página OK e sem sinais de expiração  → testado = true  (entra no pareamento automático)
 *  - Página 404 / conteúdo de erro        → testado = false (quarentena; não é pareado com produtos)
 *  - Timeout / erro de rede               → testado = false (precaução; confirmar depois via endpoint)
 */
@Service
public class CupomValidadorService {

    private static final Logger logger = LoggerFactory.getLogger(CupomValidadorService.class);

    /**
     * URL base para sondar a validade de cupons do Mercado Livre.
     * O padrão é a página de cupons do ML; se o cupom tiver um linkHotsite, ele é usado no lugar.
     */
    private static final String URL_CUPOM_ML = "https://www.mercadolivre.com.br/cupons/%s";

    /**
     * Palavras que indicam que o cupom está inativo ou expirado na página do ML.
     */
    private static final List<String> SINAIS_INVALIDO = List.of(
            "cupom não encontrado",
            "cupom inválido",
            "cupom expirado",
            "promoção encerrada",
            "não está mais disponível",
            "prazo expirou",
            "oferta encerrada",
            "página não encontrada",
            "this coupon is not valid",
            "not found",
            "404",
            "error"
    );

    @Value("${cupons.validador.enabled:true}")
    private boolean enabled;

    @Value("${cupons.validador.timeout-ms:4000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;

    public CupomValidadorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Valida automaticamente um cupom via sonda HTTP na página do Mercado Livre.
     *
     * Retorna true se o cupom parece ativo; false se há sinais de expiração ou erro.
     * Em caso de indisponibilidade temporária da validação, retorna true por padrão
     * (benefício da dúvida — o Monitor de Status vai desativar se falhar no futuro).
     */
    public boolean validarCupomAutomaticamente(Cupom cupom) {
        if (!enabled) {
            logger.debug("CupomValidadorService: Validação automática desabilitada. Aprovando cupom [{}] por padrão.", cupom.getCodigo());
            return true;
        }

        String urlAlvo = resolverUrlAlvo(cupom);

        try {
            logger.debug("CupomValidadorService: Sondando validade do cupom [{}] em: {}", cupom.getCodigo(), urlAlvo);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,*/*");
            headers.set("Accept-Language", "pt-BR,pt;q=0.9");
            headers.set("Cache-Control", "no-cache");

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(urlAlvo), HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.info("CupomValidadorService: Cupom [{}] retornou 404. Marcando como não testado (quarentena).", cupom.getCodigo());
                return false;
            }

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("CupomValidadorService: Cupom [{}] retornou HTTP {}. Aprovando por padrão.", cupom.getCodigo(), response.getStatusCode());
                return true;
            }

            String corpo = response.getBody();
            if (corpo != null) {
                String corpoLower = corpo.toLowerCase();
                for (String sinal : SINAIS_INVALIDO) {
                    if (corpoLower.contains(sinal.toLowerCase())) {
                        logger.info("CupomValidadorService: Cupom [{}] reprovado — sinal de invalidade detectado: \"{}\".", cupom.getCodigo(), sinal);
                        return false;
                    }
                }
            }

            logger.info("CupomValidadorService: Cupom [{}] aprovado automaticamente ✅", cupom.getCodigo());
            return true;

        } catch (HttpClientErrorException.NotFound e) {
            logger.info("CupomValidadorService: Cupom [{}] retornou 404. Quarentena ativada.", cupom.getCodigo());
            return false;

        } catch (HttpClientErrorException e) {
            logger.warn("CupomValidadorService: Erro HTTP {} ao validar cupom [{}]. Aprovando por padrão.",
                    e.getStatusCode(), cupom.getCodigo());
            return true;

        } catch (ResourceAccessException e) {
            // Timeout ou sem conectividade — não penaliza o cupom por falha de rede
            logger.warn("CupomValidadorService: Timeout ao sondar cupom [{}] ({}). Aprovando por padrão para não bloquear cupons válidos.",
                    cupom.getCodigo(), e.getMessage());
            return true;

        } catch (Exception e) {
            logger.warn("CupomValidadorService: Falha inesperada ao validar cupom [{}]: {}. Aprovando por padrão.",
                    cupom.getCodigo(), e.getMessage());
            return true;
        }
    }

    /**
     * Resolve a melhor URL para sondar:
     * - Se o cupom tem um linkHotsite específico da campanha, usa esse.
     * - Caso contrário, constrói a URL padrão do ML com o código do cupom.
     */
    private String resolverUrlAlvo(Cupom cupom) {
        if (cupom.getLinkHotsite() != null && !cupom.getLinkHotsite().isBlank()
                && !cupom.getLinkHotsite().equals("https://www.mercadolivre.com.br/cupons")) {
            return cupom.getLinkHotsite();
        }
        return String.format(URL_CUPOM_ML, cupom.getCodigo().toLowerCase());
    }
}
