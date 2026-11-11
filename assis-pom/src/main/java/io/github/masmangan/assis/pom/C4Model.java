package io.github.masmangan.assis.pom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modelo de nivel 2 do C4: conteineres do sistema, atores e sistemas externos.
 *
 * O modelo nasce independente da fonte que o alimentou e da notacao que o
 * desenhara. Hoje ele e construido a partir do pom.xml por ArchetypeRules;
 * quando a analise do codigo-fonte for incorporada, ela enriquece este mesmo
 * modelo em vez de produzir outro.
 *
 * Cada elemento e cada relacao carregam procedencia e evidencia. E o que
 * permite ao leitor distinguir o que foi derivado de forma deterministica do que
 * foi declarado pelo usuario, e rastrear qualquer elemento ate o dado que o
 * originou. As notas registram o que a ferramenta sabe que nao consegue derivar.
 */
public final class C4Model {

    public enum Tipo {
        PESSOA, APLICACAO, BANCO, MENSAGERIA, SISTEMA_EXTERNO,
        /** Interface servida ao navegador (SPA), conteiner dentro da fronteira. */
        INTERFACE
    }

    public enum Fonte {
        POM, CODIGO, CATALOGO
    }

    public enum Procedencia {
        /** Derivado por regra deterministica sobre a evidencia. */
        DERIVADO,
        /** Derivado e confirmado por uma segunda fonte. */
        CONFIRMADO,
        /** Informado diretamente pelo usuario. */
        DECLARADO,
        /** A evidencia existe, mas nao garante que o elemento seja um conteiner. */
        NAO_CONFIRMAVEL
    }

    /** De onde veio o elemento: a fonte e o localizador exato dentro dela. */
    public record Evidencia(Fonte fonte, String localizador) {
    }

    public record Elemento(Tipo tipo, String id, String nome, String tecnologia,
                           String descricao, Procedencia procedencia, Evidencia evidencia) {

        /** Elementos fora da fronteira do sistema: atores e sistemas de terceiros. */
        public boolean foraDaFronteira() {
            return tipo == Tipo.PESSOA || tipo == Tipo.SISTEMA_EXTERNO;
        }
    }

    public record Relacao(String origem, String destino, String tecnologia,
                          String descricao, Procedencia procedencia, Evidencia evidencia) {
    }

    private final String nomeDoSistema;
    private final Map<String, Elemento> elementos;
    private final List<Relacao> relacoes;
    private final List<String> notas;

    private C4Model(String nomeDoSistema, Map<String, Elemento> elementos,
                    List<Relacao> relacoes, List<String> notas) {
        this.nomeDoSistema = nomeDoSistema;
        this.elementos = elementos;
        this.relacoes = relacoes;
        this.notas = notas;
    }

    public static Builder comNome(String nomeDoSistema) {
        return new Builder(nomeDoSistema);
    }

    // -----------------------------------------------------------------
    // Consulta
    // -----------------------------------------------------------------

    public String nomeDoSistema() {
        return nomeDoSistema;
    }

    /** Elementos na ordem em que foram derivados, o que torna a saida estavel. */
    public List<Elemento> elementos() {
        return List.copyOf(elementos.values());
    }

    public List<Relacao> relacoes() {
        return List.copyOf(relacoes);
    }

    /** O que a ferramenta sabe que nao consegue derivar a partir das fontes lidas. */
    public List<String> notas() {
        return List.copyOf(notas);
    }

    public Optional<Elemento> porId(String id) {
        return Optional.ofNullable(elementos.get(id));
    }

    public List<Elemento> doTipo(Tipo tipo) {
        return elementos.values().stream().filter(e -> e.tipo() == tipo).toList();
    }

    public boolean temTipo(Tipo tipo) {
        return !doTipo(tipo).isEmpty();
    }

    // -----------------------------------------------------------------
    // Construcao
    // -----------------------------------------------------------------

    /**
     * Acumula elementos preservando a ordem de insercao.
     *
     * Um elemento ja presente nao e sobrescrito em silencio: a precedencia entre
     * fontes e decidida por quem constroi, nao aqui.
     */
    public static final class Builder {

        private final String nomeDoSistema;
        private final Map<String, Elemento> elementos = new LinkedHashMap<>();
        private final List<Relacao> relacoes = new ArrayList<>();
        private final List<String> notas = new ArrayList<>();

        private Builder(String nomeDoSistema) {
            this.nomeDoSistema = nomeDoSistema;
        }

        public Builder elemento(Elemento e) {
            elementos.putIfAbsent(e.id(), e);
            return this;
        }

        public Builder relacao(Relacao r) {
            relacoes.add(r);
            return this;
        }

        /**
         * Troca o destino das relacoes origem -> destinoAntigo, mantendo a posicao
         * de cada uma na lista para que a ordem de saida continue a mesma.
         */
        Builder redirecionar(String origem, String destinoAntigo, String destinoNovo) {
            for (int i = 0; i < relacoes.size(); i++) {
                Relacao r = relacoes.get(i);
                if (r.origem().equals(origem) && r.destino().equals(destinoAntigo)) {
                    relacoes.set(i, new Relacao(r.origem(), destinoNovo, r.tecnologia(),
                            r.descricao(), r.procedencia(), r.evidencia()));
                }
            }
            return this;
        }

        public Builder nota(String texto) {
            if (!notas.contains(texto)) {
                notas.add(texto);
            }
            return this;
        }

        public boolean contem(String id) {
            return elementos.containsKey(id);
        }

        public boolean temTipo(Tipo tipo) {
            return elementos.values().stream().anyMatch(e -> e.tipo() == tipo);
        }

        /** Acrescenta texto a descricao de um elemento ja presente. */
        public Builder anotar(String id, String complemento) {
            Elemento atual = elementos.get(id);
            if (atual == null) {
                return this;
            }
            String nova = (atual.descricao() == null || atual.descricao().isBlank())
                    ? complemento
                    : atual.descricao() + "; " + complemento;
            elementos.put(id, new Elemento(atual.tipo(), atual.id(), atual.nome(),
                    atual.tecnologia(), nova, atual.procedencia(), atual.evidencia()));
            return this;
        }

        public Builder anotarTipo(Tipo tipo, String complemento) {
            for (Elemento e : List.copyOf(elementos.values())) {
                if (e.tipo() == tipo) {
                    anotar(e.id(), complemento);
                }
            }
            return this;
        }

        public C4Model construir() {
            return new C4Model(nomeDoSistema, new LinkedHashMap<>(elementos),
                    List.copyOf(relacoes), List.copyOf(notas));
        }
    }
}
