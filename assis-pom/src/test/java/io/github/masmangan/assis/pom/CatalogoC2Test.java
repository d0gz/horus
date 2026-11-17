package io.github.masmangan.assis.pom;

import io.github.masmangan.assis.pom.C4Model.Elemento;
import io.github.masmangan.assis.pom.C4Model.Evidencia;
import io.github.masmangan.assis.pom.C4Model.Fonte;
import io.github.masmangan.assis.pom.C4Model.Procedencia;
import io.github.masmangan.assis.pom.C4Model.Relacao;
import io.github.masmangan.assis.pom.C4Model.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Incremento C - catalogo do C2: leitura das linhas de conteiner e relacao e sua
 * aplicacao sobre o modelo derivado pelas regras de arquetipo.
 */
class CatalogoC2Test {

    private static final Evidencia POM = new Evidencia(Fonte.POM, "g:a");

    /** pessoa -> app -> db_postgresql, tudo derivado. */
    private C4Model modelo() {
        return C4Model.comNome("loja")
                .elemento(new Elemento(Tipo.APLICACAO, "app", "loja", null, null, Procedencia.DERIVADO, POM))
                .elemento(new Elemento(Tipo.PESSOA, "pessoa", "Usuario", null, null, Procedencia.DERIVADO, POM))
                .elemento(new Elemento(Tipo.BANCO, "db_postgresql", "PostgreSQL", "PostgreSQL", null,
                        Procedencia.DERIVADO, POM))
                .relacao(new Relacao("pessoa", "app", "HTTPS", "Usa", Procedencia.DERIVADO, POM))
                .relacao(new Relacao("app", "db_postgresql", "JDBC", "Le e grava dados",
                        Procedencia.DERIVADO, POM))
                .construir();
    }

    private C4Model aplicar(String catalogo) {
        return CatalogoC2.deTexto(catalogo).aplicar(modelo());
    }

    private long relacoes(C4Model m, String origem, String destino) {
        return m.relacoes().stream()
                .filter(r -> r.origem().equals(origem) && r.destino().equals(destino)).count();
    }

    private boolean ignorada(C4Model m, int linha, String trecho) {
        return m.ignoradas().stream().anyMatch(i -> i.linha() == linha && i.texto().contains(trecho));
    }

    // ------------------------------------------------------- leitura

    @Test
    @DisplayName("tipo de linha desconhecido vira aviso com o numero da linha")
    void tipoDesconhecidoViraAviso() {
        CatalogoC2 c = CatalogoC2.deTexto("# comentario\nadd foo | x\n");

        assertEquals(1, c.avisos().size());
        assertEquals(2, c.avisos().get(0).linha());
        assertTrue(c.avisos().get(0).texto().contains("tipo de linha desconhecido"));
    }

    @Test
    @DisplayName("add relationship sem tecnologia ou descricao vira aviso e e ignorada")
    void relacaoSemTecnologiaOuDescricaoEhIgnorada() {
        C4Model m = aplicar("""
                add relationship | pessoa | db_postgresql | | Consulta
                add relationship | pessoa | db_postgresql | SQL |
                """);

        assertEquals(0, relacoes(m, "pessoa", "db_postgresql"));
        assertTrue(ignorada(m, 1, "tecnologia"));
        assertTrue(ignorada(m, 2, "descricao"));
    }

    @Test
    @DisplayName("id de conteiner com caractere invalido vira aviso")
    void idInvalidoViraAviso() {
        C4Model m = aplicar("add container | ext-keycloak | Keycloak");

        assertTrue(m.porId("ext-keycloak").isEmpty());
        assertTrue(ignorada(m, 1, "ext-keycloak"));
    }

    // ------------------------------------------------------- add container

    @Test
    @DisplayName("add container cria conteiner declarado com evidencia do catalogo")
    void addContainerCriaDeclarado() {
        C4Model m = aplicar("add container | ext_keycloak | Keycloak | OAuth2/OIDC | Autentica usuarios");

        Elemento e = m.porId("ext_keycloak").orElseThrow();
        assertEquals("Keycloak", e.nome());
        assertEquals("OAuth2/OIDC", e.tecnologia());
        assertEquals(Procedencia.DECLARADO, e.procedencia());
        assertEquals(new Evidencia(Fonte.CATALOGO, "linha 1"), e.evidencia());
    }

