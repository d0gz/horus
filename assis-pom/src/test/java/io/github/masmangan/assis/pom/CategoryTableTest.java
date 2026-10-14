package io.github.masmangan.assis.pom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP4 - Classificacao de dependencias.
 *
 * A tabela de referencia e o arquivo categorias.json, versionado em
 * src/main/resources. Estes testes verificam tanto a semantica de casamento das
 * regras quanto a tolerancia a falhas de carga: uma tabela ausente ou invalida
 * deve degradar para "nao classificada", nunca interromper a execucao.
 */
class CategoryTableTest {

    /** Tabela reduzida, usada nos testes de semantica das regras. */
    private static final String TABELA = """
            {
              "schemaVersion": 1,
              "padrao": "nao-classificada",
              "categorias": [
                { "id": "web", "rotulo": "Web e API",
                  "cor": { "preenchimento": "#dbeafe", "borda": "#2563eb", "texto": "#1e3a8a" } },
                { "id": "persistencia", "rotulo": "Persistencia",
                  "cor": { "preenchimento": "#dcfce7", "borda": "#16a34a", "texto": "#14532d" } },
                { "id": "teste", "rotulo": "Teste",
                  "cor": { "preenchimento": "#f1f5f9", "borda": "#64748b", "texto": "#0f172a" } },
                { "id": "utilitario", "rotulo": "Utilitario",
                  "cor": { "preenchimento": "#fef9c3", "borda": "#ca8a04", "texto": "#713f12" } },
                { "id": "infraestrutura", "rotulo": "Infraestrutura",
                  "cor": { "preenchimento": "#ffedd5", "borda": "#ea580c", "texto": "#7c2d12" } },
                { "id": "nao-classificada", "rotulo": "Nao classificada",
                  "cor": { "preenchimento": "#ffffff", "borda": "#94a3b8", "texto": "#334155" } }
              ],
              "regras": [
                { "groupId": "org.springframework.boot", "artifactId": "spring-boot-starter-test",
                  "categoria": "teste" },
                { "groupId": "org.springframework.boot", "artifactIdPrefixo": "spring-boot-starter-data",
                  "categoria": "persistencia" },
                { "groupId": "org.springframework.boot", "artifactIdPrefixo": "spring-boot-starter-web",
                  "categoria": "web" },
                { "groupId": "org.projectlombok", "artifactId": "lombok",
                  "categoria": "utilitario" },
                { "groupId": "org.postgresql", "artifactId": "postgresql",
                  "categoria": "persistencia" },
                { "groupIdPrefixo": "software.amazon.awssdk",
                  "categoria": "infraestrutura" },
                { "groupIdPrefixo": "org.springframework",
                  "categoria": "utilitario" }
              ]
            }
            """;

    private CategoryTable tabela() {
        return CategoryTable.deTexto(TABELA);
    }

    // ---------------------------------------------------------------
    // Semantica das regras
    // ---------------------------------------------------------------

    @Test
    @DisplayName("classifica starter-data-jpa como Persistencia por prefixo de artifactId")
    void starterDataJpaEhPersistencia() {
        assertEquals("persistencia",
                tabela().categoriaDe("org.springframework.boot", "spring-boot-starter-data-jpa"));
    }

    @Test
    @DisplayName("classifica o driver do PostgreSQL como Persistencia")
    void driverPostgresqlEhPersistencia() {
        assertEquals("persistencia", tabela().categoriaDe("org.postgresql", "postgresql"));
    }

    @Test
    @DisplayName("regra mais especifica vence regra mais geral do mesmo grupo")
    void regraMaisEspecificaVence() {
        // starter-test casaria tanto com a regra especifica de teste quanto com a
        // regra final de org.springframework. A ordem de declaracao decide.
        assertEquals("teste",
                tabela().categoriaDe("org.springframework.boot", "spring-boot-starter-test"));
    }

    @Test
    @DisplayName("casa por prefixo de groupId")
    void casaPorPrefixoDeGroupId() {
        assertEquals("infraestrutura",
                tabela().categoriaDe("software.amazon.awssdk.services", "rekognition"));
    }

