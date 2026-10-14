package io.github.masmangan.assis.pom;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de caracterizacao do extrator de dependencias.
 *
 * Estes testes descrevem o comportamento ATUAL do modulo assis-pom sobre um
 * pom.xml cru (nao efetivo). Servem de rede de seguranca para as refatoracoes
 * previstas nos proximos incrementos: enquanto todos passarem, a saida
 * documentada no volume permanece valida.
 *
 * Dependencias necessarias no pom.xml do Horus:
 *   org.apache.maven:maven-model
 *   org.junit.jupiter:junit-jupiter (escopo test)
 */
class PomDependencyExtractorTest {

    @TempDir
    Path tempDir;

    /** Escreve um pom.xml temporario e devolve o Model ja lido. */
    private Model lerPom(String conteudo) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, conteudo, StandardCharsets.UTF_8);
        return PomDependencyExtractor.readPom(pom);
    }

    /** O pom.xml do projeto FDS, usado como caso de referencia no volume. */
    private static final String POM_FDS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>3.3.0</version>
                <relativePath/>
              </parent>
              <groupId>com.fds</groupId>
              <artifactId>sist-vendas</artifactId>
              <version>0.0.1-SNAPSHOT</version>
              <name>sist-vendas</name>
              <properties>
                <java.version>21</java.version>
              </properties>
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
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-devtools</artifactId>
                  <scope>runtime</scope>
                  <optional>true</optional>
                </dependency>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-test</artifactId>
                  <scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-data-jpa</artifactId>
                </dependency>
                <dependency>
                  <groupId>com.h2database</groupId>
                  <artifactId>h2</artifactId>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.postgresql</groupId>
                  <artifactId>postgresql</artifactId>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-validation</artifactId>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;

    // ---------------------------------------------------------------
    // Leitura do POM
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Leitura do pom.xml")
    class Leitura {

        @Test
        @DisplayName("nenhuma dependencia do POM cru declara versao: ela vem do parent")
        void nenhumaDependenciaDeclaraVersao() throws Exception {
            Model model = lerPom(POM_FDS);

            long semVersao = model.getDependencies().stream()
                    .filter(d -> d.getVersion() == null)
                    .count();

            // Este numero e a medida que justifica o incremento do POM efetivo.
            assertEquals(8, semVersao);
        }
    }

    // ---------------------------------------------------------------
    // Geracao do Mermaid
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Geracao do diagrama Mermaid")
    class Geracao {

        @Test
        @DisplayName("dependencia sem escopo explicito usa aresta sem rotulo")
        void dependenciaSemEscopoUsaArestaSimples() throws Exception {
            String mermaid = PomDependencyExtractor.toMermaid(lerPom(POM_FDS));

            assertTrue(mermaid.contains("project --> dep0[\"org.springframework.boot:spring-boot-starter-web\"]"));
        }

        @Test
        @DisplayName("omite a versao no rotulo quando ela nao esta declarada no POM cru")
        void omiteVersaoQuandoAusente() throws Exception {
            String mermaid = PomDependencyExtractor.toMermaid(lerPom(POM_FDS));

            // Comportamento atual, documentado aqui de proposito: a versao nao aparece.
            // O incremento do POM efetivo deve alterar este teste, nunca por acidente.
            assertFalse(mermaid.contains("spring-boot-starter-web:"));
            assertFalse(mermaid.contains("3.3.0"));
        }

        @Test
        @DisplayName("gera apenas o no raiz quando o projeto nao tem dependencias")
        void projetoSemDependencias() throws Exception {
            String pomVazio = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.exemplo</groupId>
                      <artifactId>vazio</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """;

            String mermaid = PomDependencyExtractor.toMermaid(lerPom(pomVazio));

            assertTrue(mermaid.contains("project[\"com.exemplo:vazio:1.0.0\"]"));
            assertFalse(mermaid.contains("dep0"));
        }

        @Test
        @DisplayName("herda groupId e version do parent quando o projeto os omite")
        void herdaCoordenadasDoParent() throws Exception {
            String pomHerdado = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                        <groupId>com.exemplo</groupId>
                        <artifactId>pai</artifactId>
                        <version>2.5.0</version>
                      </parent>
                      <artifactId>filho</artifactId>
                    </project>
                    """;

            String mermaid = PomDependencyExtractor.toMermaid(lerPom(pomHerdado));

            assertTrue(mermaid.contains("project[\"com.exemplo:filho:2.5.0\"]"));
        }
    }
}
