-- Fase 2: qualidade da oferta (cupom em 3 estados, reputação do vendedor, desconto real e score no histórico).

-- Cupom: CONFIRMADO, NAO_VERIFICADO ou INVALIDO. A coluna antiga `testado` fica no banco só para consulta.
ALTER TABLE `cupons` ADD COLUMN `status_validacao` varchar(20) NOT NULL DEFAULT 'NAO_VERIFICADO';

-- O "testado=1" do crawler era muitas vezes aprovação por padrão (timeout, bloqueio). Só vira CONFIRMADO
-- o cupom cadastrado à mão ou confirmado pelo endpoint.
UPDATE `cupons` SET `status_validacao` = 'CONFIRMADO'
WHERE `testado` = 1
  AND (`observacao` IS NULL
       OR `observacao` NOT LIKE '%via crawler%'
       OR `observacao` LIKE '%Confirmado como funcional%');

-- Reputação do vendedor do Mercado Livre, consultada no máximo uma vez a cada 24h.
CREATE TABLE `vendedor` (
  `vendedor_id` varchar(50) NOT NULL,
  `nivel_reputacao` varchar(20) DEFAULT NULL,
  `power_seller` varchar(20) DEFAULT NULL,
  `atualizado_em` datetime(6) NOT NULL,
  PRIMARY KEY (`vendedor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `ofertas_descobertas`
  ADD COLUMN `loja_oficial` bit(1) DEFAULT NULL,
  ADD COLUMN `vendedor_nivel` varchar(20) DEFAULT NULL,
  ADD COLUMN `desconto_real` int DEFAULT NULL;

-- Score de todo candidato avaliado, publicado ou não (base das métricas da Fase 5).
ALTER TABLE `historico_preco` ADD COLUMN `score` int DEFAULT NULL;
