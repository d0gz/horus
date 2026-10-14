package io.github.masmangan.assis.pom;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP5 - Agrupamento por categoria e cores.
 *
 * Os testes usam uma tabela declarada em linha, e nao a do repositorio, para que
 * uma futura alteracao de categorias.json nao quebre a suite. A cobertura da
 * tabela real ja e verificada em CategoryTableTest.
 */
class PomMermaidRendererCategoriaTest {

    private static final String TABELA = """
            {
              "schemaVersion": 1,
              "padrao": "nao-classificada",
              "categorias": [
                { "id": "web", "rotulo": "Web e API",
                  "cor": { "preenchimento": "#dbeafe", "borda": "#2563eb", "texto": "#1e3a8a" } },
                { "id": "persistencia", "rotulo": "Persistencia",
                  "cor": { "preenchimento": "#dcfce7", "borda": "#16a34a", "texto": "#14532d" } },
                { "id": "nao-classificada", "rotulo": "Nao classificada",
                  "cor": { "preenchimento": "#ffffff", "borda": "#94a3b8", "texto": "#334155" } }
              ],
              "regras": [
                { "groupId": "org.springframework.boot", "artifactIdPrefixo": "spring-boot-starter-web",
                  "categoria": "web" },
                { "groupId": "org.postgresql", "categoria": "persistencia" },
                { "groupId": "com.h2database", "categoria": "persistencia" }
              ]
            }
            """;

    private String render(String xml) throws Exception {
        Model model = PomDependencyExtractor.readPom(new StringReader(xml));
        return PomMermaidRenderer.render(PomView.de(model), CategoryTable.deTexto(TABELA));
    }

    private static final String POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.exemplo</groupId>
              <artifactId>demo</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
                <dependency>
                  <groupId>com.empresa</groupId>
                  <artifactId>biblioteca-interna</artifactId>
                </dependency>
                <dependency>
                  <groupId>org.postgresql</groupId>
                  <artifactId>postgresql</artifactId>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>com.h2database</groupId>
                  <artifactId>h2</artifactId>
                  <scope>runtime</scope>
                </dependency>
              </dependencies>
            </project>
            """;

    @Test
    @DisplayName("agrupa as dependencias da mesma categoria em um unico subgraph")
    void agrupaPorCategoria() throws Exception {
        String m = render(POM);

        assertTrue(m.contains("subgraph grp_web[\"Web e API\"]"));
        assertTrue(m.contains("subgraph grp_persistencia[\"Persistencia\"]"));
    }

    @Test
    @DisplayName("mantem juntas as dependencias de persistencia declaradas em posicoes diferentes")
    void mantemJuntasAsDaMesmaCategoria() throws Exception {
        String m = render(POM);

        int inicio = m.indexOf("subgraph grp_persistencia");
        int fim = m.indexOf("end", inicio);
        String bloco = m.substring(inicio, fim);

        assertTrue(bloco.contains("dep2"));
        assertTrue(bloco.contains("dep3"));
    }

    @Test
    @DisplayName("categoria sem dependencia nao emite subgraph")
    void categoriaSemDependenciaNaoEmiteSubgraph() throws Exception {
        String m = render("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.postgresql</groupId>
                      <artifactId>postgresql</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertTrue(m.contains("grp_persistencia"));
        assertFalse(m.contains("grp_web"));
    }

    @Test
    @DisplayName("identificadores de subgraph nao contem hifen")
    void identificadoresSaoSanitizados() throws Exception {
        String m = render(POM);

        assertTrue(m.contains("grp_nao_classificada"));
        assertFalse(m.contains("grp_nao-classificada"));
    }

    @Test
    @DisplayName("emite um classDef por categoria usada, com as cores da tabela")
    void emiteClassDefPorCategoriaUsada() throws Exception {
        String m = render(POM);

        assertTrue(m.contains("classDef cor_web fill:#dbeafe,stroke:#2563eb,color:#1e3a8a"));
        assertTrue(m.contains("classDef cor_persistencia fill:#dcfce7,stroke:#16a34a,color:#14532d"));
    }

    @Test
    @DisplayName("associa cada no a classe da sua categoria")
    void associaNosAsClasses() throws Exception {
        String m = render(POM);

        assertTrue(m.contains("class dep0 cor_web"));
        assertTrue(m.contains("class dep2,dep3 cor_persistencia"));
    }

    @Test
    @DisplayName("tabela nao carregada agrupa tudo em nao classificada, sem interromper")
    void tabelaNaoCarregadaAgrupaTudo() throws Exception {
        Model model = PomDependencyExtractor.readPom(new StringReader(POM));
        String m = PomMermaidRenderer.render(PomView.de(model),
                CategoryTable.deTexto("{ json invalido"));

        assertTrue(m.contains("grp_nao_classificada"));
        assertFalse(m.contains("grp_web"));
        assertTrue(m.contains("class dep0,dep1,dep2,dep3 cor_nao_classificada"));
    }

}
