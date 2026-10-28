-- Schema em produção antes do Flyway (gerado pelo antigo ddl-auto: update).
-- Bancos que já existem pulam esta migração via baseline-on-migrate (baseline-version 1).

CREATE TABLE `nichos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ativo` bit(1) NOT NULL,
  `total_ciclos` int DEFAULT NULL,
  `data_ultima_busca` datetime(6) DEFAULT NULL,
  `nome` varchar(100) NOT NULL,
  `telegram_chat_id` varchar(100) DEFAULT NULL,
  `whatsapp_group_id` varchar(100) DEFAULT NULL,
  `categoria_mlb` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `termos_busca` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ativo` bit(1) NOT NULL,
  `total_buscas` int DEFAULT NULL,
  `data_ultima_busca` datetime(6) DEFAULT NULL,
  `nicho_id` bigint NOT NULL,
  `termo` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_termos_busca_nicho` (`nicho_id`),
  CONSTRAINT `fk_termos_busca_nicho` FOREIGN KEY (`nicho_id`) REFERENCES `nichos` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `cupons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anunciado_avulso` bit(1) DEFAULT NULL,
  `ativo` bit(1) NOT NULL,
  `testado` tinyint(1) DEFAULT '1',
  `valor_desconto` decimal(19,2) NOT NULL,
  `valor_minimo_compra` decimal(19,2) DEFAULT NULL,
  `data_criacao` datetime(6) NOT NULL,
  `data_expiracao` datetime(6) DEFAULT NULL,
  `data_inicio` datetime(6) DEFAULT NULL,
  `nicho_id` bigint DEFAULT NULL,
  `tipo_desconto` varchar(20) NOT NULL,
  `codigo` varchar(50) NOT NULL,
  `observacao` varchar(500) DEFAULT NULL,
  `link_hotsite` varchar(2048) DEFAULT NULL,
  `descricao` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_cupons_nicho` (`nicho_id`),
  CONSTRAINT `fk_cupons_nicho` FOREIGN KEY (`nicho_id`) REFERENCES `nichos` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ofertas_descobertas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ciclo_nicho` int DEFAULT NULL,
  `desconto_cupom` decimal(19,2) DEFAULT NULL,
  `desconto_percentual` int DEFAULT NULL,
  `disponivel` bit(1) DEFAULT NULL,
  `frete_gratis` bit(1) DEFAULT NULL,
  `menor_preco_historico` bit(1) DEFAULT NULL,
  `preco` decimal(19,2) NOT NULL,
  `preco_com_cupom` decimal(19,2) DEFAULT NULL,
  `preco_original` decimal(19,2) DEFAULT NULL,
  `score_qualidade` int DEFAULT NULL,
  `data_descoberta` datetime(6) NOT NULL,
  `data_envio_whatsapp` datetime(6) DEFAULT NULL,
  `nicho_id` bigint DEFAULT NULL,
  `telegram_message_id` bigint DEFAULT NULL,
  `status_envio` varchar(20) NOT NULL,
  `cupom_aplicado` varchar(50) DEFAULT NULL,
  `mlb_id` varchar(50) DEFAULT NULL,
  `subcategoria` varchar(100) DEFAULT NULL,
  `telegram_chat_id` varchar(100) DEFAULT NULL,
  `termo_origem` varchar(200) DEFAULT NULL,
  `titulo` varchar(500) NOT NULL,
  `foto_url` varchar(1024) DEFAULT NULL,
  `url` varchar(2048) NOT NULL,
  `copy_ia` text,
  `loja` enum('AMAZON','KABUM','MERCADO_LIVRE','SHOPEE') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ofertas_nicho` (`nicho_id`),
  CONSTRAINT `fk_ofertas_nicho` FOREIGN KEY (`nicho_id`) REFERENCES `nichos` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
