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
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Cupom;
import com.smart.price.enums.StatusCupom;

/**
 * Serviço de validação automática de cupons via sonda HTTP.
 *
 * Quando o crawler captura um cupom, este serviço faz uma requisição
 * na página de ativação do Mercado Livre para verificar se o cupom
 * ainda está ativo antes de gravar no banco.
 *
 * A sonda nunca confirma um cupom: só um teste no checkout (endpoint de confirmação) faz isso.
 *  - Página 404 ou texto claro de cupom encerrado → INVALIDO (nunca é pareado)
 *  - Página OK sem sinais de encerramento         → NAO_VERIFICADO
 *  - Timeout, bloqueio, erro de rede ou HTTP != 2xx → NAO_VERIFICADO
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
     * Frases que indicam que o cupom está inativo ou expirado na página do ML.
     * Termos genéricos como "error", "404" e "not found" ficaram de fora: aparecem em scripts
     * e no HTML de qualquer página e reprovavam cupons bons.
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
            "this coupon is not valid"
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
     * Sonda a página do cupom no Mercado Livre. Falha de rede ou bloqueio nunca reprova o cupom:
     * vira NAO_VERIFICADO e o post só avisa que há cupom, sem prometer preço.
     */
    public StatusCupom validarCupomAutomaticamente(Cupom cupom) {
        if (!enabled) {
            logger.debug("CupomValidadorService: Validação automática desabilitada. Cupom [{}] fica não verificado.", cupom.getCodigo());
            return StatusCupom.NAO_VERIFICADO;
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
                logger.info("CupomValidadorService: Cupom [{}] retornou 404. Marcado como inválido.", cupom.getCodigo());
                return StatusCupom.INVALIDO;
            }

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("CupomValidadorService: Cupom [{}] retornou HTTP {}. Fica não verificado.", cupom.getCodigo(), response.getStatusCode());
                return StatusCupom.NAO_VERIFICADO;
            }

            String corpo = response.getBody();
            if (corpo != null) {
                String corpoLower = corpo.toLowerCase();
                for (String sinal : SINAIS_INVALIDO) {
                    if (corpoLower.contains(sinal)) {
                        logger.info("CupomValidadorService: Cupom [{}] inválido — sinal de encerramento detectado: \"{}\".", cupom.getCodigo(), sinal);
                        return StatusCupom.INVALIDO;
                    }
                }
            }

            logger.info("CupomValidadorService: Cupom [{}] sem sinais de encerramento. Fica não verificado até teste no checkout.", cupom.getCodigo());
            return StatusCupom.NAO_VERIFICADO;

        } catch (HttpClientErrorException.NotFound e) {
            logger.info("CupomValidadorService: Cupom [{}] retornou 404. Marcado como inválido.", cupom.getCodigo());
            return StatusCupom.INVALIDO;

        } catch (Exception e) {
            // Timeout, bloqueio (403/429), erro de rede ou falha inesperada: não dá para afirmar nada
            logger.warn("CupomValidadorService: Não foi possível verificar o cupom [{}] ({}). Fica não verificado.",
                    cupom.getCodigo(), e.getMessage());
            return StatusCupom.NAO_VERIFICADO;
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
