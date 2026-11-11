package io.github.masmangan.assis.pom;

import io.github.masmangan.assis.pom.C4Model.Elemento;
import io.github.masmangan.assis.pom.C4Model.Evidencia;
import io.github.masmangan.assis.pom.C4Model.Fonte;
import io.github.masmangan.assis.pom.C4Model.ItemDoCatalogo;
import io.github.masmangan.assis.pom.C4Model.Procedencia;
import io.github.masmangan.assis.pom.C4Model.Relacao;
import io.github.masmangan.assis.pom.C4Model.Tipo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Catalogo do C2 (incremento C): as linhas do --catalogo que removem dependencias
 * e que acrescentam ou removem conteineres e relacoes.
 *
 * O mesmo arquivo tambem traz as linhas de classificacao (describe dependency e a
 * linha antiga), que sao do CatalogResolver; aqui elas sao puladas em silencio, e
 * cada leitor so avisa sobre as linhas que sao dele (D36). Os numeros de linha
 * coincidem porque os dois leem o mesmo texto.
 *
 * Como no catalogo do ER, o catalogo vence a ferramenta, e lido a cada execucao,
 * nunca e gravado, e seus problemas sao avisos com o numero da linha, nunca erros.
 * Tudo o que ele acrescenta sai com Procedencia.DECLARADO e Evidencia(CATALOGO).
 */
public final class CatalogoC2 {

    /** Uma linha de conteiner ou relacao ja lida. A ordem de aplicacao e dada pelo tipo (D41). */
    sealed interface OperacaoC2 permits AddContainer, AddRelationship, RemoveContainer, RemoveRelationship {
        int linha();
    }

    record AddContainer(int linha, String id, String nome, String tecnologia, String descricao, Tipo tipo)
            implements OperacaoC2 {
    }

    record AddRelationship(int linha, String origem, String destino, String tecnologia, String descricao)
            implements OperacaoC2 {
    }

    record RemoveContainer(int linha, String id) implements OperacaoC2 {
    }

    record RemoveRelationship(int linha, String origem, String destino) implements OperacaoC2 {
    }

