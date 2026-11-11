package io.github.masmangan.assis.pom;

import io.github.masmangan.assis.pom.C4Model.Elemento;
import io.github.masmangan.assis.pom.C4Model.Procedencia;
import io.github.masmangan.assis.pom.C4Model.Relacao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Desenha um C4Model na notacao do Mermaid.
 *
 * A renderizacao usa flowchart, e nao os tipos C4Container nativos, por duas
 * razoes. A primeira e tecnica: a propria documentacao do Mermaid classifica o
 * suporte ao C4 como experimental, sujeito a mudanca de sintaxe. A segunda e
 * conceitual: o Modelo C4 define um conjunto comum de abstracoes, nao uma
 * notacao obrigatoria, de modo que desenhar essas abstracoes em flowchart e
 * legitimo e da controle total sobre forma, cor e agrupamento.
 *
 * Convencoes adotadas:
 *   - a fronteira do sistema e um subgraph; atores e sistemas externos ficam fora;
 *   - cada tipo de elemento tem forma propria, e nao apenas cor;
 *   - a tecnologia aparece entre colchetes, como nos diagramas C4 de referencia;
 *   - o que nao e confirmavel recebe marcador textual, nao apenas traco;
 *   - o que a ferramenta nao consegue derivar vira comentario no proprio arquivo.
 */
public final class C4MermaidRenderer {

    private static final String IDENT = "    ";
    private static final String IDENT_INTERNO = "        ";

    /** Sufixo textual: a procedencia nunca e comunicada so pela cor. */
    static final String MARCA_NAO_CONFIRMAVEL = " (nao confirmado)";
    static final String MARCA_DECLARADO = " (declarado)";

    private C4MermaidRenderer() {
    }

    public static String render(C4Model modelo) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TB\n");

        List<Elemento> fora = modelo.elementos().stream()
                .filter(Elemento::foraDaFronteira).toList();
        List<Elemento> dentro = modelo.elementos().stream()
                .filter(e -> !e.foraDaFronteira()).toList();

        // Atores e sistemas de terceiros nao pertencem ao sistema analisado.
        for (Elemento e : fora) {
            sb.append(IDENT).append(no(e)).append('\n');
        }

        if (!dentro.isEmpty()) {
            sb.append('\n').append(IDENT).append("subgraph sys[\"")
                    .append(esc(modelo.nomeDoSistema())).append("\"]\n");
            for (Elemento e : dentro) {
                sb.append(IDENT_INTERNO).append(no(e)).append('\n');
            }
            sb.append(IDENT).append("end\n");
        }

        if (!modelo.relacoes().isEmpty()) {
            sb.append('\n');
            for (Relacao r : modelo.relacoes()) {
                // Relacao nao confirmada: seta tracejada e marcador no texto (D13).
                String seta = r.procedencia() == Procedencia.NAO_CONFIRMAVEL ? " -.->|\"" : " -->|\"";
                sb.append(IDENT).append(r.origem()).append(seta)
                        .append(rotuloDeRelacao(r)).append("\"| ")
                        .append(r.destino()).append('\n');
            }
        }

        sb.append('\n');
        sb.append(IDENT).append("classDef pessoa fill:#08427b,stroke:#052e56,color:#ffffff\n");
        sb.append(IDENT).append("classDef aplicacao fill:#1168bd,stroke:#0b4884,color:#ffffff\n");
        // So quando ha interface, para nao alterar os diagramas que nao tem uma.
        if (modelo.temTipo(C4Model.Tipo.INTERFACE)) {
            sb.append(IDENT).append("classDef interface fill:#1168bd,stroke:#0b4884,color:#ffffff\n");
        }
        sb.append(IDENT).append("classDef banco fill:#1168bd,stroke:#0b4884,color:#ffffff\n");
        sb.append(IDENT).append("classDef mensageria fill:#1168bd,stroke:#0b4884,color:#ffffff\n");
        sb.append(IDENT).append("classDef externo fill:#8c8c8c,stroke:#5e5e5e,color:#ffffff\n");
        sb.append(IDENT).append("classDef incerto fill:#c9d7e4,stroke:#1168bd,"
                + "color:#0b2e4f,stroke-dasharray: 4 3\n");

        for (Elemento e : modelo.elementos()) {
            sb.append(IDENT).append("class ").append(e.id()).append(' ')
                    .append(classeDe(e)).append('\n');
        }

        // As notas registram no artefato o que a fonte lida nao permite derivar.
        if (!modelo.notas().isEmpty()) {
            sb.append('\n');
            for (String nota : modelo.notas()) {
                sb.append(IDENT).append("%% ").append(nota.replace("\n", " ")).append('\n');
            }
        }

        // Incremento B: o que o usuario precisa para escrever o catalogo. So texto;
        // nao altera o desenho acima.
        List<String> comentarios = comentarios(modelo);
        if (!comentarios.isEmpty()) {
            sb.append('\n');
            for (String linha : comentarios) {
                sb.append(IDENT).append("%% ").append(linha).append('\n');
            }
        }

        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Comentarios do fim do arquivo (incremento B)
    // -----------------------------------------------------------------

