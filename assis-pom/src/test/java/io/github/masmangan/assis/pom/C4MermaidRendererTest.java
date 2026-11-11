package io.github.masmangan.assis.pom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP9b - Renderizacao do diagrama de conteineres.
 *
 * Os testes exercitam o renderizador a partir de modelos derivados de poms
 * reduzidos, para que cada asserção trate de uma convencao de desenho e nao da
 * regra de arquetipo que a produziu, ja coberta em ArchetypeRulesTest.
 */
class C4MermaidRendererTest {

    private static final String TABELA = """
            {
              "schemaVersion": 1, "padrao": "nao-classificada",
              "categorias": [
                { "id": "web", "rotulo": "Web", "cor": {} },
                { "id": "nao-classificada", "rotulo": "Nao classificada", "cor": {} }
              ],
              "regras": [
                { "groupId": "org.springframework.boot", "artifactIdPrefixo": "spring-boot-starter-web",
                  "categoria": "web" }
              ]
            }
            """;

    private String render(String... deps) throws Exception {
        return C4MermaidRenderer.render(modelo(deps));
    }

    private C4Model modelo(String... deps) throws Exception {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.0</version>
                  </parent>
                  <groupId>com.fds</groupId>
                  <artifactId>sist-vendas</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
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
        return ArchetypeRules.aplicar(pom, CategoryTable.deTexto(TABELA));
    }

    // ------------------------------------------------------- fronteira

    @Test
    @DisplayName("emite a fronteira do sistema como subgraph nomeado pelo projeto")
    void emiteFronteiraComoSubgraph() throws Exception {
        String m = render("org.postgresql:postgresql:runtime");

        assertTrue(m.startsWith("flowchart TB"));
        assertTrue(m.contains("subgraph sys[\"sist-vendas\"]"));
    }

    @Test
    @DisplayName("o ator e o sistema externo ficam fora da fronteira do sistema")
    void atorESistemaExternoFicamFora() throws Exception {
        String m = render("org.springframework.boot:spring-boot-starter-web",
                "software.amazon.awssdk:rekognition");

        int fronteira = m.indexOf("subgraph sys");
        assertTrue(m.indexOf("pessoa(") < fronteira);
        assertTrue(m.indexOf("ext_rekognition[") < fronteira);
    }

    @Test
    @DisplayName("a aplicacao e o banco ficam dentro da fronteira")
    void aplicacaoEBancoFicamDentro() throws Exception {
        String m = render("org.postgresql:postgresql:runtime");

        int abre = m.indexOf("subgraph sys");
        // Procura a linha de fechamento, e nao a substring "end": ela aparece
        // tambem dentro do nome do projeto (sist-vendas).
        int fecha = m.indexOf("\n    end\n", abre);
        String dentro = m.substring(abre, fecha);

        assertTrue(dentro.contains("app["));
        assertTrue(dentro.contains("db_postgresql[("));
    }

    // ------------------------------------------------------- formas e cores

    @Test
    @DisplayName("cada tipo de elemento tem forma propria, nao apenas cor")
    void cadaTipoTemFormaPropria() throws Exception {
        String m = render("org.springframework.boot:spring-boot-starter-web",
                "org.postgresql:postgresql:runtime",
                "org.springframework.kafka:spring-kafka",
                "software.amazon.awssdk:rekognition");

        assertTrue(m.contains("pessoa([\""), "ator em forma arredondada");
        assertTrue(m.contains("db_postgresql[(\""), "banco em forma de cilindro");
        assertTrue(m.contains("mq_kafka{{\""), "mensageria em forma hexagonal");
        assertTrue(m.contains("app[\""), "aplicacao em retangulo");
    }

    @Test
    @DisplayName("associa cada elemento a uma classe de estilo")
    void associaElementosAsClasses() throws Exception {
        String m = render("org.springframework.boot:spring-boot-starter-web",
                "org.postgresql:postgresql:runtime");

        assertTrue(m.contains("classDef pessoa "));
        assertTrue(m.contains("class pessoa pessoa"));
        assertTrue(m.contains("class app aplicacao"));
        assertTrue(m.contains("class db_postgresql banco"));
    }

    // ------------------------------------------------------- rotulos

    @Test
    @DisplayName("a tecnologia aparece entre colchetes no elemento e na relacao")
    void tecnologiaEntreColchetes() throws Exception {
        String m = render("org.postgresql:postgresql:runtime");

        assertTrue(m.contains("[Java 21, Spring Boot 3.3.0]"));
        assertTrue(m.contains("app -->|\"Le e grava dados<br/>[JDBC]\"| db_postgresql"));
    }

    @Test
    @DisplayName("o elemento nao confirmavel recebe marcador textual, nao so cor")
    void naoConfirmavelRecebeMarcadorTextual() throws Exception {
        String m = render("com.h2database:h2:runtime");

        assertTrue(m.contains("H2 (nao confirmado)"));
        assertTrue(m.contains("class db_h2 incerto"));
    }

    @Test
    @DisplayName("escapa aspas e sinal de igual nos rotulos")
    void escapaCaracteresReservados() {
        assertEquals("a#61;b", C4MermaidRenderer.esc("a=b"));
        assertEquals("diz #quot;ola#quot;", C4MermaidRenderer.esc("diz \"ola\""));
    }

    // ------------------------------------------------------- notas

    @Test
    @DisplayName("as notas sao emitidas como comentario do Mermaid")
    void notasViramComentario() throws Exception {
        String m = render("org.springframework.cloud:spring-cloud-starter-openfeign");

        assertTrue(m.contains("%% a aplicacao usa cliente HTTP"));
    }

    // ------------------------------------------------------- casos de referencia

    // ------------------------------------------------------- incremento A

    @Test
    @DisplayName("a interface web tem forma propria, diferente da aplicacao")
    void interfaceTemFormaPropria() throws Exception {
        String m = render("org.springframework.boot:spring-boot-starter-web", "org.webjars:angularjs");

        assertTrue(m.contains("ui(\""), "interface em retangulo arredondado");
        assertTrue(m.contains("app[\""), "aplicacao continua em retangulo");
        assertTrue(m.contains("class ui interface"));
    }

    @Test
    @DisplayName("relacao nao confirmada e tracejada e tem marcador textual")
    void relacaoNaoConfirmadaTracejadaComMarcador() {
        C4Model.Evidencia ev = new C4Model.Evidencia(C4Model.Fonte.POM, "g:a");
        C4Model modelo = C4Model.comNome("sistema")
                .elemento(new C4Model.Elemento(C4Model.Tipo.APLICACAO, "a", "A", null, null,
                        C4Model.Procedencia.DERIVADO, ev))
                .elemento(new C4Model.Elemento(C4Model.Tipo.APLICACAO, "b", "B", null, null,
                        C4Model.Procedencia.DERIVADO, ev))
                .relacao(new C4Model.Relacao("a", "b", "HTTP", "Roteia requisicoes",
                        C4Model.Procedencia.NAO_CONFIRMAVEL, ev))
                .construir();

        String m = C4MermaidRenderer.render(modelo);

        assertTrue(m.contains("a -.->|\"Roteia requisicoes (nao confirmado)<br/>[HTTP]\"| b"), m);
    }

}
