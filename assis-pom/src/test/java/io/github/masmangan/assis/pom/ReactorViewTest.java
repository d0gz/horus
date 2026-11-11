package io.github.masmangan.assis.pom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP7a - Travessia do reator.
 *
 * Os testes montam arvores de diretorios reais em @TempDir, porque a travessia e
 * justamente sobre o sistema de arquivos: o elemento <module> e um caminho.
 */
class ReactorViewTest {

    /** Tabela sem regras: estes testes tratam da travessia, nao da classificacao. */
    private static final CategoryResolver SEM_CATEGORIAS = CategoryTable.deTexto(
            "{\"schemaVersion\":1,\"padrao\":\"nao-classificada\",\"categorias\":[],\"regras\":[]}");

    @TempDir
    Path raiz;

    private void pom(String dir, String conteudo) throws Exception {
        Path destino = dir.isEmpty() ? raiz : raiz.resolve(dir);
        Files.createDirectories(destino);
        Files.writeString(destino.resolve("pom.xml"), conteudo, StandardCharsets.UTF_8);
    }

    private String agregador(String artifactId, String... modulos) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>%s</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                """.formatted(artifactId));
        for (String m : modulos) {
            sb.append("    <module>").append(m).append("</module>\n");
        }
        return sb.append("  </modules>\n</project>\n").toString();
    }

    private String servico(String artifactId, String... dependencias) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.exemplo</groupId>
                  <artifactId>%s</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                """.formatted(artifactId));
        for (String d : dependencias) {
            sb.append("    <dependency>\n")
                    .append("      <groupId>").append(d.split(":")[0]).append("</groupId>\n")
                    .append("      <artifactId>").append(d.split(":")[1]).append("</artifactId>\n")
                    .append("    </dependency>\n");
        }
        return sb.append("  </dependencies>\n</project>\n").toString();
    }

    private ReactorView reator() throws Exception {
        return ReactorView.de(raiz.resolve("pom.xml"));
    }

    // ---------------------------------------------------------------
    // Travessia
    // ---------------------------------------------------------------

    @Test
    @DisplayName("le o pom de cada modulo declarado, em subdiretorio")
    void leModuloEmSubdiretorio() throws Exception {
        pom("", agregador("raiz", "servico-a", "servico-b"));
        pom("servico-a", servico("servico-a"));
        pom("servico-b", servico("servico-b"));

        ReactorView r = reator();

        assertEquals(2, r.modulos().size());
        assertEquals("servico-a", r.modulos().get(0).pom().projeto().artifactId());
        assertEquals("servico-b", r.modulos().get(1).pom().projeto().artifactId());
    }

    @Test
    @DisplayName("preserva a ordem de declaracao dos modulos")
    void preservaOrdemDosModulos() throws Exception {
        pom("", agregador("raiz", "z-servico", "a-servico"));
        pom("z-servico", servico("z-servico"));
        pom("a-servico", servico("a-servico"));

        assertEquals(List.of("z-servico", "a-servico"),
                reator().modulos().stream().map(m -> m.pom().projeto().artifactId()).toList());
    }

    @Test
    @DisplayName("desce recursivamente quando um modulo tambem e agregador")
    void desceRecursivamente() throws Exception {
        pom("", agregador("raiz", "grupo"));
        pom("grupo", agregador("grupo", "folha"));
        pom("grupo/folha", servico("folha"));

        ReactorView r = reator();

        assertEquals(1, r.modulos().size());
        assertEquals("folha", r.modulos().get(0).modulos().get(0).pom().projeto().artifactId());
        assertEquals(3, r.achatado().size());
    }

    @Test
    @DisplayName("projeto sem modulos tem apenas a raiz no reator")
    void projetoSimplesTemApenasARaiz() throws Exception {
        pom("", servico("simples"));

        ReactorView r = reator();

        assertFalse(r.ehAgregador());
        assertEquals(1, r.achatado().size());
    }

    // ---------------------------------------------------------------
    // Guardas
    // ---------------------------------------------------------------

    @Test
    @DisplayName("modulo inexistente e registrado como ausente, sem interromper")
    void moduloInexistenteEhRegistrado() throws Exception {
        pom("", agregador("raiz", "existe", "fantasma"));
        pom("existe", servico("existe"));

        ReactorView r = reator();

        assertEquals(1, r.modulos().size());
        assertEquals(List.of("fantasma"), r.modulosAusentes());
    }

    @Test
    @DisplayName("recusa modulo que aponta para fora do diretorio da raiz")
    void recusaCaminhoForaDaRaiz() throws Exception {
        pom("", agregador("raiz", "../vizinho"));

        ReactorView r = reator();

        assertTrue(r.modulos().isEmpty());
        assertEquals(List.of("../vizinho"), r.modulosAusentes());
    }

    @Test
    @DisplayName("modulo com xml invalido nao derruba a travessia")
    void moduloInvalidoNaoDerrubaTravessia() throws Exception {
        pom("", agregador("raiz", "bom", "quebrado"));
        pom("bom", servico("bom"));
        pom("quebrado", "<project><groupId>truncado");

        ReactorView r = reator();

        assertEquals(1, r.modulos().size());
        assertEquals(List.of("quebrado"), r.modulosAusentes());
    }

    @Test
    @DisplayName("ignora modulo ja visitado, protegendo contra ciclo")
    void ignoraModuloJaVisitado() throws Exception {
        pom("", agregador("raiz", "a", "a"));
        pom("a", servico("a"));

        ReactorView r = reator();

        assertEquals(1, r.modulos().size());
        assertEquals(List.of("a"), r.modulosAusentes());
    }

    // ---------------------------------------------------------------
    // Arestas internas
    // ---------------------------------------------------------------

    @Test
    @DisplayName("dependencia declarada sobre outro modulo do reator vira aresta interna")
    void dependenciaEntreModulosViraAresta() throws Exception {
        pom("", agregador("raiz", "gateway", "customers"));
        pom("gateway", servico("gateway", "com.exemplo:customers"));
        pom("customers", servico("customers"));

        List<ReactorView.ArestaInterna> arestas = reator().arestasInternas();

        assertEquals(1, arestas.size());
        assertEquals("gateway", arestas.get(0).origem());
        assertEquals("customers", arestas.get(0).destino());
    }

    @Test
    @DisplayName("modulos que so se comunicam em execucao nao produzem aresta")
    void modulosSemDependenciaMavenNaoProduzemAresta() throws Exception {
        // Reproduz a estrutura observada em projetos de microsservicos: cada modulo
        // declara apenas dependencias externas e conversa com os demais por HTTP.
        pom("", agregador("raiz", "a", "b", "c"));
        pom("a", servico("a", "org.springframework.boot:spring-boot-starter-web"));
        pom("b", servico("b", "org.springframework.boot:spring-boot-starter-web"));
        pom("c", servico("c", "org.springframework.boot:spring-boot-starter-web"));

        ReactorView r = reator();

        assertEquals(4, r.achatado().size());
        assertTrue(r.arestasInternas().isEmpty());
    }

    // ---------------------------------------------------------------
    // Renderizacao
    // ---------------------------------------------------------------

    @Test
    @DisplayName("emite um no por modulo dentro do subgraph do reator")
    void emiteUmNoPorModulo() throws Exception {
        pom("", agregador("raiz", "a", "b"));
        pom("a", servico("a"));
        pom("b", servico("b"));

        String m = ReactorMermaidRenderer.render(reator());

        assertTrue(m.startsWith("flowchart LR"));
        assertTrue(m.contains("subgraph rt[\"raiz\"]"));
        assertTrue(m.contains("mod1[\"a (0 deps)\"]"));
        assertTrue(m.contains("mod2[\"b (0 deps)\"]"));
    }

    @Test
    @DisplayName("registra no artefato quando nao ha dependencia entre modulos")
    void registraAusenciaDeArestas() throws Exception {
        pom("", agregador("raiz", "a"));
        pom("a", servico("a"));

        String m = ReactorMermaidRenderer.render(reator());

        assertTrue(m.contains("%% nenhuma dependencia Maven declarada entre os modulos"));
    }

    @Test
    @DisplayName("desenha a aresta quando ha dependencia entre modulos")
    void desenhaArestaInterna() throws Exception {
        pom("", agregador("raiz", "gateway", "customers"));
        pom("gateway", servico("gateway", "com.exemplo:customers"));
        pom("customers", servico("customers"));

        String m = ReactorMermaidRenderer.render(reator());

        assertTrue(m.contains("mod1 --> mod2"));
        assertFalse(m.contains("nenhuma dependencia"));
    }

    @Test
    @DisplayName("modulo ausente aparece no diagrama, marcado")
    void moduloAusenteApareceNoDiagrama() throws Exception {
        pom("", agregador("raiz", "fantasma"));

        String m = ReactorMermaidRenderer.render(reator());

        assertTrue(m.contains("(nao encontrado)"));
    }

    @Test
    @DisplayName("o C2 do reator preserva as relacoes de cada modulo, com o id do modulo")
    void c2DoReatorPreservaRelacoes() throws Exception {
        pom("", agregador("raiz", "pedidos"));
        pom("pedidos", servico("pedidos", "org.postgresql:postgresql"));

        C4Model m = ArchetypeRules.aplicar(reator(), SEM_CATEGORIAS);

        // Defeito corrigido: as relacoes dos modulos se perdiam no diagrama agregado.
        assertTrue(m.relacoes().stream().anyMatch(r ->
                r.origem().equals("app_pedidos") && r.destino().equals("db_postgresql")));
        assertTrue(m.relacoes().stream().noneMatch(r -> r.origem().equals("app")));
    }

    @Test
    @DisplayName("servico de infraestrutura que e modulo do reator vira conteiner interno")
    void infraQueEhModuloNaoEhExterna() throws Exception {
        pom("", agregador("plataforma", "plataforma-config-server", "plataforma-web"));
        pom("plataforma-config-server", servico("plataforma-config-server"));
        pom("plataforma-web", servico("plataforma-web",
                "org.springframework.cloud:spring-cloud-starter-config"));

        C4Model m = ArchetypeRules.aplicar(reator(), SEM_CATEGORIAS);

        // Defeito corrigido: o Config Server aparecia dentro e fora da fronteira.
        assertFalse(m.temTipo(C4Model.Tipo.SISTEMA_EXTERNO));
        assertTrue(m.relacoes().stream().anyMatch(r ->
                r.origem().equals("app_plataforma_web")
                && r.destino().equals("app_plataforma_config_server")));
    }

    @Test
    @DisplayName("C2 de agregador sem modulos legiveis traz o proprio projeto e registra a ausencia")
    void c2DeAgregadorSemModulosNaoSaiVazio() throws Exception {
        pom("", agregador("raiz", "fantasma"));

        C4Model m = ArchetypeRules.aplicar(reator(), SEM_CATEGORIAS);

        // Defeito corrigido: o diagrama saia vazio, sem nenhum aviso no artefato.
        assertTrue(m.temTipo(C4Model.Tipo.APLICACAO));
        assertTrue(m.notas().stream().anyMatch(n -> n.contains("fantasma")));
    }

    // ---------------------------------------------------------------
    // C2 do reator: gateway e interface (incremento A)
    // ---------------------------------------------------------------

    private static final String GATEWAY =
            "org.springframework.cloud:spring-cloud-starter-gateway-server-webflux";
    private static final String WEB = "org.springframework.boot:spring-boot-starter-web";

    private static boolean temRelacao(C4Model m, String origem, String destino) {
        return m.relacoes().stream().anyMatch(r ->
                r.origem().equals(origem) && r.destino().equals(destino));
    }

    @Test
    @DisplayName("C2 do reator: o gateway roteia para os modulos web no lugar do ator, sem confirmacao")
    void gatewayRoteiaNoLugarDoAtor() throws Exception {
        pom("", agregador("loja", "loja-gateway", "pedidos"));
        pom("loja-gateway", servico("loja-gateway", GATEWAY));
        pom("pedidos", servico("pedidos", WEB));

        C4Model m = ArchetypeRules.aplicar(reator(), SEM_CATEGORIAS);

        C4Model.Relacao rota = m.relacoes().stream().filter(r ->
                r.origem().equals("app_loja_gateway") && r.destino().equals("app_pedidos"))
                .findFirst().orElseThrow();
        assertEquals(C4Model.Procedencia.NAO_CONFIRMAVEL, rota.procedencia());
        assertEquals("HTTP", rota.tecnologia());
        assertTrue(temRelacao(m, "pessoa", "app_loja_gateway"));
        assertFalse(temRelacao(m, "pessoa", "app_pedidos"));
    }

    @Test
    @DisplayName("C2 do reator: modulo que declara o starter do gateway sem gateway no nome nao e porta de entrada")
    void starterDoGatewaySemNomeNaoEhPortaDeEntrada() throws Exception {
        pom("", agregador("loja", "loja-gateway", "assistente"));
        pom("loja-gateway", servico("loja-gateway", GATEWAY));
        pom("assistente", servico("assistente", WEB, GATEWAY));

        C4Model m = ArchetypeRules.aplicar(reator(), SEM_CATEGORIAS);

        assertTrue(temRelacao(m, "app_loja_gateway", "app_assistente"));
        assertFalse(temRelacao(m, "app_assistente", "app_loja_gateway"));
        assertFalse(temRelacao(m, "pessoa", "app_assistente"));
        assertTrue(m.notas().stream().anyMatch(n -> n.contains(GATEWAY)
                && n.contains("nao sao declarados no Maven")));
    }

    @Test
    @DisplayName("C2 do reator: sem modulo com gateway no nome, todo modulo que declara o starter e gateway")
    void semNomeTodoStarterEhGateway() throws Exception {
        pom("", agregador("loja", "borda", "pedidos"));
        pom("borda", servico("borda", GATEWAY));
        pom("pedidos", servico("pedidos", WEB));

        C4Model m = ArchetypeRules.aplicar(reator(), SEM_CATEGORIAS);

        assertTrue(temRelacao(m, "app_borda", "app_pedidos"));
        assertTrue(temRelacao(m, "pessoa", "app_borda"));
        assertFalse(temRelacao(m, "pessoa", "app_pedidos"));
    }

    @Test
    @DisplayName("C2 do reator: a interface recebe o id do modulo que a serve")
    void interfaceRecebeIdDoModulo() throws Exception {
        pom("", agregador("loja", "loja-gateway", "pedidos"));
        pom("loja-gateway", servico("loja-gateway", GATEWAY, "org.webjars:angularjs"));
        pom("pedidos", servico("pedidos", WEB));

        C4Model m = ArchetypeRules.aplicar(reator(), SEM_CATEGORIAS);

        assertEquals(C4Model.Tipo.INTERFACE,
                m.porId("ui_loja_gateway").orElseThrow().tipo());
        assertTrue(m.porId("ui").isEmpty());
        assertTrue(temRelacao(m, "pessoa", "ui_loja_gateway"));
        assertTrue(temRelacao(m, "ui_loja_gateway", "app_loja_gateway"));
    }

}
