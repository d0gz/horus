package io.github.masmangan.assis.pom;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Representacao em memoria de um pom.xml cru, independente do Maven e do Mermaid.
 *
 * Esta classe e o modelo estavel do modulo: o extrator alimenta o PomView e o
 * renderizador consome o PomView. Trocar a biblioteca de leitura ou a notacao de
 * saida nao exige alterar o outro lado.
 *
 * Escopo desta versao: apenas o pom cru. Nenhuma resolucao de heranca, de
 * dependencyManagement ou de dependencias transitivas e realizada aqui. As
 * versoes ausentes sao registradas como gerenciadas, nunca inventadas.
 */
public final class PomView {

    /** Grupo assumido por plugins que omitem groupId, conforme convencao do Maven. */
    static final String GRUPO_PADRAO_DE_PLUGIN = "org.apache.maven.plugins";

    /** Escopo assumido por dependencias que omitem o elemento scope. */
    static final String ESCOPO_PADRAO = "compile";

    // -----------------------------------------------------------------
    // Tipos de apoio
    // -----------------------------------------------------------------

    /** Coordenada Maven completa de um artefato. */
    public record Coordenada(String groupId, String artifactId, String version) {
        public String rotulo() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }

    /**
     * Dependencia declarada, com escopo ja normalizado.
     *
     * O campo version e nulo quando o pom cru nao o declara, situacao comum em
     * projetos que herdam versoes do parent. Nesse caso versaoGerenciada() e
     * verdadeiro, e cabe a camada de apresentacao dizer isso ao leitor em vez de
     * omitir a informacao em silencio.
     */
    public record DependenciaView(String groupId, String artifactId, String version,
                                  String escopo, boolean opcional) {

        public boolean versaoGerenciada() {
            return version == null || version.isBlank();
        }

        public String coordenada() {
            return groupId + ":" + artifactId;
        }
    }

    /** Plugin declarado em build/plugins. */
    public record PluginView(String groupId, String artifactId, String version) {
        public String coordenada() {
            return groupId + ":" + artifactId;
        }
    }

    // -----------------------------------------------------------------
    // Estado
    // -----------------------------------------------------------------

    private final Coordenada projeto;
    private final Coordenada parent;
    private final Map<String, String> propriedades;
    private final List<DependenciaView> dependencias;
    private final List<PluginView> plugins;
    private final List<String> modulos;

    private PomView(Coordenada projeto, Coordenada parent, Map<String, String> propriedades,
                    List<DependenciaView> dependencias, List<PluginView> plugins,
                    List<String> modulos) {
        this.projeto = projeto;
        this.parent = parent;
        this.propriedades = Collections.unmodifiableMap(propriedades);
        this.dependencias = List.copyOf(dependencias);
        this.plugins = List.copyOf(plugins);
        this.modulos = List.copyOf(modulos);
    }

    // -----------------------------------------------------------------
    // Construcao
    // -----------------------------------------------------------------

    /** Converte o Model devolvido pelo MavenXpp3Reader neste modelo. */
    public static PomView de(Model model) {
        return new PomView(
                lerCoordenadaDoProjeto(model),
                lerParent(model),
                lerPropriedades(model),
                lerDependencias(model),
                lerPlugins(model),
                lerModulos(model));
    }

    /**
     * groupId e version podem ser omitidos quando o projeto tem parent: nesse
     * caso o Maven os herda, e a coordenada efetiva do projeto usa os valores do
     * parent.
     */
    private static Coordenada lerCoordenadaDoProjeto(Model model) {
        Parent p = model.getParent();

        String groupId = (model.getGroupId() != null)
                ? model.getGroupId()
                : (p != null ? p.getGroupId() : "unknown");

        String version = (model.getVersion() != null)
                ? model.getVersion()
                : (p != null ? p.getVersion() : "unknown");

        return new Coordenada(groupId, model.getArtifactId(), version);
    }

    private static Coordenada lerParent(Model model) {
        Parent p = model.getParent();
        if (p == null) {
            return null;
        }
        return new Coordenada(p.getGroupId(), p.getArtifactId(), p.getVersion());
    }

    /**
     * Model.getProperties() devolve java.util.Properties, que estende Hashtable e
     * nao especifica ordem de iteracao. Sem ordenacao explicita o diagrama poderia
     * mudar entre execucoes sobre o mesmo pom, o que quebraria a reprodutibilidade
     * da ferramenta. As chaves sao portanto ordenadas alfabeticamente.
     */
    private static Map<String, String> lerPropriedades(Model model) {
        Map<String, String> ordenadas = new LinkedHashMap<>();
        Properties props = model.getProperties();
        if (props == null) {
            return ordenadas;
        }
        for (String chave : new TreeSet<>(props.stringPropertyNames())) {
            ordenadas.put(chave, props.getProperty(chave));
        }
        return ordenadas;
    }

    /**
     * Somente o bloco dependencies e considerado. O bloco dependencyManagement
     * declara versoes para uso eventual, nao dependencias do projeto, e por isso
     * fica de fora.
     */
    private static List<DependenciaView> lerDependencias(Model model) {
        List<DependenciaView> resultado = new ArrayList<>();
        if (model.getDependencies() == null) {
            return resultado;
        }
        for (Dependency d : model.getDependencies()) {
            String escopo = (d.getScope() != null && !d.getScope().isBlank())
                    ? d.getScope()
                    : ESCOPO_PADRAO;

            resultado.add(new DependenciaView(
                    d.getGroupId(), d.getArtifactId(), d.getVersion(), escopo, d.isOptional()));
        }
        return resultado;
    }

    private static List<PluginView> lerPlugins(Model model) {
        List<PluginView> resultado = new ArrayList<>();
        Build build = model.getBuild();
        if (build == null || build.getPlugins() == null) {
            return resultado;
        }
        for (Plugin p : build.getPlugins()) {
            String groupId = (p.getGroupId() != null && !p.getGroupId().isBlank())
                    ? p.getGroupId()
                    : GRUPO_PADRAO_DE_PLUGIN;

            resultado.add(new PluginView(groupId, p.getArtifactId(), p.getVersion()));
        }
        return resultado;
    }

    private static List<String> lerModulos(Model model) {
        return (model.getModules() == null) ? List.of() : new ArrayList<>(model.getModules());
    }

    // -----------------------------------------------------------------
    // Consulta
    // -----------------------------------------------------------------

    public Coordenada projeto() {
        return projeto;
    }

    public Optional<Coordenada> parent() {
        return Optional.ofNullable(parent);
    }

    public Map<String, String> propriedades() {
        return propriedades;
    }

    public List<DependenciaView> dependencias() {
        return dependencias;
    }

    public List<PluginView> plugins() {
        return plugins;
    }

    public List<String> modulos() {
        return modulos;
    }
}
