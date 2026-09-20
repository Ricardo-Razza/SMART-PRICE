# ==========================================
# STAGE 1: Build (Maven + JDK 21)
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Instala dos2unix para corrigir quebras de linha Windows (CRLF) no mvnw
RUN apt-get update && apt-get install -y dos2unix && rm -rf /var/lib/apt/lists/*

# Copia arquivos do Maven Wrapper e POM primeiro para cache de dependências
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Garante permissão de execução e formatação LF no mvnw
RUN dos2unix ./mvnw && chmod +x ./mvnw

# Baixa dependências para cachear na layer do Docker
RUN ./mvnw dependency:go-offline -B || true

# Copia o código fonte e compila o JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# STAGE 2: Runtime (JRE 21 Leve e Seguro)
# ==========================================
FROM eclipse-temurin:21-jre-jammy AS runner

WORKDIR /app

# Configura fuso horário para horário de Brasília (necessário para agendador e modo noturno)
ENV TZ=America/Sao_Paulo
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Cria usuário não-root por segurança
RUN groupadd -r spring && useradd -r -g spring spring

# Copia o artefato compilado
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

USER spring:spring

EXPOSE 8080

# Flags otimizadas para containers e fuso horário
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Duser.timezone=America/Sao_Paulo", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
