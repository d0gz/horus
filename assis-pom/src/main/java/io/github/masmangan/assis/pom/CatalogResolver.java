package io.github.masmangan.assis.pom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Catalogo de classificacao fornecido pelo usuario, em arquivo de texto.
 *
 * Complementa a tabela do repositorio sem substitui-la: o catalogo tem
 * precedencia absoluta sobre qualquer regra do categorias.json para as
 * dependencias que ele lista, e delega todo o resto ao resolvedor base.
 *
 * Formato, uma dependencia por linha:
 *
 *   groupId:artifactId | categoria | cor | descricao
 *
 * Os dois primeiros campos sao obrigatorios. A cor e opcional: em branco,
 * preserva a cor que a categoria ja tem; preenchida, sobrescreve o preenchimento
 * mantendo borda e texto da categoria. A descricao e opcional e fica guardada
 * para uso posterior na interface. Linhas em branco e linhas iniciadas por # sao
 * ignoradas.
 *
 * O catalogo NUNCA e gravado no categorias.json. Ele e lido do disco a cada
 * execucao e vive apenas em memoria, de modo que o arquivo do repositorio
 * permanece sendo a unica fonte versionada de classificacao.
 */
public final class CatalogResolver implements CategoryResolver {

    private static final Pattern COR_VALIDA = Pattern.compile("#[0-9a-fA-F]{3,8}");

    /** Uma entrada do catalogo, ja normalizada. */
    public record Entrada(String groupId, String artifactId, String categoria,
                          String rotulo, String cor, String descricao) {

        public String coordenada() {
            return groupId + ":" + artifactId;
        }
    }

    private final CategoryResolver base;
    private final Map<String, Entrada> porCoordenada;
    private final Map<String, String> rotulos;
    private final Map<String, CategoryTable.Cor> cores;
    private final List<String> avisos;

    private CatalogResolver(CategoryResolver base, Map<String, Entrada> porCoordenada,
                            Map<String, String> rotulos, Map<String, CategoryTable.Cor> cores,
                            List<String> avisos) {
        this.base = base;
        this.porCoordenada = porCoordenada;
        this.rotulos = rotulos;
        this.cores = cores;
        this.avisos = List.copyOf(avisos);
    }

    // -----------------------------------------------------------------
    // Construcao
    // -----------------------------------------------------------------

    /** Resolvedor sem catalogo: delega tudo ao base. */
    public static CatalogResolver vazio(CategoryResolver base) {
        return new CatalogResolver(base, Map.of(), Map.of(), Map.of(), List.of());
    }

    /** Le o catalogo de um arquivo. Arquivo ausente ou ilegivel vira aviso, nao erro. */
    public static CatalogResolver de(CategoryResolver base, Path arquivo) {
        try {
            return deTexto(base, Files.readString(arquivo, StandardCharsets.UTF_8));
        } catch (IOException e) {
            CatalogResolver vazio = vazio(base);
            return new CatalogResolver(base, Map.of(), Map.of(), Map.of(),
                    List.of("Catalogo nao pode ser lido: " + arquivo + " (" + e.getMessage() + ")"));
        }
    }

    public static CatalogResolver deTexto(CategoryResolver base, String conteudo) {
        Map<String, Entrada> entradas = new LinkedHashMap<>();
        Map<String, String> rotulos = new LinkedHashMap<>();
        Map<String, CategoryTable.Cor> cores = new LinkedHashMap<>();
        List<String> avisos = new ArrayList<>();

        String[] linhas = conteudo.split("\\R", -1);
        for (int n = 0; n < linhas.length; n++) {
            String linha = linhas[n].trim();
            int numero = n + 1;

            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }

            String[] campos = linha.split("\\|", -1);
            if (campos.length < 2) {
                avisos.add("Linha " + numero + " ignorada: faltam campos separados por | ");
                continue;
            }

            String coordenada = campos[0].trim();
            String[] partes = coordenada.split(":");
            if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) {
                avisos.add("Linha " + numero + " ignorada: coordenada invalida '" + coordenada + "'");
                continue;
            }

            String categoriaBruta = campos[1].trim();
            if (categoriaBruta.isEmpty()) {
                avisos.add("Linha " + numero + " ignorada: categoria vazia");
                continue;
            }

            // O identificador e normalizado para minusculas, de modo que "Teste" e
            // "teste" designem a mesma categoria. O texto original vira o rotulo.
            String categoria = categoriaBruta.toLowerCase(Locale.ROOT);

            String cor = (campos.length >= 3) ? campos[2].trim() : "";
            if (!cor.isEmpty() && !COR_VALIDA.matcher(cor).matches()) {
                avisos.add("Linha " + numero + ": cor '" + cor + "' ignorada, formato invalido");
                cor = "";
            }

            String descricao = (campos.length >= 4) ? campos[3].trim() : "";

            if (entradas.containsKey(coordenada)) {
                avisos.add("Linha " + numero + ": " + coordenada
                        + " ja estava no catalogo, a ultima ocorrencia prevalece");
            }

            entradas.put(coordenada, new Entrada(partes[0], partes[1], categoria,
                    categoriaBruta, cor.isEmpty() ? null : cor,
                    descricao.isEmpty() ? null : descricao));

            // Rotulo so e registrado para categoria que o base desconhece, para nao
            // sobrescrever o nome ja definido no categorias.json.
            if (base.rotuloDe(categoria).equals(categoria)) {
                rotulos.put(categoria, categoriaBruta);
            }

            if (!cor.isEmpty()) {
                CategoryTable.Cor atual = base.corDe(categoria);
                cores.put(categoria, new CategoryTable.Cor(cor, atual.borda(), atual.texto()));
            }
        }

        return new CatalogResolver(base, entradas, rotulos, cores, avisos);
    }

    // -----------------------------------------------------------------
    // Consulta
    // -----------------------------------------------------------------

    @Override
    public String categoriaDe(String groupId, String artifactId) {
        Entrada e = porCoordenada.get(groupId + ":" + artifactId);
        return (e != null) ? e.categoria() : base.categoriaDe(groupId, artifactId);
    }

    @Override
    public String rotuloDe(String categoria) {
        String r = rotulos.get(categoria);
        return (r != null) ? r : base.rotuloDe(categoria);
    }

    @Override
    public CategoryTable.Cor corDe(String categoria) {
        CategoryTable.Cor c = cores.get(categoria);
        return (c != null) ? c : base.corDe(categoria);
    }

    /** Descricao informada pelo usuario, destinada a interface. */
    public Optional<String> descricaoDe(String groupId, String artifactId) {
        Entrada e = porCoordenada.get(groupId + ":" + artifactId);
        return (e == null) ? Optional.empty() : Optional.ofNullable(e.descricao());
    }

    /** Quantidade de entradas efetivamente carregadas. */
    public int tamanho() {
        return porCoordenada.size();
    }

    /** Problemas encontrados na leitura, para o chamador reportar ao usuario. */
    public List<String> avisos() {
        return avisos;
    }
}
