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
 * CP9c - Diagrama de conteineres na linha de comando.
 *
 * O modo c2 atende tanto projeto simples quanto multimodulo, porque recebe o
 * ReactorView e deixa a sobrecarga de ArchetypeRules decidir.
 */
class PomDependencyExtractorC2Test {

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

    /** Projeto Spring Boot com web, JPA, PostgreSQL e H2, como o caso FDS. */
    private static final String POM_FDS = """
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
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
                <dependency>
                  <groupId>org.projectlombok</groupId>
                  <artifactId>lombok</artifactId>
                  <scope>provided</scope>
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

    private int run(String... args) {
        return PomDependencyExtractor.run(args, out, err);
    }

    // ------------------------------------------------------- basico

    @Test
    @DisplayName("modo c2 gera o diagrama de conteineres")
    void modoC2GeraDiagrama() throws Exception {
        pom("", POM_FDS);
        Path saida = raiz.resolve("c2.mmd");

        assertEquals(0, run(raiz.resolve("pom.xml").toString(), saida.toString(), "--modo=c2"));
        assertTrue(terminal().startsWith("flowchart TB"));
        assertTrue(terminal().contains("subgraph sys[\"sist-vendas\"]"));
        assertTrue(Files.exists(saida));
    }

    @Test
    @DisplayName("o diagrama traz ator, aplicacao e os dois bancos do pom")
    void diagramaTrazOsElementosEsperados() throws Exception {
        pom("", POM_FDS);

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("c2.mmd").toString(), "--modo=c2");

        assertTrue(terminal().contains("pessoa(["));
        assertTrue(terminal().contains("Java 21, Spring Boot 3.3.0"));
        assertTrue(terminal().contains("PostgreSQL"));
        assertTrue(terminal().contains("H2 (nao confirmado)"));
    }

    // ------------------------------------------------------- saida padrao

    // ------------------------------------------------------- notas

    @Test
    @DisplayName("as notas do modelo sao reportadas no terminal de erro")
    void notasVaoParaSaidaDeErro() throws Exception {
        pom("", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.cloud</groupId>
                      <artifactId>spring-cloud-starter-openfeign</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("c2.mmd").toString(), "--modo=c2");

        assertTrue(erro().contains("Nota: "));
        assertTrue(erro().contains("nao sao declarados no Maven"));
    }

    // ------------------------------------------------------- multimodulo


    @DisplayName("em projeto multimodulo, cada modulo vira um conteiner")
    void multimoduloGeraUmContainerPorModulo() throws Exception {
        montarPlataforma();

        assertEquals(0, run(raiz.resolve("pom.xml").toString(),
                raiz.resolve("c2.mmd").toString(), "--modo=c2"));

        assertTrue(terminal().contains("app_gateway["));
        assertTrue(terminal().contains("app_customers["));
        assertTrue(erro().contains("relacoes entre os modulos nao sao declaradas no Maven"));
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

    // ------------------------------------------------------- catalogo

    @Test
    @DisplayName("o catalogo do usuario tambem vale no modo c2")
    void catalogoValeNoModoC2() throws Exception {
        pom("", POM_FDS);
        Path catalogo = raiz.resolve("catalogo.txt");
        Files.writeString(catalogo, "com.empresa:core | Dominio\n", StandardCharsets.UTF_8);

        assertEquals(0, run(raiz.resolve("pom.xml").toString(), raiz.resolve("c2.mmd").toString(),
                "--modo=c2", "--catalogo=" + catalogo));

        assertTrue(erro().contains("Catalogo: 1 entrada(s) aplicada(s)."));
    }

    @Test
    @DisplayName("modo c2 sobre agregador grava um diagrama por modulo")
    void modoC2GravaUmArquivoPorModulo() throws Exception {
        montarPlataforma();
        Path saida = raiz.resolve("c2.mmd");

        assertEquals(0, run(raiz.resolve("pom.xml").toString(), saida.toString(), "--modo=c2"));

        assertTrue(Files.exists(raiz.resolve("c2-gateway.mmd")));
        assertTrue(Files.exists(raiz.resolve("c2-customers.mmd")));
        assertTrue(erro().contains("Diagramas por modulo gravados: 2"));
    }

    @Test
    @DisplayName("o diagrama de cada modulo traz apenas os conteineres daquele modulo")
    void diagramaDoModuloTrazApenasOSeu() throws Exception {
        montarPlataforma();

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("c2.mmd").toString(), "--modo=c2");

        String gateway = Files.readString(raiz.resolve("c2-gateway.mmd"), StandardCharsets.UTF_8);
        String customers = Files.readString(raiz.resolve("c2-customers.mmd"), StandardCharsets.UTF_8);

        // O gateway declara web: tem ator, nao tem banco.
        assertTrue(gateway.contains("subgraph sys[\"gateway\"]"));
        assertTrue(gateway.contains("pessoa(["));
        assertFalse(gateway.contains("PostgreSQL"));

        // O customers declara driver: tem banco, nao tem ator.
        assertTrue(customers.contains("subgraph sys[\"customers\"]"));
        assertTrue(customers.contains("PostgreSQL"));
        assertFalse(customers.contains("pessoa(["));
    }

    /** Agregador com dois servicos de perfis diferentes: um web, um com banco. */
    private void montarPlataforma() throws Exception {
        pom("", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>plataforma</artifactId>
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

    @Test
    @DisplayName("por padrao, o mesmo banco declarado em dois modulos vira um so conteiner")
    void bancosIguaisSaoUnidosPorPadrao() throws Exception {
        montarDoisServicosComPostgres();

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("c2.mmd").toString(), "--modo=c2");

        assertEquals(1, ocorrencias(terminal(), "[(\"PostgreSQL"));
        assertTrue(terminal().contains("declarado em 2 modulos"));
    }

    @Test
    @DisplayName("com --bancos=por-modulo, cada modulo recebe o seu banco")
    void bancosSeparadosComOpcao() throws Exception {
        montarDoisServicosComPostgres();

        run(raiz.resolve("pom.xml").toString(), raiz.resolve("c2.mmd").toString(),
                "--modo=c2", "--bancos=por-modulo");

        assertEquals(2, ocorrencias(terminal(), "[(\"PostgreSQL"));
    }

    private void montarDoisServicosComPostgres() throws Exception {
        pom("", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>loja</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>pedidos</module>
                    <module>estoque</module>
                  </modules>
                </project>
                """);
        pom("pedidos", servico("pedidos", "org.postgresql", "postgresql"));
        pom("estoque", servico("estoque", "org.postgresql", "postgresql"));
    }

    private static int ocorrencias(String texto, String trecho) {
        int n = 0;
        for (int i = texto.indexOf(trecho); i >= 0; i = texto.indexOf(trecho, i + 1)) {
            n++;
        }
        return n;
    }
}
