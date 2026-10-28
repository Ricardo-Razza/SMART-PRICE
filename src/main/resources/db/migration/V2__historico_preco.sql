-- Fase 2: histórico real de preço de todo candidato visto em cada ciclo.

CREATE TABLE `historico_preco` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `produto_id` varchar(50) NOT NULL,
  `loja` enum('AMAZON','KABUM','MERCADO_LIVRE','SHOPEE') NOT NULL,
  `preco` decimal(19,2) NOT NULL,
  `preco_original` decimal(19,2) DEFAULT NULL,
  `vendedor_id` varchar(50) DEFAULT NULL,
  `data_coleta` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_historico_produto_loja_data` (`produto_id`, `loja`, `data_coleta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `ofertas_descobertas` ADD COLUMN `vendedor_id` varchar(50) DEFAULT NULL;
