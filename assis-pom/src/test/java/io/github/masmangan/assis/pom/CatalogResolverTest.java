package io.github.masmangan.assis.pom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP8 - Catalogo do usuario.
 *
 * O catalogo e um arquivo de texto do usuario, lido a cada execucao e nunca
 * gravado no categorias.json. Ele tem precedencia absoluta sobre a tabela para
 * as dependencias que lista.
 */
class CatalogResolverTest {

    @TempDir
    Path dir;

    private static final String TABELA = """
            {
              "schemaVersion": 1,
              "padrao": "nao-classificada",
              "categorias": [
                { "id": "persistencia", "rotulo": "Persistencia",
                  "cor": { "preenchimento": "#dcfce7", "borda": "#16a34a", "texto": "#14532d" } },
                { "id": "teste", "rotulo": "Teste",
                  "cor": { "preenchimento": "#f1f5f9", "borda": "#64748b", "texto": "#0f172a" } },
                { "id": "nao-classificada", "rotulo": "Nao classificada",
                  "cor": { "preenchimento": "#ffffff", "borda": "#94a3b8", "texto": "#334155" } }
              ],
              "regras": [
                { "groupId": "org.postgresql", "categoria": "persistencia" }
              ]
            }
            """;

    private CategoryTable base() {
        return CategoryTable.deTexto(TABELA);
    }

    private CatalogResolver catalogo(String texto) {
        return CatalogResolver.deTexto(base(), texto);
    }

    // ---------------------------------------------------------------
    // Precedencia
    // ---------------------------------------------------------------

    @Test
    @DisplayName("classifica dependencia que a tabela desconhece")
    void classificaDependenciaDesconhecida() {
        CatalogResolver c = catalogo("com.empresa:core-interno | Dominio");

        assertEquals("dominio", c.categoriaDe("com.empresa", "core-interno"));
        assertEquals("Dominio", c.rotuloDe("dominio"));
    }

    @Test
    @DisplayName("sobrescreve a classificacao da tabela para a mesma dependencia")
    void sobrescreveClassificacaoDaTabela() {
        assertEquals("persistencia", base().categoriaDe("org.postgresql", "postgresql"));

        CatalogResolver c = catalogo("org.postgresql:postgresql | Infraestrutura");

        assertEquals("infraestrutura", c.categoriaDe("org.postgresql", "postgresql"));
    }

    @Test
    @DisplayName("delega ao resolvedor base as dependencias fora do catalogo")
    void delegaAoBase() {
        CatalogResolver c = catalogo("com.empresa:core-interno | Dominio");

        assertEquals("persistencia", c.categoriaDe("org.postgresql", "postgresql"));
        assertEquals(CategoryResolver.NAO_CLASSIFICADA, c.categoriaDe("com.outra", "coisa"));
    }

    // ---------------------------------------------------------------
    // Cor
    // ---------------------------------------------------------------

    @Test
    @DisplayName("cor em branco preserva a cor original da categoria")
    void corEmBrancoPreservaOriginal() {
        CatalogResolver c = catalogo("org.postgresql:postgresql | persistencia |  | driver");

        assertEquals("#dcfce7", c.corDe("persistencia").preenchimento());
    }

    @Test
    @DisplayName("cor informada sobrescreve o preenchimento e mantem borda e texto")
    void corInformadaSobrescrevePreenchimento() {
        CatalogResolver c = catalogo("org.postgresql:postgresql | persistencia | #ff0000");

        CategoryTable.Cor cor = c.corDe("persistencia");
        assertEquals("#ff0000", cor.preenchimento());
        assertEquals("#16a34a", cor.borda());
        assertEquals("#14532d", cor.texto());
    }

    @Test
    @DisplayName("categoria nova sem cor recebe a cor neutra")
    void categoriaNovaSemCorRecebeNeutra() {
        CatalogResolver c = catalogo("com.empresa:core | Dominio");

        assertNotNull(c.corDe("dominio"));
        assertEquals("#ffffff", c.corDe("dominio").preenchimento());
    }

    @Test
    @DisplayName("cor em formato invalido e ignorada, o resto da linha vale")
    void corInvalidaEhIgnorada() {
        CatalogResolver c = catalogo("com.empresa:core | Dominio | vermelho | desc");

        assertEquals("dominio", c.categoriaDe("com.empresa", "core"));
        assertEquals("#ffffff", c.corDe("dominio").preenchimento());
        assertTrue(c.avisos().stream().anyMatch(a -> a.contains("vermelho")));
    }

    // ---------------------------------------------------------------
    // Rotulo e descricao
    // ---------------------------------------------------------------

