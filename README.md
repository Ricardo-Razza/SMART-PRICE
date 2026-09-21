#  SMART PRICE MONITOR

> Sistema inteligente e autônomo de monitoramento de preços, curadoria de ofertas, validação de cupons e publicação automatizada em canais do Telegram com links de afiliado.

---

##  Funcionalidades Principais

- 🤖 **Curadoria Inteligente**: Varredura em 10 nichos de mercado (Smartphones, Games, Hardware, Casa & Eletro, Perfumaria, Fitness, Áudio/TV, Ferramentas, Veículos e Moda) com rotação equilibrada de mais de 180 termos de busca de alta procura.
- 🎯 **Filtro Anti-Miudezas**: Algoritmo de curadoria que descarta capinhas, películas, adesivos e miudezas em buscas de produtos nobres.
- 🎟️ **Crawler & Validação de Cupons**: Busca contínua de cupons de desconto em canais de referência, teste com sonda HTTP no checkout e pareamento semântico automático com ofertas ativas.
- ✍️ **Copywriting & Encurtamento de Links**: Geração de posts no Telegram em formato canônico, limpo e direto (*estilo Herói da Promo*), exibindo links encurtados e seguros de afiliado.
- 🌙 **Modo Noturno Inteligente**: Silenciamento ou pausa automática de postagens de madrugada (23h00 às 07h30, fuso de Brasília) para evitar desinscrições de membros no canal.
- 🐳 **Pronto para Docker**: Multi-stage build com Eclipse Temurin Java 21 e `docker-compose` compatível com VPS Linux (Hostinger KVM) conectando ao MySQL local sem conflitos.
- 🔄 **CI/CD Automatizado via GitHub Actions**: Testes unitários automáticos e deploy contínuo na VPS via SSH em cada push na branch `main`.
- 📱 **Multi-Canal com WhatsApp (Evolution API v2)**: Distribuição simultânea de ofertas e cupons para canais do Telegram e Grupos de WhatsApp com proteção anti-ban e suporte a grupos segmentados por nicho.

---

##  Tecnologias Utilizadas

- **Java 21** & **Spring Boot 4.1.1**
- **Spring Data JPA** & **Hibernate**
- **MySQL 8.0**
- **Google Gemini AI** (`gemini-3.6-flash`)
- **Telegram Bot API**
- **Evolution API v2** (WhatsApp Integration)
- **Docker** & **Docker Compose**
- **GitHub Actions** (CI/CD)
- **SpringDoc OpenAPI / Swagger UI**

---

## 📱 Endpoints da Integração WhatsApp

- `GET /api/whatsapp/status`: Informa se a instância está conectada (`CONNECTED` / `DISCONNECTED`).
- `GET /api/whatsapp/grupos`: Lista todos os grupos onde o chip está presente com nome e JID (`group-id`).
- `POST /api/whatsapp/testar-envio`: Dispara mensagem de teste no grupo padrão ou informado.
- `POST /api/whatsapp/testar-oferta`: Dispara uma oferta real de teste com imagem enquadrada 1:1 e link do Mercado Livre.
- `GET /api/whatsapp/qrcode`: Obtém QR Code / código de conexão da instância.

---

## ⚙️ Como Executar com Docker

### 1. Clonar o Repositório
```bash
git clone https://github.com/Ricardo-Razza/SMART-PRICE.git
cd SMART-PRICE
```

### 2. Configurar Variáveis de Ambiente
Copie o arquivo de exemplo e preencha as suas credenciais:
```bash
cp .env.example .env
nano .env
```

### 3. Iniciar a Aplicação
```bash
docker compose up -d --build
```

### 4. Acompanhar os Logs
```bash
docker compose logs -f app
```

---

## Segurança e Boas Práticas

- **Zero Chaves no Repositório**: Nenhuma chave de API, token de bot ou senha de banco é comitada no código. Todas as credenciais são injetadas estritamente em tempo de execução via arquivo `.env`.
- **Fuso Horário Oficial**: Padronizado para `America/Sao_Paulo` nos containers, garantindo precisão nos agendamentos mesmo em servidores no exterior.

---

##  Licença

Distribuído sob licença proprietária. Todos os direitos reservados.
