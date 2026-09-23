package com.smart.price.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

@Service
public class MercadoLivrePesquisaPorNomeService {

    private static final Logger logger = LoggerFactory.getLogger(MercadoLivrePesquisaPorNomeService.class);

    private final RestTemplate restTemplate;
    private final MercadoLivreAuthService authService;

    // Cache em memória de produtos que retornaram 404 (sem vendedor ativo) para economizar requests
    private final Map<String, LocalDateTime> cacheSemVencedores = new ConcurrentHashMap<>();

    @Value("${mercadolivre.api.base-url:https://api.mercadolibre.com}")
    private String baseUrl;

    public MercadoLivrePesquisaPorNomeService(RestTemplate restTemplate, MercadoLivreAuthService authService) {
        this.restTemplate = restTemplate;
        this.authService = authService;
    }

    /**
     * Pesquisa produtos no Mercado Livre por termo e retorna entidades OfertaDescoberta
     * prontas para curadoria e notificação com fotos e cálculo de desconto real.
     */
    public List<OfertaDescoberta> pesquisarOfertasDescobertas(String termo, Nicho nicho) {
        if (termo == null || termo.isBlank()) {
            return Collections.emptyList();
        }

        String token = authService.obterAccessToken();
        if (token == null || token.isBlank()) {
            logger.error("MercadoLivrePesquisaPorNomeService: Token de acesso não disponível.");
            return Collections.emptyList();
        }

        List<OfertaDescoberta> ofertas = new ArrayList<>();

        try {
            String termoCodificado = URLEncoder.encode(termo.trim(), StandardCharsets.UTF_8);
            // Randomiza o offset entre as primeiras páginas (0, 15, 30) para explorar produtos variados a cada ciclo
            int randomOffset = new java.util.Random().nextInt(3) * 15;
            String urlCatalogo = String.format("%s/products/search?status=active&site_id=MLB&q=%s&limit=15&offset=%d",
                    baseUrl, termoCodificado, randomOffset);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("MercadoLivrePesquisaPorNomeService: Buscando ofertas para nicho [{}] termo '{}' (offset {})...",
                    nicho != null ? nicho.getNome() : "GERAL", termo, randomOffset);

            ResponseEntity<Map> response = restTemplate.exchange(URI.create(urlCatalogo), HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> produtos = (List<Map<String, Object>>) response.getBody().get("results");

                // Fallback para offset=0 caso a página sorteada não retorne resultados (termo com poucos itens cadastrados)
                if ((produtos == null || produtos.isEmpty()) && randomOffset > 0) {
                    String urlFallback = String.format("%s/products/search?status=active&site_id=MLB&q=%s&limit=15",
                            baseUrl, termoCodificado);
                    ResponseEntity<Map> respFallback = restTemplate.exchange(URI.create(urlFallback), HttpMethod.GET, entity, Map.class);
                    if (respFallback.getStatusCode().is2xxSuccessful() && respFallback.getBody() != null) {
                        produtos = (List<Map<String, Object>>) respFallback.getBody().get("results");
                    }
                }

                if (produtos != null) {
                    for (Map<String, Object> produto : produtos) {
                        Object statusProd = produto.get("status");
                        if (statusProd instanceof String s && !"active".equalsIgnoreCase(s)) {
                            continue;
                        }
                        String produtoId = (String) produto.get("id");
                        String nomeProduto = (String) produto.get("name");
                        String fotoUrl = extrairFotoUrl(produto);

                        List<OfertaDescoberta> ofertasDoItem = extrairItensParaOfertaDescoberta(
                                produtoId, nomeProduto, termo, nicho, fotoUrl, headers);
                        ofertas.addAll(ofertasDoItem);

                        if (ofertas.size() >= 15) {
                            break;
                        }
                    }
                }
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 429) {
                tratarRateLimit();
            } else {
                logger.warn("MercadoLivrePesquisaPorNomeService: Erro HTTP [{}] na busca de catálogo: {}",
                        ex.getStatusCode(), ex.getResponseBodyAsString());
            }
        } catch (Exception ex) {
            logger.error("MercadoLivrePesquisaPorNomeService: Erro ao pesquisar ofertas para termo '{}': {}",
                    termo, ex.getMessage());
        }

        return ofertas;
    }

