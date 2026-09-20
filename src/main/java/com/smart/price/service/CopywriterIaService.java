package com.smart.price.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.OfertaDescoberta;

@Service
public class CopywriterIaService {

    private static final Logger logger = LoggerFactory.getLogger(CopywriterIaService.class);
    private static final Locale LOCALE_BR = Locale.of("pt", "BR");

    private final RestTemplate restTemplate;

    @Value("${ia.gemini.enabled:true}")
    private boolean enabled;

    @Value("${ia.gemini.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${ia.gemini.model:gemini-flash-latest}")
    private String model;

    public CopywriterIaService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sobrecarga para compatibilidade que aceita código de cupom em formato String.
     */
    public String gerarCopyOferta(OfertaDescoberta oferta, String cupomCodigo) {
        return gerarCopyOferta(oferta, cupomCodigo, null);
    }

    /**
     * Gera uma copy persuasiva e personalizada para uma oferta de produto.
     * O gancho criativo de abertura é gerado pela IA (ou sorteado de ganchos de alta conversão),
     * enquanto os dados vitais (título, preço antigo, preço promocional, desconto %, cupom e frete)
     * são montados de forma 100% canônica e determinística pelo sistema, garantindo que nenhum post
     * seja publicado sem preços ou com frases cortadas.
     */
    public String gerarCopyOferta(OfertaDescoberta oferta, String cupomCodigo, BigDecimal precoComCupom) {
        if (oferta == null) {
            return "";
        }

        // Anúncio limpo, direto e profissional (estilo Herói da Promo / sem frases artificiais de IA)
        return formatarMensagemCompleta(oferta, null, cupomCodigo, precoComCupom, oferta.getUrl());
    }

    /**
     * Gera uma copy de alto impacto para o anúncio avulso de um cupom recém-lançado.
     */
    public String gerarCopyCupomAvulso(Cupom cupom, String linkFinal) {
        if (cupom == null) {
            return "";
        }

        if (enabled && apiKey != null && !apiKey.isBlank()) {
            try {
                String prompt = construirPromptCupomAvulso(cupom, linkFinal);
                String copyGerada = chamarGeminiApi(prompt, 500);
                if (copyGerada != null && !copyGerada.isBlank()) {
                    logger.info("CopywriterIaService: Copy de cupom avulso gerada via IA para [{}].", cupom.getCodigo());
                    return copyGerada.trim();
                }
            } catch (Exception ex) {
                logger.warn("CopywriterIaService: Falha ao gerar copy de cupom avulso com Gemini ({}). Ativando fallback...", ex.getMessage());
            }
        }

        return gerarCopyCupomAvulsoFallback(cupom, linkFinal);
    }

    /**
     * Monta a mensagem completa e estruturada da oferta para o Telegram,
     * no estilo limpo e de altíssima conversão de canais como Herói da Promo.
     */
    public String formatarMensagemCompleta(OfertaDescoberta oferta, String gancho, String cupomCodigo, BigDecimal precoComCupom, String link) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        StringBuilder sb = new StringBuilder();

        // 1. Gancho Criativo (apenas se explicitamente fornecido)
        if (gancho != null && !gancho.isBlank()) {
            sb.append(gancho.trim()).append("\n\n");
        }

        // 2. Título do Produto em Destaque
        sb.append("🔥 *").append(oferta.getTitulo().trim()).append("*\n\n");

        // 3. Preço Original "De: R$ ..." (se houver desconto real apurado)
        boolean temPrecoOriginal = oferta.getPrecoOriginal() != null && oferta.getPrecoOriginal().compareTo(oferta.getPreco()) > 0;
        if (temPrecoOriginal) {
            sb.append("💵 De: ~").append(moeda.format(oferta.getPrecoOriginal())).append("~\n");
        }

        // 4. Preço Promocional Atual "Por apenas: R$ ..." + Badge de % OFF
        int desc = (oferta.getDescontoPercentual() != null && oferta.getDescontoPercentual() > 0) ? oferta.getDescontoPercentual() : 0;
        sb.append("💥 *Por apenas: ").append(moeda.format(oferta.getPreco())).append("*");
        if (desc > 0) {
            sb.append(" (📉 *").append(desc).append("% OFF*)");
        }
        sb.append("\n");

        // 5. Cupom Aplicável (com código mono-espaçado para cópia em 1 toque)
        if (cupomCodigo != null && !cupomCodigo.isBlank()) {
            String precoFinal = (precoComCupom != null) ? moeda.format(precoComCupom) : moeda.format(oferta.getPreco());
            sb.append("🎟️ *COM CUPOM:* Aplique `").append(cupomCodigo.trim()).append("` no checkout ➡️ *").append(precoFinal).append("!*\n");
        }

