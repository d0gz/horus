package io.github.masmangan.assis.pom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduz um PomView para a notacao textual do Mermaid.
 *
 * O diagrama produzido representa a perspectiva arquitetural do pom cru em
 * quatro blocos: heranca, configuracao, build e dependencias, mais os modulos
 * quando o projeto e agregador.
 *
 * Arestas cheias representam dependencia. Arestas pontilhadas representam
 * relacoes de composicao do proprio arquivo: heranca do parent e pertencimento
 * dos blocos ao projeto. A distincao e proposital: sao naturezas diferentes e
 * nao devem ser lidas da mesma forma.
 *
 * Este renderizador nao substitui PomDependencyExtractor.toMermaid, que
 * permanece como o primeiro incremento do modulo, restrito a lista de
 * dependencias.
 */
public final class PomMermaidRenderer {

    private static final String IDENT = "    ";
    private static final String IDENT_INTERNO = "        ";

    private PomMermaidRenderer() {
    }

    /** Renderiza usando a tabela de categorias versionada no repositorio. */
    public static String render(PomView view) {
        return render(view, CategoryTable.padrao());
    }

    /**
     * Renderiza usando a tabela informada.
     *
     * As dependencias sao agrupadas por categoria e recebem a cor definida na
     * tabela. A categoria "nao classificada" e sempre emitida por ultimo, para
     * ser lida como residuo e nao como mais um grupo equivalente aos demais.
     */
    public static String render(PomView view, CategoryResolver tabela) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        renderHeranca(sb, view);
        renderModulos(sb, view);
        renderConfiguracao(sb, view);
        renderBuild(sb, view);
        renderDependencias(sb, view, tabela);