    @Test
    @DisplayName("groupId desconhecido resulta em nao classificada, nunca em palpite")
    void groupIdDesconhecidoEhNaoClassificada() {
        assertEquals(CategoryResolver.NAO_CLASSIFICADA,
                tabela().categoriaDe("com.empresa.interna", "biblioteca-propria"));
    }

    @Test
    @DisplayName("nao confunde grupos com prefixo parecido")
    void naoConfundeGruposParecidos() {
        // org.postgresqlx nao deve casar com a regra exata de org.postgresql.
        assertEquals(CategoryResolver.NAO_CLASSIFICADA,
                tabela().categoriaDe("org.postgresqlx", "postgresql"));
    }

    // ---------------------------------------------------------------
    // Metadados das categorias
    // ---------------------------------------------------------------

    @Test
    @DisplayName("expoe rotulo e cores da categoria")
    void expoeRotuloECores() {
        CategoryTable t = tabela();

        assertEquals("Persistencia", t.rotuloDe("persistencia"));
        assertEquals("#dcfce7", t.corDe("persistencia").preenchimento());
        assertEquals("#16a34a", t.corDe("persistencia").borda());
        assertEquals("#14532d", t.corDe("persistencia").texto());
    }

    @Test
    @DisplayName("categoria desconhecida ainda tem rotulo e cor utilizaveis")
    void categoriaDesconhecidaTemMetadadosDeFallback() {
        CategoryTable t = tabela();

        assertNotNull(t.rotuloDe("inexistente"));
        assertNotNull(t.corDe("inexistente"));
    }

    // ---------------------------------------------------------------
    // Tolerancia a falhas de carga
    // ---------------------------------------------------------------

    @Test
    @DisplayName("tabela ausente no classpath nao derruba a execucao")
    void tabelaAusenteNaoDerrubaExecucao() {
        CategoryTable t = CategoryTable.deClasspath("/nao-existe-esta-tabela.json");

        assertFalse(t.carregou());
        assertEquals(CategoryResolver.NAO_CLASSIFICADA,
                t.categoriaDe("org.springframework.boot", "spring-boot-starter-web"));
    }

    @Test
    @DisplayName("tabela malformada nao derruba a execucao")
    void tabelaMalformadaNaoDerrubaExecucao() {
        CategoryTable t = CategoryTable.deTexto("{ isto nao e json valido");

        assertFalse(t.carregou());
        assertEquals(CategoryResolver.NAO_CLASSIFICADA, t.categoriaDe("qualquer", "coisa"));
    }

    @Test
    @DisplayName("schemaVersion incompativel e recusado em vez de interpretado errado")
    void schemaVersionIncompativelEhRecusado() {
        CategoryTable t = CategoryTable.deTexto("""
                { "schemaVersion": 99, "padrao": "nao-classificada",
                  "categorias": [], "regras": [
                    { "groupId": "org.postgresql", "categoria": "persistencia" } ] }
                """);

        assertFalse(t.carregou());
        assertEquals(CategoryResolver.NAO_CLASSIFICADA,
                t.categoriaDe("org.postgresql", "postgresql"));
    }

    // ---------------------------------------------------------------
    // Tabela real do repositorio
    // ---------------------------------------------------------------

    @Test
    @DisplayName("a tabela do repositorio classifica todas as dependencias do pom FDS")
    void tabelaDoRepositorioClassificaTodoOPomFds() {
        CategoryTable t = CategoryTable.padrao();

        String[][] deps = {
                {"org.springframework.boot", "spring-boot-starter-web"},
                {"org.projectlombok", "lombok"},
                {"org.springframework.boot", "spring-boot-devtools"},
                {"org.springframework.boot", "spring-boot-starter-test"},
                {"org.springframework.boot", "spring-boot-starter-data-jpa"},
                {"com.h2database", "h2"},
                {"org.postgresql", "postgresql"},
                {"org.springframework.boot", "spring-boot-starter-validation"},
        };

        for (String[] d : deps) {
            assertNotEquals(CategoryResolver.NAO_CLASSIFICADA, t.categoriaDe(d[0], d[1]),
                    d[0] + ":" + d[1] + " deveria estar coberta pela tabela");
        }
    }
}
