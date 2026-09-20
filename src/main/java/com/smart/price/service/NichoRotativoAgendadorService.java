package com.smart.price.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.repository.NichoRepository;

@Service
public class NichoRotativoAgendadorService {

    private static final Logger logger = LoggerFactory.getLogger(NichoRotativoAgendadorService.class);

    private final NichoRepository nichoRepository;
    private final CuradoriaOfertasService curadoriaOfertasService;
    private final NotificadorOfertasService notificadorOfertasService;
    private final ModoNoturnoService modoNoturnoService;

    public NichoRotativoAgendadorService(
            NichoRepository nichoRepository,
            CuradoriaOfertasService curadoriaOfertasService,
            NotificadorOfertasService notificadorOfertasService,
            ModoNoturnoService modoNoturnoService) {
        this.nichoRepository = nichoRepository;
        this.curadoriaOfertasService = curadoriaOfertasService;
        this.notificadorOfertasService = notificadorOfertasService;
        this.modoNoturnoService = modoNoturnoService;
    }

    /**
     * Executa a busca rotativa a cada 15 minutos (configurável via 'monitoramento.nichos.cron').
     */
    @Scheduled(cron = "${monitoramento.nichos.cron:0 0/15 * * * ?}")
    public void rotinaAgendada() {
        logger.info("NichoRotativoAgendador: Iniciando ciclo automático de rotação de nichos...");
        executarProximoCiclo();
    }

    /**
     * Executa o próximo ciclo da fila Round-Robin.
     * Pode ser invocado tanto pelo agendador quanto manualmente via endpoint de teste.
     *
     * @return Lista de ofertas selecionadas e notificadas.
     */
    @Transactional
    public List<OfertaDescoberta> executarProximoCiclo() {
        if (modoNoturnoService != null && modoNoturnoService.devePausarEnvios()) {
            logger.info("NichoRotativoAgendador: Modo Noturno ativo ({} às {}, fuso {}). Envios pausados para respeitar o descanso dos membros.",
                    modoNoturnoService.getHoraInicioStr(),
                    modoNoturnoService.getHoraFimStr(),
                    modoNoturnoService.getFusoHorario());
            return List.of();
        }

        Optional<Nicho> optNicho = nichoRepository.findProximoNichoParaBusca();

        if (optNicho.isEmpty()) {
            logger.warn("NichoRotativoAgendador: Nenhum nicho ativo cadastrado para busca.");
            return List.of();
        }

        Nicho nicho = optNicho.get();
        int cicloAtual = (nicho.getTotalCiclos() != null ? nicho.getTotalCiclos() : 0) + 1;
        nicho.setTotalCiclos(cicloAtual);
        nicho.setDataUltimaBusca(LocalDateTime.now());
        nicho = nichoRepository.save(nicho);

        logger.info("NichoRotativoAgendador: Nicho selecionado para a rodada: '{}' (Ciclo #{}, Última busca: {}).",
                nicho.getNome(),
                cicloAtual,
                nicho.getDataUltimaBusca());

        List<OfertaDescoberta> melhoresOfertas;
        try {
            melhoresOfertas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(nicho);

            if (!melhoresOfertas.isEmpty()) {
                notificadorOfertasService.notificar(nicho, melhoresOfertas);
            } else {
                logger.info("NichoRotativoAgendador: Nenhuma nova oferta encontrada para o nicho '{}' no Ciclo #{}.",
                        nicho.getNome(), cicloAtual);
            }
        } catch (Exception ex) {
            logger.error("NichoRotativoAgendador: Erro ao processar ciclo #{} para o nicho '{}': {}",
                    cicloAtual, nicho.getNome(), ex.getMessage(), ex);
            melhoresOfertas = List.of();
        } finally {
            // Atualiza data_ultima_busca mesmo em caso de erro ou sem ofertas,
            // garantindo que a fila avance para o próximo nicho na próxima rodada!
            nicho.setDataUltimaBusca(LocalDateTime.now());
            nichoRepository.save(nicho);
            logger.info("NichoRotativoAgendador: Nicho '{}' atualizado. Ciclo #{} finalizado.",
                    nicho.getNome(), cicloAtual);
        }

        return melhoresOfertas;
    }
}
