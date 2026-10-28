-- Fase 1: um registro por envio em cada canal, no lugar do status gravado na própria oferta.

CREATE TABLE `envio` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `oferta_id` bigint DEFAULT NULL,
  `canal` varchar(20) NOT NULL,
  `destino` varchar(100) NOT NULL,
  `status` varchar(20) NOT NULL,
  `message_id` varchar(100) DEFAULT NULL,
  `erro` varchar(500) DEFAULT NULL,
  `data_envio` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_envio_oferta` (`oferta_id`),
  KEY `idx_envio_canal_status_data` (`canal`, `status`, `data_envio`),
  CONSTRAINT `fk_envio_oferta` FOREIGN KEY (`oferta_id`) REFERENCES `ofertas_descobertas` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Leva para a nova tabela os posts do Telegram já publicados (necessário para editar posts esgotados).
INSERT INTO `envio` (`oferta_id`, `canal`, `destino`, `status`, `message_id`, `data_envio`)
SELECT `id`, 'TELEGRAM', `telegram_chat_id`, 'ENVIADO', CAST(`telegram_message_id` AS CHAR),
       COALESCE(`data_envio_whatsapp`, `data_descoberta`)
FROM `ofertas_descobertas`
WHERE `telegram_message_id` IS NOT NULL AND `telegram_chat_id` IS NOT NULL;

-- Colunas antigas ficam só para consulta; o app não grava mais nelas.
ALTER TABLE `ofertas_descobertas` MODIFY `status_envio` varchar(20) NULL;

-- Estado que vivia em memória e zerava a cada deploy.
CREATE TABLE `produto_sem_vendedor` (
  `produto_id` varchar(50) NOT NULL,
  `data_registro` datetime(6) NOT NULL,
  PRIMARY KEY (`produto_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `estado_sistema` (
  `chave` varchar(100) NOT NULL,
  `valor` varchar(500) DEFAULT NULL,
  `atualizado_em` datetime(6) NOT NULL,
  PRIMARY KEY (`chave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
