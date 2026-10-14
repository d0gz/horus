package io.github.masmangan.assis.pom;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP3b - Renderizacao do diagrama completo.
 *
 * O diagrama produzido aqui representa a perspectiva arquitetural do pom cru:
 * heranca, configuracao, build, modulos e dependencias. E um artefato distinto
 * do diagrama de dependencias gerado por PomDependencyExtractor.toMermaid, que
 * permanece inalterado como primeiro incremento do modulo.
 */
class PomMermaidRendererTest {

    private String render(String xml) throws Exception {
        Model model = PomDependencyExtractor.readPom(new StringReader(xml));
        return PomMermaidRenderer.render(PomView.de(model));
    }

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

    private static final String POM_SEM_NADA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.exemplo</groupId>
              <artifactId>vazio</artifactId>
              <version>1.0.0</version>
            </project>
            """;

    // ---------------------------------------------------------------
    // Heranca
    // ---------------------------------------------------------------

    @Test
    @DisplayName("emite o no do parent com a coordenada completa")
    void emiteNoDoParent() throws Exception {
        String m = render(POM_FDS);

        assertTrue(m.contains("parent[\"org.springframework.boot:spring-boot-starter-parent:3.3.0\"]"));
        // A heranca usa aresta pontilhada, distinta das arestas de dependencia.
        assertTrue(m.contains("parent -.->"));
    }

    @Test
    @DisplayName("conta apenas as dependencias sem versao declarada")
    void contaSomenteVersoesAusentes() throws Exception {
        String m = render("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.exemplo</groupId>
                    <artifactId>pai</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>filho</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.postgresql</groupId>
                      <artifactId>postgresql</artifactId>
                      <version>42.7.3</version>
                    </dependency>
                    <dependency>
                      <groupId>org.projectlombok</groupId>
                      <artifactId>lombok</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertTrue(m.contains("gerencia 1 de 2 versoes"));
    }

    @Test
    @DisplayName("projeto sem parent nao emite no nem aresta de heranca")
    void semParentNaoEmiteNoDeHeranca() throws Exception {
        String m = render(POM_SEM_NADA);

        assertFalse(m.contains("parent["));
        assertFalse(m.contains("-.->"));
    }

    // ---------------------------------------------------------------
    // Blocos
    // ---------------------------------------------------------------

    @Test
    @DisplayName("propriedades viram um subgraph de configuracao ligado ao projeto")
    void propriedadesViramSubgraphDeConfiguracao() throws Exception {
        String m = render(POM_FDS);

        assertTrue(m.contains("subgraph cfg[\"Configuracao\"]"));
        assertTrue(m.contains("java.version = 21"));
        assertTrue(m.contains("project -.-> cfg"));
    }

    @Test
    @DisplayName("plugins viram um subgraph de build ligado ao projeto")
    void pluginsViramSubgraphDeBuild() throws Exception {
        String m = render(POM_FDS);

        assertTrue(m.contains("subgraph bld[\"Build\"]"));
        assertTrue(m.contains("org.springframework.boot:spring-boot-maven-plugin"));
        assertTrue(m.contains("project -.-> bld"));
    }

    @Test
    @DisplayName("modulos viram um subgraph quando o projeto e agregador")
    void modulosViramSubgraph() throws Exception {
        String m = render("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>agregador</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>assis-core</module>
                    <module>assis-pom</module>
                  </modules>
                </project>
                """);

        assertTrue(m.contains("subgraph mod[\"Modulos\"]"));
        assertTrue(m.contains("assis-core"));
        assertTrue(m.contains("assis-pom"));
    }

    @Test
    @DisplayName("bloco ausente nao emite subgraph vazio")
    void blocoAusenteNaoEmiteSubgraphVazio() throws Exception {
        String m = render(POM_SEM_NADA);

        assertFalse(m.contains("subgraph"));
    }

    // ---------------------------------------------------------------
    // Dependencias
    // ---------------------------------------------------------------

    @Test
    @DisplayName("mantem o escopo como rotulo da aresta")
    void mantemEscopoNaAresta() throws Exception {
        String m = render(POM_FDS);

        // A partir do CP5 o rotulo do no e declarado dentro do subgraph da
        // categoria, e a aresta apenas referencia o identificador.
        assertTrue(m.contains("project --> dep0"));
        assertTrue(m.contains("project -->|provided| dep1"));
        assertTrue(m.contains("dep0[\"org.springframework.boot:spring-boot-starter-web\"]"));
        assertTrue(m.contains("dep1[\"org.projectlombok:lombok\"]"));
    }

    @Test
    @DisplayName("exibe a versao no rotulo somente quando ela e declarada no pom")
    void exibeVersaoSomenteQuandoDeclarada() throws Exception {
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
                      <version>42.7.3</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertTrue(m.contains("org.postgresql:postgresql:42.7.3"));
    }

    // ---------------------------------------------------------------
    // Robustez
    // ---------------------------------------------------------------

    @Test
    @DisplayName("escapa aspas no rotulo para nao quebrar a sintaxe do Mermaid")
    void escapaAspasNoRotulo() throws Exception {
        String m = render("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <banner.texto>diz "ola"</banner.texto>
                  </properties>
                </project>
                """);

        assertFalse(m.contains("diz \"ola\""));
        assertTrue(m.contains("#quot;ola#quot;"));
    }

}
