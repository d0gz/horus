package io.github.masmangan.assis.pom;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reator Maven: um pom raiz e, recursivamente, os poms dos modulos que ele declara.
 *
 * A travessia e puramente local. O elemento &lt;module&gt; do Maven e um caminho de
 * diretorio relativo ao pom que o declara, de modo que todos os poms de um projeto
 * multimodulo estao no proprio repositorio. Nenhuma rede, nenhum repositorio local
 * e nenhuma instalacao do Maven sao necessarios.
 *
 * Modulos que nao puderem ser lidos sao registrados como ausentes em vez de
 * interromper a travessia: um reator parcial ainda e informacao util.
 */
public final class ReactorView {

    /** Limite de profundidade da travessia, protecao contra hierarquias patologicas. */
    static final int PROFUNDIDADE_MAXIMA = 10;

    private final Path caminho;
    private final PomView pom;
    private final List<ReactorView> modulos;
    private final List<String> modulosAusentes;

    private ReactorView(Path caminho, PomView pom, List<ReactorView> modulos,
                        List<String> modulosAusentes) {
        this.caminho = caminho;
        this.pom = pom;
        this.modulos = List.copyOf(modulos);
        this.modulosAusentes = List.copyOf(modulosAusentes);
    }

    // -----------------------------------------------------------------
    // Travessia
    // -----------------------------------------------------------------

    public static ReactorView de(Path pomRaiz) throws IOException, XmlPullParserException {
        Path raiz = pomRaiz.toAbsolutePath().normalize();
        return percorrer(raiz, raiz.getParent(), new HashSet<>(), 0);
    }

    private static ReactorView percorrer(Path pomAtual, Path limite,
                                         Set<Path> visitados, int profundidade)
            throws IOException, XmlPullParserException {

        visitados.add(pomAtual);
        PomView view = PomView.de(PomDependencyExtractor.readPom(pomAtual));

        List<ReactorView> filhos = new ArrayList<>();
        List<String> ausentes = new ArrayList<>();

        if (profundidade < PROFUNDIDADE_MAXIMA) {
            for (String modulo : view.modulos()) {
                Path pomFilho = resolver(pomAtual.getParent(), modulo);

                if (pomFilho == null || !dentroDoLimite(pomFilho, limite)
                        || !Files.isRegularFile(pomFilho) || visitados.contains(pomFilho)) {
                    ausentes.add(modulo);
                    continue;
                }

                try {
                    filhos.add(percorrer(pomFilho, limite, visitados, profundidade + 1));
                } catch (IOException | XmlPullParserException e) {
                    ausentes.add(modulo);
                }
            }
        } else {
            ausentes.addAll(view.modulos());
        }

        return new ReactorView(pomAtual, view, filhos, ausentes);
    }

    /** O modulo pode apontar para um diretorio ou diretamente para um arquivo de pom. */
    private static Path resolver(Path base, String modulo) {
        if (base == null || modulo == null || modulo.isBlank()) {
            return null;
        }
        Path alvo = base.resolve(modulo).normalize();
        return modulo.endsWith(".xml") ? alvo : alvo.resolve("pom.xml");
    }

    /**
     * Um modulo pode escrever "../outro", o que levaria a leitura para fora do
     * repositorio analisado. A ferramenta recusa esses caminhos.
     */
    private static boolean dentroDoLimite(Path candidato, Path limite) {
        return limite == null || candidato.startsWith(limite);
    }

    // -----------------------------------------------------------------
    // Consulta
    // -----------------------------------------------------------------

    public Path caminho() {
        return caminho;
    }

    public PomView pom() {
        return pom;
    }

    public List<ReactorView> modulos() {
        return modulos;
    }

    public List<String> modulosAusentes() {
        return modulosAusentes;
    }

    public boolean ehAgregador() {
        return !modulos.isEmpty() || !modulosAusentes.isEmpty();
    }

    /** Todos os nos do reator em pre-ordem, comecando pela raiz. */
    public List<ReactorView> achatado() {
        List<ReactorView> todos = new ArrayList<>();
        todos.add(this);
        for (ReactorView m : modulos) {
            todos.addAll(m.achatado());
        }
        return todos;
    }

    /**
     * Dependencias declaradas entre modulos do proprio reator.
     *
     * A deteccao e por igualdade de coordenada: uma dependencia e interna quando
     * seu groupId e artifactId coincidem com os de outro modulo do reator. Nao ha
     * inferencia; uma dependencia de execucao feita por HTTP, por exemplo, nao e
     * declarada no pom e portanto nao aparece aqui.
     */
    public List<ArestaInterna> arestasInternas() {
        List<ReactorView> todos = achatado();
        List<ArestaInterna> arestas = new ArrayList<>();

        for (ReactorView origem : todos) {
            for (PomView.DependenciaView d : origem.pom().dependencias()) {
                for (ReactorView destino : todos) {
                    if (destino == origem) {
                        continue;
                    }
                    PomView.Coordenada c = destino.pom().projeto();
                    if (c.groupId().equals(d.groupId()) && c.artifactId().equals(d.artifactId())) {
                        arestas.add(new ArestaInterna(
                                origem.pom().projeto().artifactId(), c.artifactId(), d.escopo()));
                    }
                }
            }
        }
        return arestas;
    }

    /** Dependencia declarada de um modulo do reator sobre outro. */
    public record ArestaInterna(String origem, String destino, String escopo) {
    }
}
