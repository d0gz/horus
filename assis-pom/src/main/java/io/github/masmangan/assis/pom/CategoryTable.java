package io.github.masmangan.assis.pom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabela local de classificacao de dependencias Maven.
 *
 * Le o arquivo categorias.json, versionado no repositorio, e resolve a categoria
 * de cada dependencia percorrendo as regras na ordem de declaracao: a primeira
 * que casar vence. Como a tabela e um arquivo do proprio projeto, a mesma entrada
 * produz sempre a mesma saida, e o criterio de cada classificacao fica auditavel
 * no campo "nota" de cada regra.
 *
 * A carga e tolerante a falha por decisao de projeto. Uma tabela ausente,
 * malformada ou de versao de esquema incompativel nao interrompe a geracao do
 * diagrama: todas as dependencias passam a ser "nao classificada", o que degrada
 * a informacao sem perder o artefato. O metodo carregou() permite ao chamador
 * avisar o usuario.
 */
public final class CategoryTable implements CategoryResolver {

    /** Unica versao de esquema reconhecida por esta implementacao. */
    static final int ESQUEMA_SUPORTADO = 1;

    static final String RECURSO_PADRAO = "/categorias.json";

    /** Cor de um grupo no diagrama. */
    public record Cor(String preenchimento, String borda, String texto) {
    }

    /** Regra de casamento. Ao menos um criterio de grupo precisa estar presente. */
    private record Regra(String groupId, String groupIdPrefixo,
                         String artifactId, String artifactIdPrefixo,
                         String categoria) {

        boolean casa(String gid, String aid) {
            if (groupId == null && groupIdPrefixo == null) {
                return false;
            }
            if (groupId != null && !groupId.equals(gid)) {
                return false;
            }
            if (groupIdPrefixo != null && (gid == null || !gid.startsWith(groupIdPrefixo))) {
                return false;
            }
            if (artifactId != null && !artifactId.equals(aid)) {
                return false;
            }
            if (artifactIdPrefixo != null && (aid == null || !aid.startsWith(artifactIdPrefixo))) {
                return false;
            }
            return true;
        }
    }

    private static final Cor COR_NEUTRA = new Cor("#ffffff", "#94a3b8", "#334155");

    private final boolean carregou;
    private final String padrao;
    private final List<Regra> regras;
    private final Map<String, String> rotulos;
    private final Map<String, Cor> cores;

    private CategoryTable(boolean carregou, String padrao, List<Regra> regras,
                          Map<String, String> rotulos, Map<String, Cor> cores) {
        this.carregou = carregou;
        this.padrao = padrao;
        this.regras = regras;
        this.rotulos = rotulos;
        this.cores = cores;
    }

    // -----------------------------------------------------------------
    // Construcao
    // -----------------------------------------------------------------

    /** Tabela do repositorio, lida de src/main/resources/categorias.json. */
    public static CategoryTable padrao() {
        return deClasspath(RECURSO_PADRAO);
    }

    /** Le a tabela de um recurso do classpath. Nunca lanca excecao. */
    public static CategoryTable deClasspath(String recurso) {
        try (InputStream in = CategoryTable.class.getResourceAsStream(recurso)) {
            if (in == null) {
                return vazia();
            }
            return deTexto(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return vazia();
        }
    }

    /** Le a tabela a partir do texto JSON. Nunca lanca excecao. */
    public static CategoryTable deTexto(String json) {
        try {
            JsonNode raiz = new ObjectMapper().readTree(json);

            // O campo existe justamente para permitir recusar um arquivo antigo
            // em vez de interpreta-lo com as regras erradas em silencio.
            if (raiz.path("schemaVersion").asInt(-1) != ESQUEMA_SUPORTADO) {
                return vazia();
            }

            String padrao = texto(raiz, "padrao");
            if (padrao == null) {
                padrao = NAO_CLASSIFICADA;
            }

            Map<String, String> rotulos = new LinkedHashMap<>();
            Map<String, Cor> cores = new LinkedHashMap<>();
            for (JsonNode c : raiz.path("categorias")) {
                String id = texto(c, "id");
                if (id == null) {
                    continue;
                }
                rotulos.put(id, texto(c, "rotulo") != null ? texto(c, "rotulo") : id);
                JsonNode cor = c.path("cor");
                cores.put(id, new Cor(
                        texto(cor, "preenchimento") != null ? texto(cor, "preenchimento") : COR_NEUTRA.preenchimento(),
                        texto(cor, "borda") != null ? texto(cor, "borda") : COR_NEUTRA.borda(),
                        texto(cor, "texto") != null ? texto(cor, "texto") : COR_NEUTRA.texto()));
            }

            List<Regra> regras = new ArrayList<>();
            for (JsonNode r : raiz.path("regras")) {
                String categoria = texto(r, "categoria");
                if (categoria == null) {
                    continue;
                }
                regras.add(new Regra(
                        texto(r, "groupId"),
                        texto(r, "groupIdPrefixo"),
                        texto(r, "artifactId"),
                        texto(r, "artifactIdPrefixo"),
                        categoria));
            }

            return new CategoryTable(true, padrao, List.copyOf(regras), rotulos, cores);

        } catch (Exception e) {
            return vazia();
        }
    }

    /** Tabela sem regras: tudo cai na categoria padrao. */
    private static CategoryTable vazia() {
        return new CategoryTable(false, NAO_CLASSIFICADA, List.of(),
                Map.of(NAO_CLASSIFICADA, "Nao classificada"),
                Map.of(NAO_CLASSIFICADA, COR_NEUTRA));
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return (valor == null || valor.isNull()) ? null : valor.asText();
    }

    // -----------------------------------------------------------------
    // Consulta
    // -----------------------------------------------------------------

    /** Indica se a tabela foi lida com sucesso. Falso significa degradacao, nao erro fatal. */
    public boolean carregou() {
        return carregou;
    }

    @Override
    public String categoriaDe(String groupId, String artifactId) {
        for (Regra r : regras) {
            if (r.casa(groupId, artifactId)) {
                return r.categoria();
            }
        }
        return padrao;
    }

    @Override
    public String rotuloDe(String categoria) {
        return rotulos.getOrDefault(categoria, categoria);
    }

    @Override
    public Cor corDe(String categoria) {
        return cores.getOrDefault(categoria, COR_NEUTRA);
    }
}