    static final String TITULO_CONTEINERES = "---- Conteineres deste diagrama (use estes ids no catalogo) ----";
    static final String TITULO_NAO_IDENTIFICADAS = "---- Dependencias nao identificadas (podem ser conteineres) ----";

    /**
     * Linhas das secoes de comentario, sem o prefixo "%% ", na ordem fixa das
     * secoes; secao vazia nao gera linha alguma. Dentro de cada secao, ordem
     * alfabetica, para que a saida nao dependa da ordem de derivacao.
     */
    public static List<String> comentarios(C4Model modelo) {
        List<String> linhas = new ArrayList<>();

        List<Elemento> elementos = modelo.elementos().stream()
                .sorted(Comparator.comparing(Elemento::id)).toList();
        if (!elementos.isEmpty()) {
            linhas.add(TITULO_CONTEINERES);
            for (Elemento e : elementos) {
                linhas.add("  " + e.id() + "  " + e.nome());
            }
        }

        List<C4Model.DependenciaNaoIdentificada> deps = modelo.naoIdentificadas().stream()
                .sorted(Comparator.comparing(C4Model.DependenciaNaoIdentificada::coordenada)).toList();
        if (!deps.isEmpty()) {
            linhas.add(TITULO_NAO_IDENTIFICADAS);
            // Uma vez so: e igual para todas; o "para ignorar" muda com a coordenada.
            linhas.add("  para incluir: add container | ext_... | Nome | Tecnologia | Descricao");
            for (C4Model.DependenciaNaoIdentificada d : deps) {
                linhas.add("  " + d.coordenada() + " (" + origem(d) + ")");
                linhas.add("    para ignorar: remove dependency | " + d.coordenada());
            }
        }
        return linhas;
    }

    private static String origem(C4Model.DependenciaNaoIdentificada d) {
        StringBuilder sb = new StringBuilder();
        if (!d.modulos().isEmpty()) {
            sb.append(d.modulos().size() == 1 ? "modulo " : "modulos ")
                    .append(String.join(", ", d.modulos())).append("; ");
        }
        sb.append("categoria ").append(d.categoria());
        if (d.regraGenerica() != null) {
            sb.append(" pela regra generica ").append(d.regraGenerica());
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Elementos
    // -----------------------------------------------------------------

    /** Cada tipo tem forma propria, para que o desenho nao dependa so da cor. */
    private static String no(Elemento e) {
        String rotulo = rotuloDeElemento(e);
        return switch (e.tipo()) {
            case PESSOA -> e.id() + "([\"" + rotulo + "\"])";
            case BANCO -> e.id() + "[(\"" + rotulo + "\")]";
            case MENSAGERIA -> e.id() + "{{\"" + rotulo + "\"}}";
            case INTERFACE -> e.id() + "(\"" + rotulo + "\")";
            case APLICACAO, SISTEMA_EXTERNO -> e.id() + "[\"" + rotulo + "\"]";
        };
    }

    /**
     * Tres linhas, como nos diagramas C4 de referencia: nome, tecnologia entre
     * colchetes e descricao. A marca de procedencia acompanha o nome, em texto.
     */
    static String rotuloDeElemento(Elemento e) {
        StringBuilder sb = new StringBuilder(esc(e.nome()));

        if (e.procedencia() == Procedencia.NAO_CONFIRMAVEL) {
            sb.append(MARCA_NAO_CONFIRMAVEL);
        } else if (e.procedencia() == Procedencia.DECLARADO) {
            sb.append(MARCA_DECLARADO);
        }

        if (e.tecnologia() != null && !e.tecnologia().isBlank()) {
            sb.append("<br/>[").append(esc(e.tecnologia())).append(']');
        }
        if (e.descricao() != null && !e.descricao().isBlank()) {
            sb.append("<br/>").append(esc(e.descricao()));
        }
        return sb.toString();
    }

    static String rotuloDeRelacao(Relacao r) {
        StringBuilder sb = new StringBuilder();
        if (r.descricao() != null && !r.descricao().isBlank()) {
            sb.append(esc(r.descricao()));
        }
        if (r.procedencia() == Procedencia.NAO_CONFIRMAVEL) {
            sb.append(sb.length() > 0 ? MARCA_NAO_CONFIRMAVEL : MARCA_NAO_CONFIRMAVEL.trim());
        }
        if (r.tecnologia() != null && !r.tecnologia().isBlank()) {
            if (sb.length() > 0) {
                sb.append("<br/>");
            }
            sb.append('[').append(esc(r.tecnologia())).append(']');
        }
        return sb.toString();
    }

    private static String classeDe(Elemento e) {
        if (e.procedencia() == Procedencia.NAO_CONFIRMAVEL) {
            return "incerto";
        }
        return switch (e.tipo()) {
            case PESSOA -> "pessoa";
            case APLICACAO -> "aplicacao";
            case INTERFACE -> "interface";
            case BANCO -> "banco";
            case MENSAGERIA -> "mensageria";
            case SISTEMA_EXTERNO -> "externo";
        };
    }

    /**
     * Aspas encerrariam o rotulo e o sinal de igual e lido pelo Mermaid como
     * inicio de aresta. Os dois sao substituidos pelas entidades equivalentes.
     */
    static String esc(String texto) {
        return (texto == null) ? "" : texto.replace("\"", "#quot;").replace("=", "#61;");
    }
}