    /** O Mermaid so aceita letras, digitos e sublinhado em ids. */
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9_]+");

    private static final Map<String, Tipo> TIPOS = new LinkedHashMap<>();

    static {
        TIPOS.put("externo", Tipo.SISTEMA_EXTERNO);
        TIPOS.put("interno", Tipo.APLICACAO);
        TIPOS.put("banco", Tipo.BANCO);
        TIPOS.put("mensageria", Tipo.MENSAGERIA);
        TIPOS.put("interface", Tipo.INTERFACE);
    }

    private final Map<String, Integer> dependenciasRemovidas;
    private final List<AddContainer> adicionarConteineres;
    private final List<AddRelationship> adicionarRelacoes;
    private final List<RemoveContainer> removerConteineres;
    private final List<RemoveRelationship> removerRelacoes;
    private final List<ItemDoCatalogo> avisos;

    private CatalogoC2(Map<String, Integer> dependenciasRemovidas,
                       List<AddContainer> adicionarConteineres, List<AddRelationship> adicionarRelacoes,
                       List<RemoveContainer> removerConteineres, List<RemoveRelationship> removerRelacoes,
                       List<ItemDoCatalogo> avisos) {
        this.dependenciasRemovidas = Collections.unmodifiableMap(new LinkedHashMap<>(dependenciasRemovidas));
        this.adicionarConteineres = List.copyOf(adicionarConteineres);
        this.adicionarRelacoes = List.copyOf(adicionarRelacoes);
        this.removerConteineres = List.copyOf(removerConteineres);
        this.removerRelacoes = List.copyOf(removerRelacoes);
        this.avisos = List.copyOf(avisos);
    }

    /** Sem catalogo: nada muda. */
    public static CatalogoC2 vazio() {
        return new CatalogoC2(Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // -----------------------------------------------------------------
    // Leitura
    // -----------------------------------------------------------------

    public static CatalogoC2 deTexto(String conteudo) {
        Leitor l = new Leitor();
        String[] linhas = conteudo.split("\\R", -1);
        for (int n = 0; n < linhas.length; n++) {
            l.ler(linhas[n].trim(), n + 1);
        }
        return new CatalogoC2(l.removidas, List.copyOf(l.addContainer.values()),
                List.copyOf(l.addRelationship.values()), List.copyOf(l.removeContainer.values()),
                List.copyOf(l.removeRelationship.values()), l.avisos);
    }

    /** Estado da leitura: um mapa por tipo de linha, para que a repetida valha pela ultima (D45). */
    private static final class Leitor {
        final Map<String, Integer> removidas = new LinkedHashMap<>();
        final Map<String, AddContainer> addContainer = new LinkedHashMap<>();
        final Map<String, AddRelationship> addRelationship = new LinkedHashMap<>();
        final Map<String, RemoveContainer> removeContainer = new LinkedHashMap<>();
        final Map<String, RemoveRelationship> removeRelationship = new LinkedHashMap<>();
        final List<ItemDoCatalogo> avisos = new ArrayList<>();

        void ler(String linha, int numero) {
            if (linha.isEmpty() || linha.startsWith("#")) {
                return;
            }
            String[] campos = linha.split("\\|", -1);
            for (int i = 0; i < campos.length; i++) {
                campos[i] = campos[i].trim();
            }
            String tipoDaLinha = campos[0].toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

            // So as linhas que comecam por add/remove sao deste leitor. As demais
            // (describe dependency, a linha antiga g:a | ..., e qualquer linha
            // malformada fora disso) sao do CatalogResolver, que avisa sobre elas.
            if (!tipoDaLinha.startsWith("add ") && !tipoDaLinha.startsWith("remove ")) {
                return;
            }

            switch (tipoDaLinha) {
                case "remove dependency" -> lerRemoveDependency(campos, numero);
                case "add container" -> lerAddContainer(campos, numero);
                case "add relationship" -> lerAddRelationship(campos, numero);
                case "remove container" -> lerRemoveContainer(campos, numero);
                case "remove relationship" -> lerRemoveRelationship(campos, numero);
                default -> aviso(numero, "tipo de linha desconhecido '" + campos[0] + "'");
            }
        }

        private void lerRemoveDependency(String[] campos, int numero) {
            String coordenada = campo(campos, 1);
            String[] partes = coordenada.split(":", -1);
            if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) {
                aviso(numero, "coordenada invalida '" + coordenada + "' (use groupId:artifactId)");
                return;
            }
            Integer anterior = removidas.remove(coordenada);
            repetida(anterior, numero);
            removidas.put(coordenada, numero);
        }

        private void lerAddContainer(String[] campos, int numero) {
            String id = campo(campos, 1);
            String nome = campo(campos, 2);
            if (!idValido(id, numero)) {
                return;
            }
            if (nome.isEmpty()) {
                aviso(numero, "add container sem nome");
                return;
            }
            String tipoTexto = campo(campos, 5).toLowerCase(Locale.ROOT);
            Tipo tipo = tipoTexto.isEmpty() ? Tipo.SISTEMA_EXTERNO : TIPOS.get(tipoTexto);
            if (tipo == null) {
                aviso(numero, "tipo de conteiner '" + campo(campos, 5) + "' desconhecido (use "
                        + String.join(", ", TIPOS.keySet()) + ")");
                return;
            }
            AddContainer anterior = addContainer.remove(id);
            repetida(anterior == null ? null : anterior.linha(), numero);
            addContainer.put(id, new AddContainer(numero, id, nome,
                    vazioViraNulo(campo(campos, 3)), vazioViraNulo(campo(campos, 4)), tipo));
        }

        private void lerAddRelationship(String[] campos, int numero) {
            String origem = campo(campos, 1);
            String destino = campo(campos, 2);
            String tecnologia = campo(campos, 3);
            String descricao = campo(campos, 4);
            if (!idValido(origem, numero) || !idValido(destino, numero)) {
                return;
            }
            // O C4 pede tecnologia explicita entre conteineres, e a seta precisa dizer o que faz.
            if (tecnologia.isEmpty()) {
                aviso(numero, "add relationship sem tecnologia");
                return;
            }
            if (descricao.isEmpty()) {
                aviso(numero, "add relationship sem descricao");
                return;
            }
            String chave = origem + " -> " + destino;
            AddRelationship anterior = addRelationship.remove(chave);
            repetida(anterior == null ? null : anterior.linha(), numero);
            addRelationship.put(chave, new AddRelationship(numero, origem, destino, tecnologia, descricao));
        }

        private void lerRemoveContainer(String[] campos, int numero) {
            String id = campo(campos, 1);
            if (!idValido(id, numero)) {
                return;
            }
            RemoveContainer anterior = removeContainer.remove(id);
            repetida(anterior == null ? null : anterior.linha(), numero);
            removeContainer.put(id, new RemoveContainer(numero, id));
        }

        private void lerRemoveRelationship(String[] campos, int numero) {
            String origem = campo(campos, 1);
            String destino = campo(campos, 2);
            if (!idValido(origem, numero) || !idValido(destino, numero)) {
                return;
            }
            String chave = origem + " -> " + destino;
            RemoveRelationship anterior = removeRelationship.remove(chave);
            repetida(anterior == null ? null : anterior.linha(), numero);
            removeRelationship.put(chave, new RemoveRelationship(numero, origem, destino));
        }

        private boolean idValido(String id, int numero) {
            if (ID_VALIDO.matcher(id).matches()) {
                return true;
            }
            aviso(numero, "id '" + id + "' invalido (use letras, digitos e _)");
            return false;
        }

        /** A linha anterior com a mesma identidade deixa de valer; o aviso fica nela. */
        private void repetida(Integer linhaAnterior, int numero) {
            if (linhaAnterior != null) {
                aviso(linhaAnterior, "repetida na linha " + numero + ", vale a ultima");
            }
        }

        private void aviso(int numero, String texto) {
            avisos.add(new ItemDoCatalogo(numero, texto));
        }

        private static String campo(String[] campos, int i) {
            return i < campos.length ? campos[i] : "";
        }

        private static String vazioViraNulo(String s) {
            return s.isEmpty() ? null : s;
        }
    }

    // -----------------------------------------------------------------
    // Consulta e aplicacao
    // -----------------------------------------------------------------

    /** groupId:artifactId -> linha do remove dependency, na ordem das linhas. */
    public Map<String, Integer> dependenciasRemovidas() {
        return dependenciasRemovidas;
    }

    /** Problemas de leitura, com o numero da linha. */
    public List<ItemDoCatalogo> avisos() {
        return avisos;
    }

    /**
     * Aplica as linhas de conteiner e relacao sobre o modelo derivado, na ordem
     * fixa (D41): add container, add relationship, remove container, remove
     * relationship. O remove dependency ja foi aplicado antes das regras.
     */
    public C4Model aplicar(C4Model derivado) {
        C4Model.Builder b = C4Model.Builder.de(derivado);
        avisos.forEach(b::ignorada);

        for (AddContainer a : adicionarConteineres) {
            b.substituir(new Elemento(a.tipo(), a.id(), a.nome(), a.tecnologia(), a.descricao(),
                    Procedencia.DECLARADO, evidencia(a.linha())));
        }

        for (AddRelationship a : adicionarRelacoes) {
            String ausente = !b.contem(a.origem()) ? a.origem() : !b.contem(a.destino()) ? a.destino() : null;
            if (ausente != null) {
                b.ignorada(new ItemDoCatalogo(a.linha(), "conteiner '" + ausente + "' nao existe"));
                continue;
            }
            // Mesmo par de uma relacao existente: o catalogo a substitui (D42).
            b.removerRelacoes(a.origem(), a.destino());
            b.relacao(new Relacao(a.origem(), a.destino(), a.tecnologia(), a.descricao(),
                    Procedencia.DECLARADO, evidencia(a.linha())));
        }

        for (RemoveContainer r : removerConteineres) {
            if (!b.contem(r.id())) {
                b.ignorada(new ItemDoCatalogo(r.linha(), "conteiner '" + r.id() + "' nao existe"));
                continue;
            }
            int relacoes = b.removerElemento(r.id());
            b.removido(new ItemDoCatalogo(r.linha(), "conteiner " + r.id() + " (linha " + r.linha()
                    + ") e suas " + relacoes + " relacoes"));
        }

        for (RemoveRelationship r : removerRelacoes) {
            int removidas = b.removerRelacoes(r.origem(), r.destino());
            if (removidas == 0) {
                b.ignorada(new ItemDoCatalogo(r.linha(), "relacao '" + r.origem() + " -> " + r.destino()
                        + "' nao existe"));
                continue;
            }
            b.removido(new ItemDoCatalogo(r.linha(), "relacao " + r.origem() + " -> " + r.destino()
                    + " (linha " + r.linha() + ")"));
        }

        return b.construir();
    }

    private static Evidencia evidencia(int linha) {
        return new Evidencia(Fonte.CATALOGO, "linha " + linha);
    }
}
