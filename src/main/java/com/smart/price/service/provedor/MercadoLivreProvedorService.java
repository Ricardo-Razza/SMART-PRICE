package com.smart.price.service.provedor;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.enums.LojaEnum;
import com.smart.price.service.MercadoLivrePesquisaPorNomeService;

@Service
public class MercadoLivreProvedorService implements ProvedorLojaService {

    private static final Logger logger = LoggerFactory.getLogger(MercadoLivreProvedorService.class);

    private final MercadoLivrePesquisaPorNomeService mlPesquisaService;

    @Value("${mercadolivre.provedor.enabled:true}")
    private boolean habilitado;

    @Value("${afiliado.mercadolivre.matt-tool:${ML_MATT_TOOL:32843307}}")
    private String mattTool;

    @Value("${afiliado.mercadolivre.matt-word:${ML_MATT_WORD:smartpricetele}}")
    private String mattWord;

    @Value("${afiliado.mercadolivre.force-in-app:${ML_FORCE_IN_APP:true}}")
    private boolean forceInApp;

    @Value("${afiliado.mercadolivre.tag:${ML_AFILIADO_TAG:}}")
    private String tagAfiliado;

    public MercadoLivreProvedorService(MercadoLivrePesquisaPorNomeService mlPesquisaService) {
        this.mlPesquisaService = mlPesquisaService;
    }

    @Override
    public LojaEnum getLoja() {
        return LojaEnum.MERCADO_LIVRE;
    }

    @Override
    public boolean isHabilitado() {
        return habilitado;
    }

    @Override
    public List<OfertaDescoberta> buscarOfertasPorTermo(String termo, Nicho nicho) {
        if (!isHabilitado()) {
            logger.debug("MercadoLivreProvedorService: Provedor desabilitado temporariamente.");
            return Collections.emptyList();
        }

        List<OfertaDescoberta> ofertas = mlPesquisaService.pesquisarOfertasDescobertas(termo, nicho);
        if (ofertas != null && !ofertas.isEmpty()) {
            for (OfertaDescoberta o : ofertas) {
                o.setLoja(LojaEnum.MERCADO_LIVRE);
                if (o.getUrl() != null && !o.getUrl().isBlank()) {
                    o.setUrl(formatarLinkAfiliado(o.getUrl()));
                }
            }
        }
        return ofertas != null ? ofertas : Collections.emptyList();
    }

    @Override
    public List<OfertaDescoberta> buscarOfertasEmAlta(Nicho nicho) {
        if (!isHabilitado() || nicho == null) {
            return Collections.emptyList();
        }

        String categoriaMlb = nicho.getCategoriaMlb();
        if (categoriaMlb == null || categoriaMlb.isBlank()) {
            logger.warn("MercadoLivreProvedorService: Nicho [{}] não possui categoriaMlb configurada.", nicho.getNome());
            return Collections.emptyList();
        }

        List<OfertaDescoberta> ofertas = mlPesquisaService.pesquisarOfertasEmAltaPorCategoria(categoriaMlb, nicho);
        if (ofertas != null && !ofertas.isEmpty()) {
            for (OfertaDescoberta o : ofertas) {
                o.setLoja(LojaEnum.MERCADO_LIVRE);
                if (o.getUrl() != null && !o.getUrl().isBlank()) {
                    o.setUrl(formatarLinkAfiliado(o.getUrl()));
                }
            }
        }
        return ofertas != null ? ofertas : Collections.emptyList();
    }

    @Override
    public String formatarLinkAfiliado(String urlOriginal) {
        if (urlOriginal == null || urlOriginal.isBlank()) {
            return "";
        }

        String paramsAfiliado;
        if (mattTool != null && !mattTool.isBlank() && mattWord != null && !mattWord.isBlank()) {
            paramsAfiliado = String.format("matt_tool=%s&matt_word=%s%s",
                    mattTool.trim(),
                    mattWord.trim(),
                    forceInApp ? "&forceInApp=true" : "");
        } else if (tagAfiliado != null && !tagAfiliado.isBlank()) {
            paramsAfiliado = tagAfiliado.trim();
            if (paramsAfiliado.startsWith("?") || paramsAfiliado.startsWith("&")) {
                paramsAfiliado = paramsAfiliado.substring(1);
            }
        } else {
            return urlOriginal;
        }

        // Se o link já possui a tag exata, retorna sem duplicar
        if (urlOriginal.contains(paramsAfiliado)) {
            return urlOriginal;
        }

        // Remove eventuais parâmetros de tracking de terceiros (matt_tool, matt_word) para blindar a comissão do usuário
        String urlLimpa = limparParametrosAfiliadosAntigos(urlOriginal);

        String separator = urlLimpa.contains("?") ? "&" : "?";
        return urlLimpa + separator + paramsAfiliado;
    }

    private static final java.util.regex.Pattern PATTERN_MATT_TOOL = java.util.regex.Pattern.compile("([&?])matt_tool=[^&]*");
    private static final java.util.regex.Pattern PATTERN_MATT_WORD = java.util.regex.Pattern.compile("([&?])matt_word=[^&]*");
    private static final java.util.regex.Pattern PATTERN_FORCE_IN_APP = java.util.regex.Pattern.compile("([&?])forceInApp=[^&]*");
    private static final java.util.regex.Pattern PATTERN_QUESTION_AMP = java.util.regex.Pattern.compile("\\?&");
    private static final java.util.regex.Pattern PATTERN_DOUBLE_AMP = java.util.regex.Pattern.compile("&&+");

    private String limparParametrosAfiliadosAntigos(String url) {
        if (url == null) return "";
        String limpa = PATTERN_MATT_TOOL.matcher(url).replaceAll("$1");
        limpa = PATTERN_MATT_WORD.matcher(limpa).replaceAll("$1");
        limpa = PATTERN_FORCE_IN_APP.matcher(limpa).replaceAll("$1");
        limpa = PATTERN_QUESTION_AMP.matcher(limpa).replaceAll("?");
        limpa = PATTERN_DOUBLE_AMP.matcher(limpa).replaceAll("&");
        if (limpa.endsWith("?") || limpa.endsWith("&")) {
            limpa = limpa.substring(0, limpa.length() - 1);
        }
        return limpa;
    }
}