        // 6. Frete Grátis
        if (Boolean.TRUE.equals(oferta.getFreteGratis())) {
            sb.append("🚚 *Frete Grátis incluso!*\n");
        }

        // 7. Link Oficial de Compra (Hiperlink curto e limpo)
        String linkFinal = (link != null && !link.isBlank()) ? link : (oferta.getUrl() != null ? oferta.getUrl() : "");
        if (!linkFinal.isBlank()) {
            String textoCurto = formatarTextoLinkCurto(linkFinal, oferta.getMlbId());
            sb.append("\n🛒 *Compre aqui:*\n👉 [").append(textoCurto).append("](").append(linkFinal).append(")");
        }

        return sb.toString();
    }

    public String formatarTextoLinkCurto(String url, String mlbId) {
        if (mlbId != null && !mlbId.isBlank()) {
            return "mercadolivre.com.br/" + mlbId.trim();
        }
        if (url != null && url.contains("MLB-")) {
            try {
                int idx = url.indexOf("MLB-");
                int endIdx = url.indexOf("?", idx);
                if (endIdx < 0) {
                    endIdx = url.indexOf("/", idx);
                }
                if (endIdx < 0) {
                    endIdx = url.length();
                }
                String code = url.substring(idx, endIdx);
                return "mercadolivre.com.br/" + code;
            } catch (Exception ignored) {
            }
        }
        return "mercadolivre.com.br/oferta";
    }

    private String chamarGeminiApi(String prompt) {
        return chamarGeminiApi(prompt, 120);
    }

    private String chamarGeminiApi(String prompt, int maxTokens) {
        String urlEndpoint = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                model, apiKey.trim()
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.8,
                        "maxOutputTokens", maxTokens
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(urlEndpoint, requestEntity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<?, ?> body = response.getBody();
            List<?> candidates = (List<?>) body.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                if (content != null) {
                    List<?> parts = (List<?>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                        return (String) firstPart.get("text");
                    }
                }
            }
        }

        return null;
    }

    private String construirPromptGancho(OfertaDescoberta oferta, String cupomCodigo) {
        String nichoNome = oferta.getNicho() != null ? oferta.getNicho().getNome() : "GERAL";
        int desc = (oferta.getDescontoPercentual() != null && oferta.getDescontoPercentual() > 0) ? oferta.getDescontoPercentual() : 0;
        boolean menorPreco = Boolean.TRUE.equals(oferta.getMenorPrecoHistorico());

        String dicaNicho = switch (nichoNome.toUpperCase()) {
            case String n when n.contains("GAME") || n.contains("CONSOLE") ->
                "Gamer e empolgado, use gírias leves de jogos.";
            case String n when n.contains("HARDWARE") || n.contains("INFORM") ->
                "Entusiasta de setup, PC gamer e hardware.";
            case String n when n.contains("SMARTPHONE") || n.contains("WEARABLE") ->
                "Moderno, focado em tecnologia do dia a dia.";
            case String n when n.contains("CASA") || n.contains("ELETRO") ->
                "Prático, focado em facilitar a rotina da casa.";
            case String n when n.contains("PERFUM") || n.contains("COSMÉTIC") || n.contains("BELEZA") ->
                "Charmoso, focado em beleza, elogios e economia.";
            default ->
                "Extrovertido e divertido.";
        };

        return String.format("""
                Você é o administrador do maior canal de promoções do Telegram no Brasil.
                Escreva APENAS 1 OU 2 FRASES CURTAS de abertura (gancho com emojis) para anunciar este produto:
                - Produto: %s
                - Nicho: %s (%s)
                - Desconto: %d%% OFF
                - Menor preço histórico: %s
                - Tem cupom: %s

                REGRAS ESTRITAS:
                1. Escreva no máximo 2 frases curtas, animadas e completas com emojis chamativos.
                2. NUNCA liste valores em Reais (R$), preços, números de parcelamento ou links.
                3. NUNCA deixe frases incompletas ou cortadas.
                4. NUNCA use introduções como 'Aqui está:' ou 'Opção:'. Retorne DIRETO o gancho pronto.
                """,
                oferta.getTitulo(),
                nichoNome,
                dicaNicho,
                desc,
                menorPreco ? "SIM" : "NÃO",
                (cupomCodigo != null && !cupomCodigo.isBlank()) ? "SIM (" + cupomCodigo + ")" : "NÃO"
        );
    }

    private String validarELimparGancho(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String limpo = raw.trim();

        // Remove quebras de linha múltiplas no gancho
        limpo = limpo.replaceAll("\\n{2,}", "\n");

        // Remove aspas envolventes se a IA colocou
        if ((limpo.startsWith("\"") && limpo.endsWith("\"")) || (limpo.startsWith("“") && limpo.endsWith("”"))) {
            limpo = limpo.substring(1, limpo.length() - 1).trim();
        }

        // Remove prefixos conversacionais caso a IA tenha gerado
        limpo = limpo.replaceAll("(?i)^(aqui está[^:\\n]*:|opção \\d+:?|olha esse gancho:?|claro!?:?)\\s*", "").trim();

        // Se ficou muito curto ou anormalmente longo, descarta
        if (limpo.length() < 12 || limpo.length() > 250) {
            return null;
        }

        // Garante fechamento limpo se a frase terminar sem pontuação
        char ultimo = limpo.charAt(limpo.length() - 1);
        if (Character.isLetterOrDigit(ultimo)) {
            limpo = limpo + "!";
        }

        return limpo;
    }

    private String sortearFraseUrgencia() {
        String[] urgencias = {
            "\n⚡ _Estoque limitado. Esse preço não espera por ninguém!_\n",
            "\n⚠️ _Os gafanhotos do grupo já estão de olho — corre antes que acabe!_\n",
            "\n🕐 _Preço promocional por tempo limitado. Garanta o seu antes que suba!_\n"
        };
        return urgencias[new Random().nextInt(urgencias.length)];
    }

    private String construirPromptCupomAvulso(Cupom cupom, String linkFinal) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        String valorDesc = "PERCENTUAL".equalsIgnoreCase(cupom.getTipoDesconto())
                ? cupom.getValorDesconto().intValue() + "% OFF"
                : moeda.format(cupom.getValorDesconto()) + " OFF";

        String condicaoMinima = (cupom.getValorMinimoCompra() != null && cupom.getValorMinimoCompra().compareTo(BigDecimal.ZERO) > 0)
                ? "Compras a partir de " + moeda.format(cupom.getValorMinimoCompra())
                : "Sem valor mínimo";

        String nichoStr = (cupom.getNicho() != null) ? cupom.getNicho().getNome() : "Todo o site Mercado Livre";

        return String.format("""
                Você é o admin mais animado e extrovertido do maior canal de achados e promoções do Brasil no Telegram.
                Acabou de sair um CUPOM EXCLUSIVO no Mercado Livre e você não aguentou — precisa avisar todo mundo AGORA!
                Crie um anúncio urgente, animado e que faça as pessoas correm para apertar o link antes que o cupom se esgote.

                DADOS DO CUPOM:
                - Código: %s
                - Desconto: %s
                - Compra mínima: %s
                - Válido para: %s
                - Descrição: %s
                - Link de ativação (USE EXATAMENTE ESTE LINK, sem alterar): %s

                INSTRUÇÕES OBRIGATÓRIAS:
                1. ALERTA DE ABERTURA: Comece com uma frase de choque em CAIXA ALTA com emojis explosivos. Seja criativo — não use sempre a mesma frase. Exemplos de tom (NÃO copie, crie algo original): "🚨 CAPTURAMOS UM CUPOM SELVAGEM NO MERCADO LIVRE!", "🔥 GENTE, SAIU CUPOM! O ADMIN NÃO VAI DEIXAR SÓ PRA ELE!", "⚡ ALERTA VERMELHO: CUPOM NOVO EM ÁREA DE RISCO DE SUMIR!".
                2. Destaque o código entre crases assim: `%s` — para que o membro toque no Telegram e copie em 1 toque.
                3. Mostre o desconto, o valor mínimo e a categoria de forma visual e clara.
                4. Urgência real: cupons do ML têm limite de ativação e somem em minutos quando o grupo descobre.
                5. Finalize com: 👉 %s
                6. PROIBIDO: linguagem de robô, "Excelente oportunidade", saudações corporativas, introduções como "Aqui está:".
                7. Retorne SOMENTE o texto pronto para disparar no Telegram.
                """,
                cupom.getCodigo(),
                valorDesc,
                condicaoMinima,
                nichoStr,
                cupom.getDescricao() != null ? cupom.getDescricao() : "não informada",
                linkFinal,
                cupom.getCodigo(),
                linkFinal
        );
    }

    /**
     * Fallback animado para oferta de produto — usado quando a API do Gemini está indisponível.
     */
    public String gerarCopyFallback(OfertaDescoberta oferta, String cupomCodigo, BigDecimal precoComCupom) {
        String gancho = sortearGanchoFallback(oferta);
        return formatarMensagemCompleta(oferta, gancho, cupomCodigo, precoComCupom, oferta.getUrl());
    }

    public String sortearGanchoFallback(OfertaDescoberta oferta) {
        Random rand = new Random();
        if (Boolean.TRUE.equals(oferta.getMenorPrecoHistorico())) {
            String[] ganchosMenorPreco = {
                "🚨 *HISTÓRICO! NUNCA ESTEVE TÃO BARATO!* 🚨\nEsse preço nunca aconteceu antes — e pode não acontecer de novo! 😱",
                "📉 *MENOR VALOR DA HISTÓRIA REGISTRADO!* 📉\nCaiu pro menor valor já visto no Mercado Livre! Fuja do arrependimento!",
                "🏆 *ALERTA VERMELHO: RECORDE DE MENOR PREÇO!* 🏆\nO menor valor que você vai ver hoje — corre antes que o estoque zere!"
            };
            return ganchosMenorPreco[rand.nextInt(ganchosMenorPreco.length)];
        } else if (oferta.getDescontoPercentual() != null && oferta.getDescontoPercentual() >= 30) {
            String[] ganchosAltoDesconto = {
                String.format("🔥 *O ESTAGIÁRIO DO ML PIROU — %d%% OFF!* 🔥\nPreço despencou! Aproveita enquanto o estoque aguenta!", oferta.getDescontoPercentual()),
                String.format("💣 *MINHA FATURA QUE LUTE, MAS %d%% OFF NÃO DÁ PRA IGNORAR!* 💣\nDesconto pesado de verdade pra quem sabe aproveitar!", oferta.getDescontoPercentual()),
                String.format("🤯 *%d%% DE DESCONTO REAL NO MERCADO LIVRE!* 🤯\nPreço muito abaixo da média! Quem perdoa é Deus, o estoque não!", oferta.getDescontoPercentual())
            };
            return ganchosAltoDesconto[rand.nextInt(ganchosAltoDesconto.length)];
        } else {
            String[] ganchosGerais = {
                "👀 *ACHADO DE HOJE: VALE MUITO O CLIQUE!* 👀\nPreço caiu e a oferta tá excelente — confira antes que suba!",
                "🎯 *PROMOÇÃO ATIVA COM PREÇO REDUZIDO!* 🎯\nSó pros que sabem aproveitar uma boa oportunidade quando veem!",
                "🛎️ *ALERTA DE PROMOÇÃO NO MERCADO LIVRE!* 🛎️\nBoa oportunidade pra garantir o seu agora pagando menos!"
            };
            return ganchosGerais[rand.nextInt(ganchosGerais.length)];
        }
    }

    /**
     * Fallback inteligente para anúncio avulso de cupom.
     */
    public String gerarCopyCupomAvulsoFallback(Cupom cupom, String linkFinal) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        StringBuilder sb = new StringBuilder();

        sb.append("🚨 *NOVO CUPOM LIBERADO NO MERCADO LIVRE!* 🚨\n\n");

        String valorDesc = "PERCENTUAL".equalsIgnoreCase(cupom.getTipoDesconto())
                ? cupom.getValorDesconto().intValue() + "% OFF"
                : moeda.format(cupom.getValorDesconto()) + " OFF";

        sb.append("🎟️ Cupom: `").append(cupom.getCodigo()).append("` _(toque para copiar)_\n");
        sb.append("💰 *Desconto:* ").append(valorDesc).append("\n");

        if (cupom.getValorMinimoCompra() != null && cupom.getValorMinimoCompra().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("📦 *Válido para compras a partir de:* ").append(moeda.format(cupom.getValorMinimoCompra())).append("\n");
        }

        if (cupom.getNicho() != null) {
            sb.append("🎯 *Categoria:* ").append(cupom.getNicho().getNome()).append("\n");
        } else {
            sb.append("🎯 *Válido em produtos selecionados de todo o site!*\n");
        }

        if (cupom.getDescricao() != null && !cupom.getDescricao().isBlank()) {
            sb.append("ℹ️ ").append(cupom.getDescricao()).append("\n");
        }

        sb.append("\n⏳ *CORRE:* Os cupons do Mercado Livre possuem limite de ativação e costumam acabar rapidamente!\n\n");
        sb.append("🛒 *Ative seu cupom e garanta o desconto:*\n").append(linkFinal);

        return sb.toString();
    }
}
