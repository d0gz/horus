package io.github.masmangan.assis.pom;

import java.util.Optional;

/**
 * Resolve a categoria arquitetural de uma dependencia Maven.
 *
 * A categoria e usada para agrupar e colorir os elementos do diagrama. Ela e
 * independente da fronteira do sistema, que vem do escopo Maven: uma dependencia
 * pode ser de Teste e estar fora do sistema, ou de Persistencia e estar dentro.
 *
 * Existe como interface para que a origem da classificacao possa mudar sem
 * afetar quem a consome. A implementacao de referencia e CategoryTable, que le
 * uma tabela local versionada no repositorio e portanto produz sempre o mesmo
 * resultado para a mesma entrada.
 */
public interface CategoryResolver {

    /** Categoria atribuida quando nenhuma regra casa. Nunca e um palpite. */
    String NAO_CLASSIFICADA = "nao-classificada";

    /**
     * Devolve o identificador da categoria da dependencia informada.
     * Nunca devolve nulo: na ausencia de regra aplicavel, devolve
     * {@link #NAO_CLASSIFICADA}.
     */
    String categoriaDe(String groupId, String artifactId);

    /** Rotulo legivel da categoria. Por contrato, devolve o proprio identificador se desconhecida. */
    String rotuloDe(String categoria);

    /** Cores da categoria. Devolve uma cor neutra se desconhecida. */
    CategoryTable.Cor corDe(String categoria);

    /**
     * Padrao da regra generica (de familia) que classificou a dependencia, como
     * "org.springframework*", ou vazio quando a classificacao nao veio de uma.
     * Uma regra generica e um palpite pela familia, nao a classificacao da
     * biblioteca; o C2 avisa o usuario disso.
     */
    default Optional<String> regraGenericaDe(String groupId, String artifactId) {
        return Optional.empty();
    }
}
