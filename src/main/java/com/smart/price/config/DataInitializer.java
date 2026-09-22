package com.smart.price.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.TermoBusca;
import com.smart.price.repository.CupomRepository;
import com.smart.price.repository.NichoRepository;
import com.smart.price.repository.TermoBuscaRepository;

/**
 * Inicializador que configura os nichos oficiais e povoa uma vasta esteira de termos de busca
 * de alta procura (produtos e marcas mais desejados no Brasil) para rotação autônoma (round-robin).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${telegram.bot.chat-id-tech:}")
    private String telegramChatIdTech;

    private final NichoRepository nichoRepository;
    private final CupomRepository cupomRepository;
    private final TermoBuscaRepository termoBuscaRepository;

    public DataInitializer(
            NichoRepository nichoRepository,
            CupomRepository cupomRepository,
            TermoBuscaRepository termoBuscaRepository) {
        this.nichoRepository = nichoRepository;
        this.cupomRepository = cupomRepository;
        this.termoBuscaRepository = termoBuscaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        logger.info("DataInitializer: Inicializando e sincronizando nichos e catálogo amplo de termos de busca...");

        // 1. SMARTPHONES & WEARABLES
        sincronizarNichoComTermos("SMARTPHONES & WEARABLES", "MLB1051,MLB1055,MLB417704", null, List.of(
                "iphone",
                "smartphone samsung",
                "smartphone xiaomi",
                "smartphone poco",
                "motorola edge",
                "smartwatch samsung",
                "apple watch",
                "smartwatch amazfit",
                "fone bluetooth jbl",
                "fone cancelamento de ruido",
                "fone sem fio xiaomi redmi",
                "tablet samsung galaxy",
                "ipad",
                "tablet xiaomi pad",
                "kindle amazon",
                "carregador anker 20w",
                "carregador rapido 65w gan",
                "carregador por inducao",
                "power bank inducao",
                "suporte veicular magsafe"
        ));

        // 2. GAMES & CONSOLES
        sincronizarNichoComTermos("GAMES & CONSOLES", "MLB1144", null, List.of(
                "console playstation 5",
                "playstation 5 slim",
                "nintendo switch oled",
                "xbox series s",
                "xbox series x",
                "steam deck",
                "asus rog ally",
                "controle ps5 dualsense",
                "controle xbox sem fio",
                "controle sem fio 8bitdo",
                "jogos ps5 midia fisica",
                "jogos nintendo switch",
                "headset gamer hyperx",
                "headset wireless ps5 pc",
                "volante logitech g29",
                "cadeira gamer ergonomica",
                "cartao memoria nintendo switch 128gb",
                "base carregadora controle ps5",
                "placa de captura de video 4k"
        ));

        // 3. HARDWARE & INFORMÁTICA
        sincronizarNichoComTermos("HARDWARE & INFORMÁTICA", "MLB1648,MLB454379,MLB1712,MLB14370", null, List.of(
                "ssd nvme 1tb",
                "ssd 2tb nvme pcie 4.0",
                "memoria ram ddr4 16gb",
                "memoria ram ddr5 32gb",
                "placa de video rtx 4060",
                "placa de video rtx 4070",
                "placa de video radeon rx",
                "processador ryzen 5",
                "processador ryzen 7",
                "processador intel core i5",
                "notebook gamer dell acer lenovo",
                "notebook para trabalho e estudos",
                "monitor gamer 144hz",
                "monitor 240hz fast ips",
                "suporte articulado para monitor f80n",
                "teclado mecanico switch red",
                "mouse gamer sem fio logitech",
                "mousepad extra grande speed 90x40",
                "water cooler 240mm",
                "fonte modular 650w bronze gold",
                "gabinete aquario gamer",
                "roteador wifi 6 gigabit mesh",
                "hub adaptador usb-c 7 em 1 hdmi"
        ));

        // 4. CASA INTELIGENTE & ELETRO
        sincronizarNichoComTermos("CASA INTELIGENTE & ELETRO", "MLB5726,MLB438284", null, List.of(
                "air fryer mondial philips",
                "fritadeira sem oleo digital inox",
                "robo aspirador wap xiaomi",
                "aspirador de po vertical sem fio",
                "alexa echo dot",
                "alexa echo show com tela",
                "fechadura digital biometrica",
                "lampada smart wifi alexa",
                "cafeteira espresso eletrica",
                "cafeteira nespresso dolce gusto",
                "panela de pressao eletrica 5l",
                "panela de arroz eletrica",
                "micro-ondas espelhado 30l",
                "ventilador turbo silencioso",
                "purificador de agua refrigerado",
                "liquidificador potente 1200w inox",
                "passadeira a vapor portatil steamer",
                "mop giratorio com balde lava e seca",
                "sanduicheira grill antiaderente",
                "balanca digital de cozinha alta precisao"
        ));

        // 5. PERFUMARIA & COSMÉTICOS
        sincronizarNichoComTermos("PERFUMARIA & COSMÉTICOS", "MLB1271,MLB1246", null, List.of(
                "perfume importado masculino",
                "perfume importado feminino",
                "perfume malbec o boticario",
                "perfume 212 carolina herrera",
                "perfume la vie est belle lancome",
                "perfume silver scent",
                "oleo extraordinario elseve loreal",
                "oleo capilar wella oil reflections",
                "kit shampoo e mascara loreal professionnel",
                "mascara hidratacao capilar lola cosmeticos",
                "tonico capilar antiqueda crescimento",
                "protetor solar facial la roche posay",
                "cerave gel de limpeza facial",
                "serum facial vitamina c clareador",
                "creme hidratante neutrogena hydro boost",
                "escova secadora rotativa alisadora",
                "modelador de cachos babyliss automatico",
                "maquina de cortar cabelo philips wahl",
                "aparador de pelos corporais bodygroom philips"
        ));

        // 6. ESPORTES & FITNESS
        sincronizarNichoComTermos("ESPORTES & FITNESS", "MLB1276", null, List.of(
                "whey protein concentrado 100%",
                "whey protein isolado",
                "creatina monohidratada 100% pura",
                "creatina creapure selo de pureza",
                "pre treino furan diabo verde c4",
                "barra de proteina caixa com 12",
                "pasta de amendoim integral crocante",
                "coqueteleira eletrica mixer shake",
                "tenis corrida masculino amortecimento",
                "tenis corrida feminino olympikus nike",
                "smartband fitness miband fitbit",
                "colchonete tapete eva yoga pilates",
                "kit elasticos extensores super band crossfit",
                "par de halteres emborrachados musculacao",
                "corda de pular profissional com rolamento",
                "strap musculacao com munhequeira",
                "garrafa termica inox agua academia 1l",
                "mochila de hidratacao ciclismo corrida"
        ));

        // 7. ÁUDIO, TV & VÍDEO
        sincronizarNichoComTermos("ÁUDIO, TV & VÍDEO", "MLB1000,MLB1002", null, List.of(
                "smart tv 50 polegadas 4k",
                "smart tv 65 polegadas 4k qled oled",
                "soundbar bluetooth subwoofer tv",
                "caixa de som jbl bluetooth a prova dagua",
                "caixa de som boombox jbl",
                "fire tv stick 4k amazon",
                "roku express streaming player",
                "projetor smart wifi portatil cinema em casa",
                "microfone condensador usb podcast gravacao"
        ));

        // 8. FERRAMENTAS & CONSTRUÇÃO
        sincronizarNichoComTermos("FERRAMENTAS & CONSTRUÇÃO", "MLB1499", null, List.of(
                "parafusadeira furadeira sem fio bateria 12v 20v",
                "maleta de ferramentas jogo completo chave catraca",
                "esmerilhadeira angular 4 1/2 bosch dewalt",
                "lavadora de alta pressao wap karcher",
                "trena a laser digital medidor de distancia",
                "nivel a laser autonivelante 360",
                "jogo de brocas e bits philips fenda",
                "compressor de ar portatil para pneu carro moto",
                "aspirador de po automotivo 12v"
        ));

        // 9. ACESSÓRIOS PARA VEÍCULOS
        sincronizarNichoComTermos("ACESSÓRIOS PARA VEÍCULOS", "MLB1747", null, List.of(
                "camera veicular frontal re dvr",
                "central multimidia android carplay",
                "carregador veicular rapido turbo usb c",
                "sensor de re estacionamento display led",
                "suporte celular veicular trava automatica",
                "kit lampadas led farol h4 h7 automotivo",
                "politriz automotiva lixadeira roto orbital",
                "rastreador veicular gps bloqueador"
        ));

        // 10. MODA & CALÇADOS
        sincronizarNichoComTermos("MODA & CALÇADOS", "MLB1430", null, List.of(
                "tenis casual masculino couro branco",
                "tenis casual feminino plataforma",
                "mochila antifurto para notebook impermeavel",
                "relogio masculino pulso inox esportivo",
                "bolsa feminina tiracolo transversal",
                "carteira masculina couro legitimo antifurto",
                "jaqueta corta vento impermeavel capuz",
                "chinelo slide nuvem ortopedico"
        ));

        // 11. TECH & COMPUTADORES (Focado exclusivamente em informática, computadores e tecnologia de alto giro)
        sincronizarNichoComTermos("TECH & COMPUTADORES", "MLB1648,MLB454379,MLB1712,MLB14370,MLB1051,MLB1055,MLB1000,MLB1144",
                (telegramChatIdTech != null && !telegramChatIdTech.isBlank()) ? telegramChatIdTech.trim() : null,
                List.of(
                        "notebook gamer rtx",
                        "notebook lenovo thinkpad dell",
                        "macbook air m2 m3",
                        "placa de video rtx 4060",
                        "placa de video rtx 4070 ti",
                        "processador ryzen 5 7600",
                        "processador intel core i5 i7",
                        "monitor gamer 144hz fast ips",
                        "monitor ultrawide lg dell",
                        "ssd nvme 1tb pcie 4.0",
                        "ssd nvme 2tb kingston samsung",
                        "memoria ram ddr5 32gb",
                        "teclado mecanico gamer sem fio",
                        "mouse gamer sem fio logitech g pro",
                        "headset gamer sem fio 7.1",
                        "smart tv 55 polegadas 4k 120hz",
                        "iphone 15 pro max",
                        "samsung galaxy s24 ultra",
                        "playstation 5 slim midia fisica",
                        "nintendo switch oled",
                        "gabinete gamer aquario com fans",
                        "fonte 750w 80 plus gold modular",
                        "water cooler 360mm rgb"
                ));

        // Remove cupons de exemplo/mock
        limparCuponsExemplo();

        logger.info("DataInitializer: Nichos e mais de 200 termos de busca de alta procura configurados para mineração contínua.");
    }

    private void sincronizarNichoComTermos(String nomeNicho, String categoriaMlb, String telegramChatId, List<String> termosDesejados) {
        Nicho nicho = nichoRepository.findByNomeIgnoreCase(nomeNicho)
                .orElseGet(() -> new Nicho(nomeNicho, categoriaMlb, telegramChatId));

        nicho.setCategoriaMlb(categoriaMlb);
        if (telegramChatId != null && !telegramChatId.isBlank()) {
            nicho.setTelegramChatId(telegramChatId);
        }
        if (nicho.getAtivo() == null) {
            nicho.setAtivo(true);
        }
        nicho = nichoRepository.save(nicho);

        int novosTermos = 0;
        for (String termo : termosDesejados) {
            String termoLimpo = termo.trim();
            if (termoLimpo.isEmpty()) continue;
            if (termoBuscaRepository.findByNichoAndTermoIgnoreCase(nicho, termoLimpo).isEmpty()) {
                termoBuscaRepository.save(new TermoBusca(nicho, termoLimpo));
                novosTermos++;
            }
        }

        logger.info("DataInitializer: Nicho [{}] pronto (Categoria MLB: {}, Telegram: {}, {} termos ativos, {} novos cadastrados).",
                nomeNicho, categoriaMlb, nicho.getTelegramChatId() != null ? nicho.getTelegramChatId() : "PADRÃO GERAL",
                termosDesejados.size(), novosTermos);
    }

    private void limparCuponsExemplo() {
        List<String> mocks = List.of("VALE15", "TECH50", "MELI10");
        for (String mock : mocks) {
            cupomRepository.findByCodigoIgnoreCase(mock).ifPresent(c -> {
                cupomRepository.delete(c);
                logger.info("DataInitializer: Cupom mock [{}] removido com sucesso para evitar anúncios falsos.", mock);
            });
        }
    }
}