    /**
     * Pesquisa os produtos mais vendidos / em alta no Mercado Livre para as categorias oficiais (MLB Category IDs)
     * vinculadas ao nicho, sem necessidade de termos mockados pré-definidos.
     */
    public List<OfertaDescoberta> pesquisarOfertasEmAltaPorCategoria(String categoriaMlb, Nicho nicho) {
        if (categoriaMlb == null || categoriaMlb.isBlank()) {
            return Collections.emptyList();
        }

        String token = authService.obterAccessToken();
        if (token == null || token.isBlank()) {
            logger.error("MercadoLivrePesquisaPorNomeService: Token de acesso não disponível.");
            return Collections.emptyList();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        List<OfertaDescoberta> ofertas = new ArrayList<>();
        List<String> categorias = new ArrayList<>(java.util.Arrays.asList(categoriaMlb.split(",")));
        Collections.shuffle(categorias);

        for (String catIdRaw : categorias) {
            String catId = catIdRaw.trim();
            if (catId.isEmpty()) {
                continue;
            }

            try {
                String urlHighlights = String.format("%s/highlights/MLB/category/%s", baseUrl, catId);
                logger.info("MercadoLivrePesquisaPorNomeService: Minerando produtos em alta na categoria [{}] para nicho [{}]...",
                        catId, nicho != null ? nicho.getNome() : "GERAL");

                ResponseEntity<Map> response = restTemplate.exchange(URI.create(urlHighlights), HttpMethod.GET, entity, Map.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    continue;
                }

                List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
                if (content == null || content.isEmpty()) {
                    continue;
                }

                // Embaralha a lista de itens em alta para alternar os produtos analisados a cada ciclo
                List<Map<String, Object>> contentEmbaralhado = new ArrayList<>(content);
                Collections.shuffle(contentEmbaralhado);

                List<CompletableFuture<List<OfertaDescoberta>>> futures = new ArrayList<>();
                int limiteItens = 0;

                for (Map<String, Object> itemHl : contentEmbaralhado) {
                    if (limiteItens >= 10) {
                        break;
                    }

                    Object typeObj = itemHl.get("type");
                    if (typeObj != null && !"PRODUCT".equalsIgnoreCase(typeObj.toString())) {
                        continue;
                    }

                    String produtoId = (String) itemHl.get("id");
                    if (produtoId == null || produtoId.isBlank()) {
                        continue;
                    }

                    LocalDateTime horario404 = cacheSemVencedores.get(produtoId);
                    if (horario404 != null && horario404.isAfter(LocalDateTime.now().minusHours(2))) {
                        continue;
                    }

                    limiteItens++;

                    // Dispara a consulta dos dados e itens do produto de forma paralela e não bloqueante
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            String urlProduto = String.format("%s/products/%s", baseUrl, produtoId);
                            ResponseEntity<Map> respProd = restTemplate.exchange(URI.create(urlProduto), HttpMethod.GET, entity, Map.class);
                            if (respProd.getStatusCode().is2xxSuccessful() && respProd.getBody() != null) {
                                Map<String, Object> prodData = respProd.getBody();
                                Object statusProd = prodData.get("status");
                                if (statusProd instanceof String s && !"active".equalsIgnoreCase(s)) {
                                    return Collections.<OfertaDescoberta>emptyList();
                                }

                                String nomeProduto = (String) prodData.get("name");
                                String fotoUrl = extrairFotoUrl(prodData);

                                return extrairItensParaOfertaDescoberta(
                                        produtoId, nomeProduto, "Em Alta " + (nicho != null ? nicho.getNome() : catId), nicho, fotoUrl, headers);
                            }
                        } catch (HttpStatusCodeException ex) {
                            if (ex.getStatusCode().value() == 404) {
                                cacheSemVencedores.put(produtoId, LocalDateTime.now());
                            } else if (ex.getStatusCode().value() == 429) {
                                tratarRateLimit();
                            }
                        } catch (Exception e) {
                            logger.debug("MercadoLivrePesquisaPorNomeService: Falha ao obter dados do produto {}: {}", produtoId, e.getMessage());
                        }
                        return Collections.<OfertaDescoberta>emptyList();
                    }));
                }