    @Test
    @DisplayName("add container sem tipo cria sistema externo; com tipo banco cria banco")
    void addContainerTipoPadraoEBanco() {
        C4Model m = aplicar("""
                add container | ext_keycloak | Keycloak | OAuth2/OIDC | Autentica usuarios
                add container | db_redis | Redis | Redis | Cache de sessao | banco
                """);

        assertEquals(Tipo.SISTEMA_EXTERNO, m.porId("ext_keycloak").orElseThrow().tipo());
        assertEquals(Tipo.BANCO, m.porId("db_redis").orElseThrow().tipo());
    }

    @Test
    @DisplayName("add container com id existente substitui o conteiner")
    void addContainerSubstitui() {
        C4Model m = aplicar("add container | db_postgresql | PostgreSQL 16 | PostgreSQL | Pedidos | banco");

        Elemento e = m.porId("db_postgresql").orElseThrow();
        assertEquals("PostgreSQL 16", e.nome());
        assertEquals(Procedencia.DECLARADO, e.procedencia());
        assertEquals(1, m.doTipo(Tipo.BANCO).size());
        assertEquals(1, relacoes(m, "app", "db_postgresql"));
    }

    // ------------------------------------------------------- add relationship

    @Test
    @DisplayName("add relationship com id inexistente vira aviso e nada muda")
    void addRelationshipIdInexistente() {
        C4Model m = aplicar("add relationship | app | ext_nada | HTTP | Chama");

        assertEquals(modelo().relacoes(), m.relacoes());
        assertTrue(ignorada(m, 1, "conteiner 'ext_nada' nao existe"));
    }

    @Test
    @DisplayName("add relationship com a mesma origem e destino de uma derivada a substitui")
    void addRelationshipSubstituiDerivada() {
        C4Model m = aplicar("add relationship | app | db_postgresql | JDBC | Grava pedidos");

        assertEquals(1, relacoes(m, "app", "db_postgresql"));
        Relacao r = m.relacoes().stream().filter(x -> x.destino().equals("db_postgresql")).findFirst().orElseThrow();
        assertEquals("Grava pedidos", r.descricao());
        assertEquals(Procedencia.DECLARADO, r.procedencia());
    }

    // ------------------------------------------------------- remocoes

    @Test
    @DisplayName("remove container remove tambem as relacoes dele e registra quantas")
    void removeContainerRemoveRelacoes() {
        C4Model m = aplicar("remove container | db_postgresql");

        assertTrue(m.porId("db_postgresql").isEmpty());
        assertEquals(0, relacoes(m, "app", "db_postgresql"));
        assertTrue(m.removidos().stream().anyMatch(i ->
                i.texto().equals("conteiner db_postgresql (linha 1) e suas 1 relacoes")), m.removidos().toString());
    }

    @Test
    @DisplayName("remove relationship remove a relacao e registra a remocao")
    void removeRelationship() {
        C4Model m = aplicar("remove relationship | pessoa | app");

        assertEquals(0, relacoes(m, "pessoa", "app"));
        assertTrue(m.removidos().stream().anyMatch(i -> i.texto().equals("relacao pessoa -> app (linha 1)")));
    }

    @Test
    @DisplayName("ordem fixa: a relacao acrescentada a um conteiner removido tambem some")
    void ordemFixaRemoveDepoisDeAdd() {
        C4Model m = aplicar("""
                remove container | ext_grafana
                add relationship | app | ext_grafana | HTTP | Publica paineis
                add container    | ext_grafana | Grafana
                """);

        assertTrue(m.porId("ext_grafana").isEmpty());
        assertEquals(0, relacoes(m, "app", "ext_grafana"));
    }

    @Test
    @DisplayName("linha repetida: vale a ultima, com aviso")
    void linhaRepetidaValeAUltima() {
        C4Model m = aplicar("""
                add container | ext_x | Primeiro
                add container | ext_x | Segundo
                """);

        assertEquals("Segundo", m.porId("ext_x").orElseThrow().nome());
        assertTrue(ignorada(m, 1, "repetida na linha 2"));
    }
}
