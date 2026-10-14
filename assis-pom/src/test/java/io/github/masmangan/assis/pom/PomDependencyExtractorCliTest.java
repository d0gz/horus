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
 * CP2 - Interface de linha de comando.
 *
 * Estes testes cobrem apenas a camada de entrada e saida do modulo:
 * leitura do caminho por argumento, escrita do arquivo .mmd e codigos de saida.
 * A logica de extracao e de geracao do Mermaid nao muda neste checkpoint e
 * continua coberta pelos testes de PomDependencyExtractorTest.
 *
 * Codigos de saida esperados:
 *   0 - sucesso
 *   1 - arquivo pom.xml nao encontrado
 *   2 - uso incorreto (nenhum argumento informado)
 *   3 - falha ao interpretar o XML
 */
class PomDependencyExtractorCliTest {

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

    private String textoDaSaidaPadrao() {
        return saidaPadrao.toString(StandardCharsets.UTF_8);
    }

    private String textoDaSaidaErro() {
        return saidaErro.toString(StandardCharsets.UTF_8);
    }

    private static final String POM_MINIMO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.exemplo</groupId>
              <artifactId>demo</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.projectlombok</groupId>
                  <artifactId>lombok</artifactId>
                  <scope>provided</scope>
                </dependency>
              </dependencies>
            </project>
            """;

    /** Escreve um pom.xml temporario e devolve o caminho. */
    private Path criarPom(String conteudo) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, conteudo, StandardCharsets.UTF_8);
        return pom;
    }

    // ---------------------------------------------------------------
    // Entrada
    // ---------------------------------------------------------------

    @Test
    @DisplayName("aceita o caminho do pom.xml como primeiro argumento")
    void aceitaCaminhoDoPomPorArgumento() throws Exception {
        Path pom = criarPom(POM_MINIMO);
        Path destino = tempDir.resolve("dependencies.mmd");

        int codigo = PomDependencyExtractor.run(
                new String[]{pom.toString(), destino.toString()}, out, err);

        assertEquals(0, codigo);
        assertTrue(textoDaSaidaPadrao().startsWith("flowchart LR"));
        assertTrue(textoDaSaidaPadrao().contains("com.exemplo:demo:1.0.0"));
    }

    @Test
    @DisplayName("falha com codigo 2 quando nenhum argumento e informado")
    void falhaSemArgumento() {
        int codigo = PomDependencyExtractor.run(new String[]{}, out, err);

        assertEquals(2, codigo);
        assertFalse(textoDaSaidaErro().isBlank());
        // Erros vao para a saida de erro: a saida padrao contem somente diagramas.
        assertTrue(textoDaSaidaPadrao().isEmpty());
    }

    @Test
    @DisplayName("falha com codigo 1 quando o arquivo informado nao existe")
    void falhaQuandoArquivoNaoExiste() {
        Path inexistente = tempDir.resolve("nao-existe.xml");

        int codigo = PomDependencyExtractor.run(
                new String[]{inexistente.toString()}, out, err);

        assertEquals(1, codigo);
        assertTrue(textoDaSaidaErro().contains("nao-existe.xml"));
    }

    @Test
    @DisplayName("falha com codigo 3 quando o XML esta malformado")
    void falhaQuandoXmlMalformado() throws Exception {
        Path pom = criarPom("<project><groupId>quebrado");

        int codigo = PomDependencyExtractor.run(new String[]{pom.toString()}, out, err);

        assertEquals(3, codigo);
        assertFalse(textoDaSaidaErro().isBlank());
    }

    // ---------------------------------------------------------------
    // Saida
    // ---------------------------------------------------------------

    @Test
    @DisplayName("usa o segundo argumento como caminho de saida quando informado")
    void usaSegundoArgumentoComoSaida() throws Exception {
        Path pom = criarPom(POM_MINIMO);
        Path destino = tempDir.resolve("saida").resolve("diagrama.mmd");

        int codigo = PomDependencyExtractor.run(
                new String[]{pom.toString(), destino.toString()}, out, err);

        assertEquals(0, codigo);
        assertTrue(Files.exists(destino), "o diretorio de saida deve ser criado se nao existir");
    }

    @Test
    @DisplayName("o conteudo do arquivo e identico ao impresso no terminal")
    void arquivoEIdenticoAoTerminal() throws Exception {
        Path pom = criarPom(POM_MINIMO);
        Path destino = tempDir.resolve("dependencies.mmd");

        PomDependencyExtractor.run(new String[]{pom.toString(), destino.toString()}, out, err);

        String noArquivo = Files.readString(destino, StandardCharsets.UTF_8);
        assertEquals(noArquivo, textoDaSaidaPadrao());
    }

}