                // Aguarda a conclusão de todos os itens da categoria em paralelo
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<List<OfertaDescoberta>> f : futures) {
                    try {
                        List<OfertaDescoberta> res = f.get();
                        if (res != null && !res.isEmpty()) {
                            ofertas.addAll(res);
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode().value() == 429) {
                    tratarRateLimit();
                } else {
                    logger.warn("MercadoLivrePesquisaPorNomeService: Erro HTTP [{}] ao buscar destaques da categoria {}: {}",
                            ex.getStatusCode(), catId, ex.getResponseBodyAsString());
                }
            } catch (Exception ex) {
                logger.error("MercadoLivrePesquisaPorNomeService: Erro ao buscar destaques da categoria {}: {}",
                        catId, ex.getMessage());
            }
        }

        return ofertas;
    }

    private List<OfertaDescoberta> extrairItensParaOfertaDescoberta(
            String produtoId, String nomeProduto, String termo, Nicho nicho, String fotoUrl, HttpHeaders headers) {

        if (produtoId != null) {
            LocalDateTime horario404 = cacheSemVencedores.get(produtoId);
            if (horario404 != null && horario404.isAfter(LocalDateTime.now().minusHours(2))) {
                return Collections.emptyList();
            }
        }

        List<OfertaDescoberta> lista = new ArrayList<>();
        try {
            String urlItems = String.format("%s/products/%s/items", baseUrl, produtoId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(URI.create(urlItems), HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");

                if (results != null && !results.isEmpty()) {
                    // Seleciona exclusivamente o vendedor vencedor da Buy Box (menor preço ativo, com desempate por frete grátis)
                    results.stream()
                            .filter(this::isItemDisponivel)
                            .min(Comparator.comparingDouble((Map<String, Object> item) -> ((Number) item.get("price")).doubleValue())
                                    .thenComparing(item -> {
                                        Map<String, Object> shipping = (Map<String, Object>) item.get("shipping");
                                        return (shipping != null && Boolean.TRUE.equals(shipping.get("free_shipping"))) ? 0 : 1;
                                    }))
                            .ifPresent(item -> {
                                Number precoNum = (Number) item.get("price");
                                String itemId = (String) item.get("item_id");
                                BigDecimal preco = BigDecimal.valueOf(precoNum.doubleValue());

                                Number originalPriceNum = (Number) item.get("original_price");
                                BigDecimal precoOriginal = (originalPriceNum != null)
                                        ? BigDecimal.valueOf(originalPriceNum.doubleValue())
                                        : null;

                                // Cálculo do percentual de desconto real baseado no preço do vendedor vencedor
                                Integer descontoPercentual = 0;
                                if (precoOriginal != null && precoOriginal.compareTo(preco) > 0) {
                                    BigDecimal diferenca = precoOriginal.subtract(preco);
                                    descontoPercentual = diferenca.multiply(BigDecimal.valueOf(100))
                                            .divide(precoOriginal, 0, RoundingMode.HALF_UP)
                                            .intValue();
                                }

                                Boolean freeShipping = Boolean.FALSE;
                                Map<String, Object> shipping = (Map<String, Object>) item.get("shipping");
                                if (shipping != null) {
                                    freeShipping = Boolean.TRUE.equals(shipping.get("free_shipping"));
                                }

                                String linkProduto;
                                if (itemId != null && !itemId.isBlank()) {
                                    String idFormatado = itemId.matches("(?i)MLB\\d+")
                                            ? itemId.replaceFirst("(?i)MLB", "MLB-")
                                            : itemId;
                                    linkProduto = "https://produto.mercadolivre.com.br/" + idFormatado;
                                } else if (produtoId != null && !produtoId.isBlank()) {
                                    linkProduto = "https://www.mercadolivre.com.br/p/" + produtoId;
                                } else {
                                    linkProduto = "https://www.mercadolivre.com.br";
                                }

                                OfertaDescoberta oferta = new OfertaDescoberta();
                                // IMPORTANTE: Usar produtoId canônico como mlbId para garantir unicidade e anti-duplicação
                                oferta.setMlbId(produtoId != null && !produtoId.isBlank() ? produtoId : itemId);
                                oferta.setTitulo(nomeProduto != null ? nomeProduto : termo);
                                oferta.setPreco(preco);
                                oferta.setPrecoOriginal(precoOriginal);
                                oferta.setDescontoPercentual(descontoPercentual);
                                oferta.setFreteGratis(freeShipping);
                                oferta.setFotoUrl(fotoUrl);
                                oferta.setUrl(linkProduto);
                                oferta.setNicho(nicho);
                                oferta.setTermoOrigem(termo);
                                oferta.setDataDescoberta(LocalDateTime.now());
                                oferta.setStatusEnvio("PENDENTE");
                                oferta.setDisponivel(Boolean.TRUE);

                                lista.add(oferta);
                            });
                }
                if (lista.isEmpty() && produtoId != null) {
                    cacheSemVencedores.put(produtoId, LocalDateTime.now());
                }
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                cacheSemVencedores.put(produtoId, LocalDateTime.now());
            } else if (ex.getStatusCode().value() == 429) {
                tratarRateLimit();
            }
        } catch (Exception ex) {
            logger.debug("MercadoLivrePesquisaPorNomeService: Falha ao extrair itens do produto {}: {}", produtoId, ex.getMessage());
        }
        return lista;
    }

    /**
     * Valida rigorosamente se o item do vendedor está ativo, disponível para compra imediata
     * e possui estoque/preço válidos.
     */
    private boolean isItemDisponivel(Map<String, Object> item) {
        if (item == null) {
            return false;
        }

        // 1. Preço deve ser numérico e > 0
        if (!(item.get("price") instanceof Number p) || p.doubleValue() <= 0) {
            return false;
        }

        // 2. Status do item deve ser 'active' (nunca paused, closed ou inactive)
        Object statusObj = item.get("status");
        if (statusObj instanceof String status) {
            String s = status.trim().toLowerCase();
            if (!"active".equals(s)) {
                return false;
            }
        }

        // 3. Quantidade disponível deve ser > 0 se o campo existir
        Object qtyObj = item.get("available_quantity");
        if (qtyObj instanceof Number qty && qty.intValue() <= 0) {
            return false;
        }

        // 4. Aceita pagamento pelo Mercado Pago
        Object acceptsMp = item.get("accepts_mercadopago");
        if (Boolean.FALSE.equals(acceptsMp)) {
            return false;
        }

        // 5. Sub-status não pode conter indicação de falta de estoque ou suspensão
        Object subStatusObj = item.get("sub_status");
        if (subStatusObj instanceof List<?> subList) {
            for (Object sub : subList) {
                if (sub instanceof String s) {
                    String subLower = s.toLowerCase();
                    if (subLower.contains("out_of_stock") || subLower.contains("suspended")
                            || subLower.contains("waiting_for_patch") || subLower.contains("under_review")) {
                        return false;
                    }
                }
            }
        }

        // 6. Tags não podem conter 'out_of_stock' ou 'paused'
        Object tagsObj = item.get("tags");
        if (tagsObj instanceof List<?> tagsList) {
            for (Object tag : tagsList) {
                if (tag instanceof String t) {
                    String tagLower = t.toLowerCase();
                    if (tagLower.contains("out_of_stock") || tagLower.contains("paused")) {
                        return false;
                    }
                }
            }
        }

        // 7. Condição do item: deve ser novo se informado (ignora usados / reembalados)
        Object conditionObj = item.get("condition");
        if (conditionObj instanceof String cond) {
            String condLower = cond.trim().toLowerCase();
            if ("used".equals(condLower) || "not_specified".equals(condLower)) {
                return false;
            }
        }

        return true;
    }

    private String extrairFotoUrl(Map<String, Object> produto) {
        if (produto == null) return null;
        Object picsObj = produto.get("pictures");
        if (picsObj instanceof List<?> picsList && !picsList.isEmpty()) {
            Object first = picsList.get(0);
            if (first instanceof Map<?, ?> picMap) {
                Object url = picMap.get("url");
                if (url instanceof String s && !s.isBlank()) {
                    return s.trim();
                }
            }
        }
        return null;
    }

    private void tratarRateLimit() {
        logger.warn("MercadoLivre: Rate limit 429 atingido! Aguardando 10 segundos antes de continuar...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
