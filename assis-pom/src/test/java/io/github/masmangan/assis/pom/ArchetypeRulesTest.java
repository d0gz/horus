package io.github.masmangan.assis.pom;
import io.github.masmangan.assis.pom.C4Model.Procedencia;
import io.github.masmangan.assis.pom.C4Model.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP9a - Regras de arquetipo.
 *
 * Cada teste fixa uma regra: que evidencia ela exige e que elemento produz. As
 * regras negativas sao tao importantes quanto as positivas, porque um diagrama
 * de conteineres que mostrasse Lombok ou JUnit estaria errado.
 */
class ArchetypeRulesTest {

    private static final String TABELA = """
            {
              "schemaVersion": 1, "padrao": "nao-classificada",
              "categorias": [
                { "id": "web", "rotulo": "Web", "cor": {} },
                { "id": "nao-classificada", "rotulo": "Nao classificada", "cor": {} }
              ],
              "regras": [
                { "groupId": "org.springframework.boot", "artifactIdPrefixo": "spring-boot-starter-web",
                  "categoria": "web" },
                { "groupId": "com.squareup.okhttp3", "categoria": "web" },
                { "groupId": "org.springframework.boot", "artifactId": "spring-boot-starter-validation",
                  "categoria": "web" },
                { "groupId": "org.projectlombok", "artifactId": "lombok", "categoria": "utilitario" },
                { "groupIdPrefixo": "org.springframework", "categoria": "utilitario", "generica": true }
              ]
            }
            """;

    /** Monta um pom com as dependencias informadas no formato grupo:artefato:escopo. */
    private C4Model modelo(String... deps) throws Exception {
        return modelo(java.util.Map.of(), deps);
    }

