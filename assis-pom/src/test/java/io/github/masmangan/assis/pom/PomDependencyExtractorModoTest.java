package io.github.masmangan.assis.pom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP3c - Selecao do artefato na linha de comando.
 *
 * O modulo passa a produzir dois artefatos distintos a partir do mesmo pom.xml:
 * a lista de dependencias (primeiro incremento) e a perspectiva arquitetural
 * completa (segundo incremento). A flag --modo escolhe qual deles gerar, e o
 * modo dependencias e o padrao para preservar o comportamento anterior.
 */
class PomDependencyExtractorModoTest {

    @TempDir
    Path tempDir;

    private ByteArrayOutputStream saidaPadrao;
    private ByteArrayOutputStream saidaErro;
    private PrintStream out;
    private PrintStream err;

    @BeforeEach
    void prepararFluxos() {
        saidaPadrao = new ByteArrayOutputStream();
        saidaErro = new ByteArrayOutputStream();
        out = new PrintStream(saidaPadrao, true, StandardCharsets.UTF_8);
        err = new PrintStream(saidaErro, true, StandardCharsets.UTF_8);
    }

    private String terminal() {
        return saidaPadrao.toString(StandardCharsets.UTF_8);
    }

    private static final String POM_COM_PARENT = """
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
              <properties>
                <java.version>21</java.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
              </dependencies>
            </project>
            """;

    private Path criarPom() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, POM_COM_PARENT, StandardCharsets.UTF_8);
        return pom;
    }

    @Test
    @DisplayName("sem flag, gera a lista de dependencias, como antes")
    void modoPadraoEhDependencias() throws Exception {
        Path pom = criarPom();
        Path destino = tempDir.resolve("saida.mmd");

        int codigo = PomDependencyExtractor.run(
                new String[]{pom.toString(), destino.toString()}, out, err);

        assertEquals(0, codigo);
        assertFalse(terminal().contains("parent["));
        assertFalse(terminal().contains("subgraph"));
    }

    @Test
    @DisplayName("com --modo=completo, gera a perspectiva arquitetural do pom")
    void modoCompletoGeraDiagramaCompleto() throws Exception {
        Path pom = criarPom();
        Path destino = tempDir.resolve("saida.mmd");

        int codigo = PomDependencyExtractor.run(
                new String[]{pom.toString(), destino.toString(), "--modo=completo"}, out, err);

        assertEquals(0, codigo);
        assertTrue(terminal().contains("parent[\"org.springframework.boot:spring-boot-starter-parent:3.3.0\"]"));
        assertTrue(terminal().contains("subgraph cfg[\"Configuracao\"]"));
        assertTrue(terminal().contains("gerencia 1 de 1 versoes"));
    }

    @Test
    @DisplayName("a flag pode vir antes dos caminhos")
    void flagPodeVirEmQualquerPosicao() throws Exception {
        Path pom = criarPom();
        Path destino = tempDir.resolve("saida.mmd");

        int codigo = PomDependencyExtractor.run(
                new String[]{"--modo=completo", pom.toString(), destino.toString()}, out, err);

        assertEquals(0, codigo);
        assertTrue(terminal().contains("subgraph"));
    }

    @Test
    @DisplayName("modo desconhecido e erro de uso")
    void modoInvalidoEhErroDeUso() throws Exception {
        Path pom = criarPom();

        int codigo = PomDependencyExtractor.run(
                new String[]{pom.toString(), "--modo=grafo"}, out, err);

        assertEquals(2, codigo);
        assertTrue(saidaErro.toString(StandardCharsets.UTF_8).contains("grafo"));
        
        // Uma opcao desconhecida tambem e erro de uso, e nunca e tratada como caminho.
        assertEquals(2, PomDependencyExtractor.run(
                new String[]{pom.toString(), "--verboso"}, out, err));
    }

    @Test
    @DisplayName("no modo completo, as dependencias sao agrupadas por categoria")
    void modoCompletoAgrupaPorCategoria() throws Exception {
        Path pom = criarPom();

        PomDependencyExtractor.run(
                new String[]{pom.toString(), tempDir.resolve("saida.mmd").toString(),
                        "--modo=completo"}, out, err);

        // spring-boot-starter-web esta coberto pela tabela do repositorio.
        assertFalse(terminal().contains("grp_nao_classificada"),
                "nenhuma dependencia deste pom deveria cair na categoria residual");
    }

}