        return sb.toString();
    }

    /**
     * O parent so aparece quando existe. O rotulo da aresta informa quantas
     * dependencias tem a versao gerenciada por ele, tornando visivel no proprio
     * diagrama a informacao que o pom cru nao carrega.
     */
    private static void renderHeranca(StringBuilder sb, PomView view) {
        view.parent().ifPresent(parent ->
                sb.append(IDENT).append("parent[\"").append(esc(parent.rotulo())).append("\"]\n"));

        sb.append(IDENT).append("project[\"").append(esc(view.projeto().rotulo())).append("\"]\n");

        if (view.parent().isPresent()) {
            long gerenciadas = view.dependencias().stream()
                    .filter(PomView.DependenciaView::versaoGerenciada)
                    .count();

            sb.append(IDENT)
                    .append("parent -.->|gerencia ").append(gerenciadas)
                    .append(" de ").append(view.dependencias().size())
                    .append(" versoes| project\n");
        }
    }

    private static void renderModulos(StringBuilder sb, PomView view) {
        if (view.modulos().isEmpty()) {
            return;
        }
        sb.append('\n').append(IDENT).append("subgraph mod[\"Modulos\"]\n");
        for (int i = 0; i < view.modulos().size(); i++) {
            sb.append(IDENT_INTERNO).append("mod").append(i)
                    .append("[\"").append(esc(view.modulos().get(i))).append("\"]\n");
        }
        sb.append(IDENT).append("end\n");
        sb.append(IDENT).append("project -.-> mod\n");
    }

    private static void renderConfiguracao(StringBuilder sb, PomView view) {
        if (view.propriedades().isEmpty()) {
            return;
        }
        sb.append('\n').append(IDENT).append("subgraph cfg[\"Configuracao\"]\n");
        int i = 0;
        // A ordem das chaves ja foi normalizada alfabeticamente pelo PomView.
        for (Map.Entry<String, String> e : view.propriedades().entrySet()) {
            sb.append(IDENT_INTERNO).append("cfg").append(i++)
                    .append("[\"").append(esc(e.getKey()))
                    .append(" = ").append(esc(e.getValue())).append("\"]\n");
        }
        sb.append(IDENT).append("end\n");
        sb.append(IDENT).append("project -.-> cfg\n");
    }

    private static void renderBuild(StringBuilder sb, PomView view) {
        if (view.plugins().isEmpty()) {
            return;
        }
        sb.append('\n').append(IDENT).append("subgraph bld[\"Build\"]\n");
        for (int i = 0; i < view.plugins().size(); i++) {
            sb.append(IDENT_INTERNO).append("bld").append(i)
                    .append("[\"").append(esc(view.plugins().get(i).coordenada())).append("\"]\n");
        }
        sb.append(IDENT).append("end\n");
        sb.append(IDENT).append("project -.-> bld\n");
    }

    /**
     * Agrupa as dependencias por categoria, emite um subgraph por grupo usado e
     * associa cada no a uma classe de estilo.
     *
     * O rotulo textual do subgraph acompanha a cor de proposito: quem imprime em
     * preto e branco ou nao distingue cores continua conseguindo ler o grupo. A
     * cor acelera a leitura, o rotulo garante ela.
     */
    private static void renderDependencias(StringBuilder sb, PomView view, CategoryResolver tabela) {
        if (view.dependencias().isEmpty()) {
            return;
        }

        Map<String, List<Integer>> porCategoria = agrupar(view, tabela);

        for (Map.Entry<String, List<Integer>> grupo : porCategoria.entrySet()) {
            sb.append('\n').append(IDENT)
                    .append("subgraph ").append(idDeGrupo(grupo.getKey()))
                    .append("[\"").append(esc(tabela.rotuloDe(grupo.getKey()))).append("\"]\n");

            for (int i : grupo.getValue()) {
                sb.append(IDENT_INTERNO).append("dep").append(i)
                        .append("[\"").append(esc(rotuloDe(view.dependencias().get(i)))).append("\"]\n");
            }
            sb.append(IDENT).append("end\n");
        }

        sb.append('\n');
        for (int i = 0; i < view.dependencias().size(); i++) {
            PomView.DependenciaView d = view.dependencias().get(i);
            if (PomView.ESCOPO_PADRAO.equals(d.escopo())) {
                sb.append(IDENT).append("project --> dep").append(i).append('\n');
            } else {
                sb.append(IDENT).append("project -->|").append(esc(d.escopo()))
                        .append("| dep").append(i).append('\n');
            }
        }

        sb.append('\n');
        for (String categoria : porCategoria.keySet()) {
            CategoryTable.Cor cor = tabela.corDe(categoria);
            sb.append(IDENT).append("classDef ").append(idDeClasse(categoria))
                    .append(" fill:").append(cor.preenchimento())
                    .append(",stroke:").append(cor.borda())
                    .append(",color:").append(cor.texto()).append('\n');
        }
        for (Map.Entry<String, List<Integer>> grupo : porCategoria.entrySet()) {
            sb.append(IDENT).append("class ")
                    .append(grupo.getValue().stream().map(i -> "dep" + i).collect(Collectors.joining(",")))
                    .append(' ').append(idDeClasse(grupo.getKey())).append('\n');
        }
    }

    /**
     * Agrupa preservando a ordem de primeira aparicao das categorias, o que torna
     * a saida estavel sem depender da ordem das regras na tabela. A categoria
     * residual e movida para o fim.
     */
    private static Map<String, List<Integer>> agrupar(PomView view, CategoryResolver tabela) {
        Map<String, List<Integer>> porCategoria = new LinkedHashMap<>();
        for (int i = 0; i < view.dependencias().size(); i++) {
            PomView.DependenciaView d = view.dependencias().get(i);
            String categoria = tabela.categoriaDe(d.groupId(), d.artifactId());
            porCategoria.computeIfAbsent(categoria, k -> new ArrayList<>()).add(i);
        }

        List<Integer> residual = porCategoria.remove(CategoryResolver.NAO_CLASSIFICADA);
        if (residual != null) {
            porCategoria.put(CategoryResolver.NAO_CLASSIFICADA, residual);
        }
        return porCategoria;
    }

    private static String rotuloDe(PomView.DependenciaView d) {
        String rotulo = d.coordenada();
        if (!d.versaoGerenciada()) {
            rotulo += ":" + d.version();
        }
        if (d.opcional()) {
            rotulo += " (optional)";
        }
        return rotulo;
    }

    /** Identificadores do Mermaid nao aceitam hifen, comum nos ids de categoria. */
    private static String sanitizar(String categoria) {
        return categoria.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static String idDeGrupo(String categoria) {
        return "grp_" + sanitizar(categoria);
    }

    private static String idDeClasse(String categoria) {
        return "cor_" + sanitizar(categoria);
    }

    /**
     * Aspas duplas encerrariam o rotulo e quebrariam a sintaxe. O Mermaid aceita
     * entidades no formato #nnn; e #quot; dentro de rotulos entre aspas.
     */
    static String esc(String texto) {
        return (texto == null) ? "" : texto.replace("\"", "#quot;");
    }
}