    @Test
    @DisplayName("nao sobrescreve o rotulo de categoria ja definida no categorias.json")
    void naoSobrescreveRotuloExistente() {
        CatalogResolver c = catalogo("com.empresa:core | teste");

        assertEquals("Teste", c.rotuloDe("teste"));
    }

    @Test
    @DisplayName("o identificador da categoria ignora maiusculas e minusculas")
    void identificadorIgnoraCaixa() {
        CatalogResolver c = catalogo("com.empresa:core | TESTE");

        assertEquals("teste", c.categoriaDe("com.empresa", "core"));
    }

    @Test
    @DisplayName("guarda a descricao informada pelo usuario")
    void guardaDescricao() {
        CatalogResolver c = catalogo("com.empresa:core | Dominio | #e0f2fe | Biblioteca compartilhada");

        assertEquals("Biblioteca compartilhada",
                c.descricaoDe("com.empresa", "core").orElseThrow());
        assertTrue(c.descricaoDe("org.postgresql", "postgresql").isEmpty());
    }

    // ---------------------------------------------------------------
    // Robustez
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ignora linhas em branco e comentarios")
    void ignoraBrancosEComentarios() {
        CatalogResolver c = catalogo("""
                # catalogo da equipe

                com.empresa:core | Dominio

                # fim
                """);

        assertEquals(1, c.tamanho());
        assertTrue(c.avisos().isEmpty());
    }

    @Test
    @DisplayName("linha malformada e avisada com o numero e nao derruba a leitura")
    void linhaMalformadaEhAvisada() {
        CatalogResolver c = catalogo("""
                com.empresa:core | Dominio
                linha sem pipe nenhum
                sem-dois-pontos | Dominio
                com.outra:lib | Dominio
                """);

        assertEquals(2, c.tamanho());
        assertEquals(2, c.avisos().size());
        assertTrue(c.avisos().get(0).contains("Linha 2"));
        assertTrue(c.avisos().get(1).contains("Linha 3"));
    }

    @Test
    @DisplayName("coordenada repetida: a ultima vence, com aviso")
    void coordenadaRepetidaUltimaVence() {
        CatalogResolver c = catalogo("""
                com.empresa:core | Dominio
                com.empresa:core | Infraestrutura
                """);

        assertEquals("infraestrutura", c.categoriaDe("com.empresa", "core"));
        assertTrue(c.avisos().get(0).contains("ja estava no catalogo"));
    }

    @Test
    @DisplayName("arquivo inexistente vira aviso, nao excecao")
    void arquivoInexistenteViraAviso() {
        CatalogResolver c = CatalogResolver.de(base(), dir.resolve("nao-existe.txt"));

        assertEquals(0, c.tamanho());
        assertFalse(c.avisos().isEmpty());
        assertEquals("persistencia", c.categoriaDe("org.postgresql", "postgresql"));
    }

    // ---------------------------------------------------------------
    // Integracao com o renderizador
    // ---------------------------------------------------------------

    @Test
    @DisplayName("a categoria criada pelo usuario aparece como subgraph no diagrama")
    void categoriaDoUsuarioApareceNoDiagrama() throws Exception {
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.empresa</groupId>
                      <artifactId>core-interno</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """;

        PomView view = PomView.de(PomDependencyExtractor.readPom(new java.io.StringReader(pom)));
        String m = PomMermaidRenderer.render(view,
                catalogo("com.empresa:core-interno | Dominio | #e0f2fe | Core da empresa"));

        assertTrue(m.contains("subgraph grp_dominio[\"Dominio\"]"));
        assertTrue(m.contains("classDef cor_dominio fill:#e0f2fe"));
        assertFalse(m.contains("grp_nao_classificada"));
    }

    // ------------------------------------------------------- regra generica (incremento B)

    @Test
    @DisplayName("dependencia classificada pelo catalogo nao e de regra generica")
    void catalogoNaoEhRegraGenerica() {
        CategoryTable comGenerica = CategoryTable.deTexto("""
                { "schemaVersion": 1, "padrao": "nao-classificada", "categorias": [],
                  "regras": [ { "groupIdPrefixo": "org.springframework", "categoria": "utilitario",
                                "generica": true } ] }
                """);
        CatalogResolver c = CatalogResolver.deTexto(comGenerica,
                "org.springframework.boot:spring-boot-starter-cache | infraestrutura");

        assertTrue(c.regraGenericaDe("org.springframework.boot", "spring-boot-starter-cache").isEmpty());
        assertEquals(java.util.Optional.of("org.springframework*"),
                c.regraGenericaDe("org.springframework.boot", "spring-boot-starter"));
    }
}