    private C4Model modelo(java.util.Map<String, Integer> removidas, String... deps) throws Exception {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.0</version>
                  </parent>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties><java.version>21</java.version></properties>
                  <dependencies>
                """);
        for (String d : deps) {
            String[] p = d.split(":");
            sb.append("    <dependency>\n")
                    .append("      <groupId>").append(p[0]).append("</groupId>\n")
                    .append("      <artifactId>").append(p[1]).append("</artifactId>\n");
            if (p.length > 2) {
                sb.append("      <scope>").append(p[2]).append("</scope>\n");
            }
            sb.append("    </dependency>\n");
        }
        sb.append("  </dependencies>\n</project>\n");

        PomView pom = PomView.de(PomDependencyExtractor.readPom(new StringReader(sb.toString())));
        return ArchetypeRules.aplicar(pom, CategoryTable.deTexto(TABELA), removidas);
    }

    private List<String> nomes(C4Model m, Tipo tipo) {
        return m.doTipo(tipo).stream().map(C4Model.Elemento::nome).toList();
    }

    private boolean temRelacao(C4Model m, String origem, String destino, String tecnologia) {
        return m.relacoes().stream().anyMatch(r -> r.origem().equals(origem)
                && r.destino().equals(destino) && tecnologia.equals(r.tecnologia()));
    }

    // ------------------------------------------------------- R1

    @Test
    @DisplayName("R1: o projeto vira o conteiner de aplicacao")
    void projetoViraContainerDeAplicacao() throws Exception {
        C4Model m = modelo();

        assertEquals(1, m.doTipo(Tipo.APLICACAO).size());
        assertEquals("demo", m.porId("app").orElseThrow().nome());
    }

    @Test
    @DisplayName("R1: a tecnologia vem da property java.version e do parent Spring Boot")
    void tecnologiaVemDaPropertyEDoParent() throws Exception {
        assertEquals("Java 21, Spring Boot 3.3.0",
                modelo().porId("app").orElseThrow().tecnologia());
    }

    // ------------------------------------------------------- R2

    @Test
    @DisplayName("R2: dependencia web produz o ator externo")
    void categoriaWebProduzAtor() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web");

        assertEquals(1, m.doTipo(Tipo.PESSOA).size());
        assertTrue(temRelacao(m, "pessoa", "app", "HTTPS"));
    }

    @Test
    @DisplayName("R2: sem dependencia web nao ha ator")
    void semWebNaoHaAtor() throws Exception {
        assertFalse(modelo("org.postgresql:postgresql").temTipo(Tipo.PESSOA));
    }

    @Test
    @DisplayName("R2: duas dependencias web nao duplicam o ator nem a relacao")
    void duasDependenciasWebNaoDuplicamAtor() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web",
                "org.springframework.boot:spring-boot-starter-webflux");

        assertEquals(1, m.doTipo(Tipo.PESSOA).size());
        assertEquals(1, m.relacoes().stream().filter(r -> r.origem().equals("pessoa")).count());
    }

    // ------------------------------------------------------- R3 e R4

    @Test
    @DisplayName("R3: driver JDBC produz conteiner de banco relacional")
    void driverJdbcProduzBanco() throws Exception {
        C4Model m = modelo("org.postgresql:postgresql:runtime");

        assertEquals(List.of("PostgreSQL"), nomes(m, Tipo.BANCO));
        assertTrue(temRelacao(m, "app", "db_postgresql", "JDBC"));
    }

    @Test
    @DisplayName("R4: banco em memoria e marcado como nao confirmavel e nao gera relacao")
    void bancoEmMemoriaEhNaoConfirmavel() throws Exception {
        C4Model m = modelo("com.h2database:h2:runtime");

        C4Model.Elemento h2 = m.doTipo(Tipo.BANCO).get(0);
        assertEquals("H2", h2.nome());
        assertEquals(Procedencia.NAO_CONFIRMAVEL, h2.procedencia());
        assertTrue(m.relacoes().isEmpty());
    }

    @Test
    @DisplayName("R3 e R4 juntos: o caso do FDS produz PostgreSQL derivado e H2 nao confirmavel")
    void casoFdsComDoisBancos() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web",
                "org.springframework.boot:spring-boot-starter-data-jpa",
                "com.h2database:h2:runtime",
                "org.postgresql:postgresql:runtime");

        assertEquals(2, m.doTipo(Tipo.BANCO).size());
        assertEquals(Procedencia.NAO_CONFIRMAVEL, m.porId("db_h2").orElseThrow().procedencia());
        assertEquals(Procedencia.DERIVADO, m.porId("db_postgresql").orElseThrow().procedencia());
    }

    // ------------------------------------------------------- R5, R6 e R7

    @Test
    @DisplayName("R5: JPA sem driver declarado produz banco generico")
    void jpaSemDriverProduzBancoGenerico() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-data-jpa");

        assertEquals(List.of("Banco de dados"), nomes(m, Tipo.BANCO));
        assertEquals("driver nao declarado", m.porId("db_generico").orElseThrow().tecnologia());
    }

    @Test
    @DisplayName("R5: JPA com driver nao produz banco generico adicional")
    void jpaComDriverNaoDuplicaBanco() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-data-jpa",
                "org.postgresql:postgresql:runtime");

        assertEquals(1, m.doTipo(Tipo.BANCO).size());
        assertTrue(m.porId("db_generico").isEmpty());
    }

    @Test
    @DisplayName("R6: data-mongodb produz banco com tecnologia propria")
    void mongodbProduzBancoDedicado() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-data-mongodb");

        assertEquals(List.of("MongoDB"), nomes(m, Tipo.BANCO));
    }

    @Test
    @DisplayName("R7: Flyway nao cria conteiner, apenas anota o banco existente")
    void flywayApenasAnota() throws Exception {
        C4Model m = modelo("org.postgresql:postgresql:runtime", "org.flywaydb:flyway-core");

        assertEquals(1, m.doTipo(Tipo.BANCO).size());
        assertTrue(m.porId("db_postgresql").orElseThrow().descricao().contains("esquema versionado"));
    }

    @Test
    @DisplayName("R7: Flyway sem banco declarado produz o banco generico")
    void flywaySemBancoProduzGenerico() throws Exception {
        C4Model m = modelo("org.flywaydb:flyway-core");

        assertEquals(1, m.doTipo(Tipo.BANCO).size());
        assertTrue(m.porId("db_generico").isPresent());
    }

    // ------------------------------------------------------- R8

    @Test
    @DisplayName("R8: SDK de nuvem produz sistema externo nomeado pelo servico")
    void sdkDeNuvemProduzSistemaExterno() throws Exception {
        C4Model m = modelo("software.amazon.awssdk:rekognition");

        assertEquals(List.of("Rekognition"), nomes(m, Tipo.SISTEMA_EXTERNO));
        assertTrue(temRelacao(m, "app", "ext_rekognition", "SDK/HTTPS"));
    }

    @Test
    @DisplayName("R8: artefato de nucleo do SDK nao designa servico e nao produz elemento")
    void sdkCoreNaoProduzElemento() throws Exception {
        assertFalse(modelo("software.amazon.awssdk:sdk-core").temTipo(Tipo.SISTEMA_EXTERNO));
    }

    // ------------------------------------------------------- R9 a R14

    @Test
    @DisplayName("R9: cliente OAuth2 produz provedor de identidade externo")
    void oauth2ProduzIdentidade() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-oauth2-client");

        assertEquals("Provedor de identidade", m.porId("ext_identidade").orElseThrow().nome());
    }

    @Test
    @DisplayName("R10: cliente Kafka produz conteiner de mensageria")
    void kafkaProduzMensageria() throws Exception {
        C4Model m = modelo("org.springframework.kafka:spring-kafka");

        assertEquals(List.of("Kafka"), nomes(m, Tipo.MENSAGERIA));
    }

    @Test
    @DisplayName("R11: cache local roda no processo e nao produz elemento")
    void cacheLocalNaoProduzElemento() throws Exception {
        C4Model m = modelo("com.github.ben-manes.caffeine:caffeine");

        assertEquals(1, m.elementos().size());
    }

    @Test
    @DisplayName("R12: cliente Eureka produz servico de registro externo")
    void eurekaProduzRegistro() throws Exception {
        C4Model m = modelo("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client");

        assertEquals(1, m.doTipo(Tipo.SISTEMA_EXTERNO).size());
        // O conteiner externo leva o nome do servico, nao o da biblioteca cliente.
        assertEquals("Eureka", m.doTipo(Tipo.SISTEMA_EXTERNO).get(0).nome());
    }

    @Test
    @DisplayName("R13: actuator sozinho apenas anota a aplicacao")
    void actuatorApenasAnota() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-actuator");

        assertFalse(m.temTipo(Tipo.SISTEMA_EXTERNO));
        assertTrue(m.porId("app").orElseThrow().descricao().contains("saude e metricas"));
    }

    @Test
    @DisplayName("R13: registry de metricas produz coletor externo")
    void micrometerRegistryProduzColetor() throws Exception {
        C4Model m = modelo("io.micrometer:micrometer-registry-prometheus");

        assertEquals(1, m.doTipo(Tipo.SISTEMA_EXTERNO).size());
        assertEquals("Prometheus", m.doTipo(Tipo.SISTEMA_EXTERNO).get(0).nome());
    }

    @Test
    @DisplayName("R14: cliente de email produz servidor SMTP externo")
    void emailProduzSmtp() throws Exception {
        assertTrue(modelo("org.springframework.boot:spring-boot-starter-mail")
                .porId("ext_smtp").isPresent());
    }

    // ------------------------------------------------------- R15

    @Test
    @DisplayName("R15: cliente HTTP generico nao produz elemento, produz nota")
    void clienteHttpGenericoProduzNota() throws Exception {
        C4Model m = modelo("org.springframework.cloud:spring-cloud-starter-openfeign");

        assertEquals(1, m.elementos().size());
        assertTrue(m.notas().stream().anyMatch(n -> n.contains("nao sao declarados no Maven")));
    }

    // ------------------------------------------------------- regras negativas

    @Test
    @DisplayName("negativa: dependencia de teste nao produz conteiner")
    void dependenciaDeTesteNaoProduzConteiner() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-test:test",
                "org.testcontainers:postgresql:test");

        assertEquals(1, m.elementos().size());
        assertFalse(m.temTipo(Tipo.BANCO));
    }

    @Test
    @DisplayName("negativa: dependencia provided nao produz conteiner")
    void dependenciaProvidedNaoProduzConteiner() throws Exception {
        assertEquals(1, modelo("org.projectlombok:lombok:provided").elementos().size());
    }

    @Test
    @DisplayName("negativa: utilitario em escopo de execucao tambem nao produz conteiner")
    void utilitarioNaoProduzConteiner() throws Exception {
        assertEquals(1, modelo("org.mapstruct:mapstruct",
                "org.springframework.boot:spring-boot-devtools:runtime").elementos().size());
    }

    // ------------------------------------------------------- procedencia

    @Test
    @DisplayName("todo elemento carrega evidencia rastreavel ate o pom")
    void todoElementoTemEvidencia() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web",
                "org.postgresql:postgresql:runtime");

        for (C4Model.Elemento e : m.elementos()) {
            assertNotNull(e.evidencia(), e.nome());
            assertEquals(C4Model.Fonte.POM, e.evidencia().fonte());
            assertNotNull(e.evidencia().localizador());
        }

        // A evidencia aponta para a coordenada exata que originou o elemento.
        assertEquals("org.postgresql:postgresql",
                m.porId("db_postgresql").orElseThrow().evidencia().localizador());
    }

    // ------------------------------------------------------- casos de referencia

    @Test
    @DisplayName("caso Rekognition: reproduz os quatro elementos da Figura 9.5")
    void casoRekognition() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web",
                "org.springframework.boot:spring-boot-starter-data-jpa",
                "org.postgresql:postgresql:runtime",
                "software.amazon.awssdk:rekognition",
                "software.amazon.awssdk:sdk-core",
                "org.projectlombok:lombok:provided",
                "org.springframework.boot:spring-boot-starter-test:test");

        assertTrue(m.temTipo(Tipo.PESSOA));
        assertTrue(m.temTipo(Tipo.APLICACAO));
        assertEquals(List.of("PostgreSQL"), nomes(m, Tipo.BANCO));
        assertEquals(List.of("Rekognition"), nomes(m, Tipo.SISTEMA_EXTERNO));
        assertEquals(4, m.elementos().size());
    }

    // ------------------------------------------------------- R19 gateway

    @Test
    @DisplayName("R19: starter do gateway produz o ator e a nota de rotas nao declaradas")
    void gatewayProduzAtorENota() throws Exception {
        C4Model m = modelo("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux");

        assertTrue(temRelacao(m, "pessoa", "app", "HTTPS"));
        assertTrue(m.notas().stream().anyMatch(n -> n.contains("gateway")
                && n.contains("rotas nao sao declaradas no pom")));
    }

    // ------------------------------------------------------- R20 interface web

    @Test
    @DisplayName("R20: webjar angularjs produz a interface web dentro da fronteira")
    void webjarAngularjsProduzInterface() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web",
                "org.webjars:angularjs");

        C4Model.Elemento ui = m.porId("ui").orElseThrow();
        assertEquals(Tipo.INTERFACE, ui.tipo());
        assertEquals("AngularJS", ui.tecnologia());
        assertFalse(ui.foraDaFronteira());
    }

    @Test
    @DisplayName("R20: com interface, o ator usa a interface e a interface chama a aplicacao")
    void atorUsaInterfaceQueChamaAplicacao() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web",
                "org.webjars:angularjs");

        assertTrue(temRelacao(m, "pessoa", "ui", "HTTPS"));
        assertTrue(temRelacao(m, "ui", "app", "JSON/HTTP"));
        assertFalse(m.relacoes().stream().anyMatch(r ->
                r.origem().equals("pessoa") && r.destino().equals("app")));
    }

    @Test
    @DisplayName("R20: webjar de apoio (bootstrap) nao produz interface")
    void webjarDeApoioNaoProduzInterface() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-web",
                "org.webjars:bootstrap");

        assertFalse(m.temTipo(Tipo.INTERFACE));
    }

    // ------------------------------------------------------- R2 mais estreito

    @Test
    @DisplayName("R2: cliente HTTP da categoria web (okhttp3) nao cria o ator e vira nota")
    void okhttpNaoCriaAtor() throws Exception {
        C4Model m = modelo("com.squareup.okhttp3:okhttp");

        assertFalse(m.temTipo(Tipo.PESSOA));
        assertTrue(m.notas().stream().anyMatch(n -> n.contains("com.squareup.okhttp3:okhttp")
                && n.contains("nao sao declarados no Maven")));
    }

    @Test
    @DisplayName("R2: validacao da categoria web nao cria o ator")
    void validacaoNaoCriaAtor() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-validation");

        assertFalse(m.temTipo(Tipo.PESSOA));
        assertEquals(1, m.elementos().size());
    }

    // ------------------------------------------------------- nao identificadas (incremento B)

    @Test
    @DisplayName("dependencia desconhecida sem regra e registrada como nao identificada")
    void desconhecidaEhRegistrada() throws Exception {
        C4Model m = modelo("com.empresa:biblioteca-propria");

        assertEquals(1, m.naoIdentificadas().size());
        C4Model.DependenciaNaoIdentificada d = m.naoIdentificadas().get(0);
        assertEquals("com.empresa:biblioteca-propria", d.coordenada());
        assertEquals("nao-classificada", d.categoria());
        assertTrue(d.regraGenerica() == null);
    }

    @Test
    @DisplayName("dependencia conhecida irrelevante (lombok em compile) nao e registrada")
    void lombokNaoEhRegistrado() throws Exception {
        assertTrue(modelo("org.projectlombok:lombok").naoIdentificadas().isEmpty());
    }

    @Test
    @DisplayName("dependencia de regra generica e registrada com o padrao da regra")
    void genericaEhRegistradaComPadrao() throws Exception {
        C4Model m = modelo("org.springframework.boot:spring-boot-starter-cache");

        C4Model.DependenciaNaoIdentificada d = m.naoIdentificadas().get(0);
        assertEquals("utilitario", d.categoria());
        assertEquals("org.springframework*", d.regraGenerica());
    }

    @Test
    @DisplayName("dependencia que produz elemento nao e registrada")
    void dependenciaComRegraNaoEhRegistrada() throws Exception {
        assertTrue(modelo("org.postgresql:postgresql",
                "org.springframework.boot:spring-boot-starter-data-jpa").naoIdentificadas().isEmpty());
    }

    // ------------------------------------------------------- remove dependency (incremento C)

    @Test
    @DisplayName("remove dependency: a dependencia nao entra nas regras nem nas nao identificadas, e fica nos removidos")
    void removeDependencyTiraDasRegras() throws Exception {
        C4Model m = modelo(java.util.Map.of("org.postgresql:postgresql", 3, "com.empresa:lib", 4),
                "org.postgresql:postgresql", "com.empresa:lib");

        assertFalse(m.temTipo(Tipo.BANCO));
        assertTrue(m.naoIdentificadas().isEmpty());
        assertEquals(List.of("dependencia org.postgresql:postgresql (linha 3)", "dependencia com.empresa:lib (linha 4)"),
                m.removidos().stream().map(C4Model.ItemDoCatalogo::texto).toList());
    }

    @Test
    @DisplayName("remove dependency de coordenada que nao esta no pom vira aviso")
    void removeDependencyForaDoPomViraAviso() throws Exception {
        C4Model m = modelo(java.util.Map.of("org.jolokia:jolokia-core", 2), "org.postgresql:postgresql");

        assertTrue(m.temTipo(Tipo.BANCO));
        assertTrue(m.ignoradas().stream().anyMatch(i -> i.linha() == 2
                && i.texto().contains("'org.jolokia:jolokia-core' nao esta no pom")), m.ignoradas().toString());
    }
}
