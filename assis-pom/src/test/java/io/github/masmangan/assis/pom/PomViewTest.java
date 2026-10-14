package io.github.masmangan.assis.pom;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP3a - Modelo intermediario PomView.
 *
 * Estes testes cobrem a extracao completa do pom.xml cru para um modelo em
 * memoria: coordenadas, heranca, propriedades, dependencias, plugins e modulos.
 *
 * Nenhum destes testes toca a geracao do diagrama. A renderizacao dos blocos
 * novos e assunto do CP3b.
 */
class PomViewTest {

    /** Constroi o PomView a partir de um pom.xml em texto. */
    private PomView view(String xml) throws Exception {
        Model model = PomDependencyExtractor.readPom(new StringReader(xml));
        return PomView.de(model);
    }

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
    // Coordenadas e heranca
    // ---------------------------------------------------------------

    @Test
    @DisplayName("herda groupId e version do parent quando o projeto os omite")
    void herdaCoordenadaDoParent() throws Exception {
        PomView v = view("""
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
                """);

        assertEquals("com.exemplo", v.projeto().groupId());
        assertEquals("filho", v.projeto().artifactId());
        assertEquals("2.5.0", v.projeto().version());
    }

    @Test
    @DisplayName("le o parent como coordenada completa")
    void leParentComoCoordenadaCompleta() throws Exception {
        PomView v = view(POM_FDS);

        assertTrue(v.parent().isPresent());
        assertEquals("org.springframework.boot:spring-boot-starter-parent:3.3.0",
                v.parent().get().rotulo());
    }

    @Test
    @DisplayName("projeto sem parent resulta em Optional vazio, nao em no fantasma")
    void parentAusenteResultaEmOptionalVazio() throws Exception {
        PomView v = view(POM_SEM_NADA);

        assertTrue(v.parent().isEmpty());
    }

    // ---------------------------------------------------------------
    // Propriedades
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ordena as propriedades alfabeticamente para garantir saida deterministica")
    void propriedadesSaoOrdenadasAlfabeticamente() throws Exception {
        PomView v = view("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <zebra.version>1</zebra.version>
                    <alfa.version>2</alfa.version>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                </project>
                """);

        // Model.getProperties() devolve java.util.Properties, que estende Hashtable
        // e nao garante ordem de iteracao. Sem ordenacao explicita o diagrama
        // mudaria entre execucoes, quebrando a reprodutibilidade.
        assertEquals(List.of("alfa.version", "maven.compiler.release", "zebra.version"),
                List.copyOf(v.propriedades().keySet()));
    }

    // ---------------------------------------------------------------
    // Plugins e modulos
    // ---------------------------------------------------------------

    @Test
    @DisplayName("le os plugins declarados em build/plugins")
    void lePluginsDeBuild() throws Exception {
        PomView v = view(POM_FDS);

        assertEquals(1, v.plugins().size());
        assertEquals("org.springframework.boot", v.plugins().get(0).groupId());
        assertEquals("spring-boot-maven-plugin", v.plugins().get(0).artifactId());
    }

    @Test
    @DisplayName("plugin sem groupId assume o grupo padrao do Maven")
    void pluginSemGroupIdAssumePadraoDoMaven() throws Exception {
        PomView v = view("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build>
                    <plugins>
                      <plugin>
                        <artifactId>maven-compiler-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        assertEquals("org.apache.maven.plugins", v.plugins().get(0).groupId());
    }

    @Test
    @DisplayName("le os modulos de um projeto agregador")
    void leModulesQuandoPackagingEPom() throws Exception {
        PomView v = view("""
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

        assertEquals(List.of("assis-core", "assis-pom"), v.modulos());
    }

    // ---------------------------------------------------------------
    // Dependencias normalizadas
    // ---------------------------------------------------------------

    @Test
    @DisplayName("dependencia sem escopo explicito e normalizada para compile")
    void escopoAusenteEhCompile() throws Exception {
        PomView v = view(POM_FDS);

        assertEquals("compile", v.dependencias().get(0).escopo());
        assertEquals("provided", v.dependencias().get(1).escopo());
        assertEquals("runtime", v.dependencias().get(2).escopo());
    }

    @Test
    @DisplayName("dependencia sem version e marcada como gerenciada pelo parent")
    void marcaVersaoComoGerenciadaQuandoAusente() throws Exception {
        PomView v = view(POM_FDS);

        assertTrue(v.dependencias().stream().allMatch(PomView.DependenciaView::versaoGerenciada));
    }

    @Test
    @DisplayName("preserva a marcacao optional")
    void preservaMarcacaoOptional() throws Exception {
        PomView v = view(POM_FDS);

        assertFalse(v.dependencias().get(0).opcional());
        assertTrue(v.dependencias().get(2).opcional());
    }

    @Test
    @DisplayName("dependencyManagement nao conta como dependencia direta")
    void dependencyManagementNaoContaComoDependenciaDireta() throws Exception {
        PomView v = view("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.fasterxml.jackson</groupId>
                        <artifactId>jackson-bom</artifactId>
                        <version>2.17.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.postgresql</groupId>
                      <artifactId>postgresql</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertEquals(1, v.dependencias().size());
        assertEquals("postgresql", v.dependencias().get(0).artifactId());
    }

    @Test
    @DisplayName("preserva a ordem de declaracao das dependencias")
    void preservaOrdemDeDeclaracao() throws Exception {
        PomView v = view(POM_FDS);

        assertEquals(List.of("spring-boot-starter-web", "lombok", "spring-boot-devtools"),
                v.dependencias().stream().map(PomView.DependenciaView::artifactId).toList());
    }
}
