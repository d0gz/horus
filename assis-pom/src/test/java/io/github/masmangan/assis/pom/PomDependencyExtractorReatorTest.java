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
 * CP7b - Reator na linha de comando.
 *
 * Verifica o modo reator e a geracao de um arquivo por modulo no modo completo.
 */
class PomDependencyExtractorReatorTest {

    @TempDir
    Path raiz;

    private ByteArrayOutputStream so;
    private ByteArrayOutputStream se;
    private PrintStream out;
    private PrintStream err;

    @BeforeEach
    void fluxos() {
        so = new ByteArrayOutputStream();
        se = new ByteArrayOutputStream();
        out = new PrintStream(so, true, StandardCharsets.UTF_8);
        err = new PrintStream(se, true, StandardCharsets.UTF_8);
    }

    private String terminal() {
        return so.toString(StandardCharsets.UTF_8);
    }

    private String erro() {
        return se.toString(StandardCharsets.UTF_8);
    }

    private void pom(String dir, String conteudo) throws Exception {
        Path d = dir.isEmpty() ? raiz : raiz.resolve(dir);
        Files.createDirectories(d);
        Files.writeString(d.resolve("pom.xml"), conteudo, StandardCharsets.UTF_8);
    }

    /** Monta um agregador com dois servicos, cada um com uma dependencia externa. */
    private void montarReator() throws Exception {
        pom("", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>raiz</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>gateway</module>
                    <module>customers</module>
                  </modules>
                </project>
                """);
        pom("gateway", servico("gateway", "org.springframework.boot", "spring-boot-starter-web"));
        pom("customers", servico("customers", "org.postgresql", "postgresql"));
    }

    private String servico(String artifactId, String grupo, String artefato) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>%s</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>%s</groupId>
                      <artifactId>%s</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(artifactId, grupo, artefato);
    }

    private int run(String... args) {
        return PomDependencyExtractor.run(args, out, err);
    }

    @Test
    @DisplayName("modo reator emite um no por modulo")
    void modoReatorEmiteUmNoPorModulo() throws Exception {
        montarReator();
        Path saida = raiz.resolve("reator.mmd");

        assertEquals(0, run(raiz.resolve("pom.xml").toString(), saida.toString(), "--modo=reator"));
        assertTrue(terminal().contains("subgraph rt[\"raiz\"]"));
        assertTrue(terminal().contains("gateway (1 deps)"));
        assertTrue(terminal().contains("customers (1 deps)"));
        assertTrue(Files.exists(saida));
    }

    @Test
    @DisplayName("modo completo sobre agregador grava um arquivo por modulo")
    void modoCompletoGravaUmArquivoPorModulo() throws Exception {
        montarReator();
        Path saida = raiz.resolve("pom.mmd");

        assertEquals(0, run(raiz.resolve("pom.xml").toString(), saida.toString(), "--modo=completo"));

        assertTrue(Files.exists(raiz.resolve("pom-gateway.mmd")));
        assertTrue(Files.exists(raiz.resolve("pom-customers.mmd")));
        assertTrue(erro().contains("Diagramas por modulo gravados: 2"));
    }

    @Test
    @DisplayName("o arquivo de cada modulo traz o diagrama daquele modulo")
    void arquivoDoModuloTrazOModuloCerto() throws Exception {
        montarReator();

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("pom.mmd").toString(), "--modo=completo");

        String gateway = Files.readString(raiz.resolve("pom-gateway.mmd"), StandardCharsets.UTF_8);
        assertTrue(gateway.contains("com.exemplo:gateway:1.0.0"));
        assertTrue(gateway.contains("spring-boot-starter-web"));
        assertFalse(gateway.contains("postgresql"));
    }

    @Test
    @DisplayName("projeto sem modulos nao gera arquivos extras")
    void projetoSimplesNaoGeraArquivosExtras() throws Exception {
        pom("", servico("simples", "org.postgresql", "postgresql"));

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("pom.mmd").toString(), "--modo=completo");

        assertFalse(erro().contains("Diagramas por modulo"));
    }

    @Test
    @DisplayName("avisa quando o modo dependencias e usado sobre um agregador")
    void avisaAgregadorNoModoDependencias() throws Exception {
        montarReator();

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("d.mmd").toString());

        assertTrue(erro().contains("pom agregador"));
    }

}
