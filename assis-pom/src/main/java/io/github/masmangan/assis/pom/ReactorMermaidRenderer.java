package io.github.masmangan.assis.pom;

import java.util.List;

/**
 * Renderiza a visao de reator: um no por modulo do projeto multimodulo.
 *
 * Esta e a visao arquitetural do repositorio. As arestas entre modulos existem
 * somente quando ha dependencia Maven declarada entre eles. Um reator sem
 * arestas nao e um defeito do diagrama: significa que os modulos nao se
 * referenciam em tempo de compilacao, o que e comum em microsservicos que
 * se comunicam por HTTP em tempo de execucao.
 */
public final class ReactorMermaidRenderer {

    private static final String IDENT = "    ";
    private static final String IDENT_INTERNO = "        ";

    private ReactorMermaidRenderer() {
    }

    public static String render(ReactorView reator) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        List<ReactorView> todos = reator.achatado();

        sb.append(IDENT).append("subgraph rt[\"")
                .append(PomMermaidRenderer.esc(reator.pom().projeto().artifactId()))
                .append("\"]\n");

        for (int i = 0; i < todos.size(); i++) {
            ReactorView m = todos.get(i);
            sb.append(IDENT_INTERNO).append(idDe(i))
                    .append("[\"").append(PomMermaidRenderer.esc(rotuloDe(m))).append("\"]\n");
        }
        sb.append(IDENT).append("end\n");

        for (String ausente : reator.modulosAusentes()) {
            sb.append(IDENT).append("ausente_").append(sanitizar(ausente))
                    .append("[\"").append(PomMermaidRenderer.esc(ausente))
                    .append(" (nao encontrado)\"]\n");
        }

        List<ReactorView.ArestaInterna> arestas = reator.arestasInternas();

        if (arestas.isEmpty()) {
            // Comentario do Mermaid: registra no proprio artefato que a ausencia de
            // arestas foi constatada, e nao um bloco esquecido.
            sb.append('\n').append(IDENT)
                    .append("%% nenhuma dependencia Maven declarada entre os modulos\n");
        } else {
            sb.append('\n');
            for (ReactorView.ArestaInterna a : arestas) {
                int origem = indiceDe(todos, a.origem());
                int destino = indiceDe(todos, a.destino());
                if (origem < 0 || destino < 0) {
                    continue;
                }
                if (PomView.ESCOPO_PADRAO.equals(a.escopo())) {
                    sb.append(IDENT).append(idDe(origem)).append(" --> ")
                            .append(idDe(destino)).append('\n');
                } else {
                    sb.append(IDENT).append(idDe(origem)).append(" -->|")
                            .append(PomMermaidRenderer.esc(a.escopo())).append("| ")
                            .append(idDe(destino)).append('\n');
                }
            }
        }

        return sb.toString();
    }

    /** O rotulo traz a densidade de cada modulo, util para localizar onde esta o peso. */
    private static String rotuloDe(ReactorView m) {
        int n = m.pom().dependencias().size();
        return m.pom().projeto().artifactId() + " (" + n + " deps)";
    }

    private static int indiceDe(List<ReactorView> todos, String artifactId) {
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).pom().projeto().artifactId().equals(artifactId)) {
                return i;
            }
        }
        return -1;
    }

    private static String idDe(int i) {
        return "mod" + i;
    }

    private static String sanitizar(String texto) {
        return texto.replaceAll("[^A-Za-z0-9]", "_");
    }
}
