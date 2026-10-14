package io.github.masmangan.assis.pom;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.masmangan.assis.pom.C4Model.Evidencia;
import io.github.masmangan.assis.pom.C4Model.Fonte;
import io.github.masmangan.assis.pom.C4Model.Procedencia;
import io.github.masmangan.assis.pom.C4Model.Tipo;

import java.util.List;
import java.util.Locale;

/**
 * Regras de arquetipo: traduzem a evidencia do pom.xml em elementos do nivel 2 do C4.
 *
 * Cada regra declara que evidencia exige e o que produz. Uma dependencia de
 * biblioteca e assimetrica: declarar o driver do PostgreSQL significa que o
 * projeto e o cliente do banco, nunca o contrario. Por isso toda relacao
 * derivada nasce da aplicacao para o outro elemento, com a unica excecao do ator.
 *
 * As regras negativas valem tanto quanto as positivas: dependencia de teste, de
 * build ou utilitaria nao produz conteiner algum. Lombok e JUnit nao existem em
 * um diagrama de conteineres.
 *
 * Esta tabela e semantica do C4, nao preferencia do usuario, e por isso vive no
 * codigo e nao em categorias.json: alterar o que e um conteiner muda o
 * significado do diagrama, nao a sua aparencia.
 */
public final class ArchetypeRules {

    static final String ID_APP = "app";
    static final String ID_PESSOA = "pessoa";

    /** Como representar armazenamentos declarados em mais de um modulo do reator. */
    public enum Armazenamento {
        /** Um no por tecnologia, ligado a todos os modulos que a declaram. Padrao. */
        COMPARTILHADO,
        /** Um no por modulo, sem afirmar compartilhamento. */
        POR_MODULO
    }

    /** Escopos que descrevem o sistema em execucao. Os demais ficam de fora do C2. */
    private static final List<String> ESCOPOS_EM_EXECUCAO = List.of("compile", "runtime");

    private ArchetypeRules() {
    }

    public static C4Model aplicar(PomView pom, CategoryResolver categorias) {
        C4Model.Builder b = C4Model.comNome(pom.projeto().artifactId());

        regraR1Aplicacao(b, pom);

        boolean precisaBanco = false;
        boolean temMigracao = false;

        for (PomView.DependenciaView d : pom.dependencias()) {
            if (!ESCOPOS_EM_EXECUCAO.contains(d.escopo())) {
                continue; // regra negativa: test e provided nao produzem conteiner
            }

            String g = d.groupId();
            String a = d.artifactId();
            Evidencia ev = new Evidencia(Fonte.POM, d.coordenada());

            if (ehWeb(g, a, categorias.categoriaDe(g, a))) {
                regraR2Ator(b, ev);
            } else if (ehDriverJdbc(g, a)) {
                regraR3Banco(b, a, ev);
            } else if (ehBancoEmMemoria(g, a)) {
                regraR4BancoNaoConfirmavel(b, a, ev);
            } else if (ehDataStoreProprio(g, a)) {
                regraR6BancoDedicado(b, a, ev);
            } else if (ehJpaOuJdbc(g, a)) {
                precisaBanco = true;
            } else if (ehMigracao(g)) {
                temMigracao = true;
            } else if (ehSdkDeNuvem(g) && !ehArtefatoDeNucleo(a)) {
                regraR8SistemaExterno(b, a, ev);
            } else if (ehIdentidade(g, a)) {
                regraR9Identidade(b, a, ev);
            } else if (ehMensageria(g, a)) {
                regraR10Mensageria(b, a, ev);
            } else if (ehRegistroOuConfiguracao(a)) {
                regraR12Registro(b, a, ev);
            } else if (ehRegistroDeMetricas(g, a)) {
                regraR13Metricas(b, a, ev);
            } else if (ehActuator(a)) {
                b.anotar(ID_APP, "expoe endpoints de saude e metricas");
            } else if (ehEmail(a)) {
                regraR14Email(b, ev);
            } else if (ehClienteHttpGenerico(g, a)) {
                b.nota("a aplicacao usa cliente HTTP (" + d.coordenada()
                        + "), mas os destinos nao sao declarados no Maven");
            }
            // cache local, utilitarios e qualquer outra coisa: nenhum elemento
        }

        // R5 e R7 dependem do conjunto todo, por isso rodam depois do laco.
        if ((precisaBanco || temMigracao) && !b.temTipo(Tipo.BANCO)) {
            b.elemento(new C4Model.Elemento(Tipo.BANCO, "db_generico", "Banco de dados",
                    "driver nao declarado", "Persistencia relacional",
                    Procedencia.DERIVADO, new Evidencia(Fonte.POM, "spring-boot-starter-data-*")));
            b.relacao(new C4Model.Relacao(ID_APP, "db_generico", "JDBC", "Le e grava dados",
                    Procedencia.DERIVADO, new Evidencia(Fonte.POM, "spring-boot-starter-data-*")));
        }
        if (temMigracao) {
            b.anotarTipo(Tipo.BANCO, "esquema versionado por ferramenta de migracao");
        }

        return b.construir();
    }

    /**
     * Deriva o C2 de um reator, tratando cada modulo como um conteiner do sistema.
     *
     * Dois ajustes distinguem esta visao da simples uniao dos modulos. As
     * relacoes de cada modulo sao reapontadas para o identificador proprio
     * daquele conteiner, porque o id "app" so faz sentido isoladamente. E um
     * servico de infraestrutura que corresponde a um modulo do reator deixa de
     * ser externo: ele e parte do sistema analisado, nao um terceiro.
     */
    public static C4Model aplicar(ReactorView reator, CategoryResolver categorias) {
        return aplicar(reator, categorias, Armazenamento.COMPARTILHADO);
    }

    /**
     * Variante que permite escolher como representar armazenamentos declarados
     * em mais de um modulo. O pom nao informa se dois modulos usam a mesma
     * instancia; as duas representacoes sao possiveis e ambas registram em nota
     * o que nao pode ser confirmado.
     */
    public static C4Model aplicar(ReactorView reator, CategoryResolver categorias,
                                  Armazenamento armazenamento) {
        if (!reator.ehAgregador()) {
            return aplicar(reator.pom(), categorias);
        }

        C4Model.Builder b = C4Model.comNome(reator.pom().projeto().artifactId());
        List<ReactorView> todos = reator.achatado();

        // Um modulo declarado e nao lido torna o diagrama incompleto. Registrar
        // isso e mais util do que desenhar um sistema que parece menor do que e.
        for (String ausente : reator.modulosAusentes()) {
            b.nota("modulo declarado mas nao lido: " + ausente
                    + "; o diagrama nao representa esse conteiner");
        }

        // Agregador sem nenhum modulo legivel: ao menos o proprio projeto entra,
        // para que o artefato nunca saia vazio.
        if (todos.size() == 1) {
            C4Model raiz = aplicar(reator.pom(), categorias);
            raiz.elementos().forEach(b::elemento);
            raiz.relacoes().forEach(b::relacao);
            raiz.notas().forEach(b::nota);
            return b.construir();
        }

        // artifactId do modulo -> id do conteiner correspondente.
        Map<String, String> modulos = new LinkedHashMap<>();
        for (int i = 1; i < todos.size(); i++) {
            String artefato = todos.get(i).pom().projeto().artifactId();
            modulos.put(artefato, "app_" + sanitizar(artefato));
        }

        // Quantos modulos declaram cada armazenamento, para anotar o no unido.
        Map<String, Integer> declarantes = new LinkedHashMap<>();

        for (int i = 1; i < todos.size(); i++) {
            PomView modulo = todos.get(i).pom();
            String idDoModulo = "app_" + sanitizar(modulo.projeto().artifactId());
            C4Model parcial = aplicar(modulo, categorias);

            // Identificadores validos apenas no modelo isolado, reapontados para
            // os do reator antes de copiar as relacoes.
            Map<String, String> reaponta = new LinkedHashMap<>();
            reaponta.put(ID_APP, idDoModulo);

            for (C4Model.Elemento e : parcial.elementos()) {
                if (ID_APP.equals(e.id())) {
                    b.elemento(new C4Model.Elemento(e.tipo(), idDoModulo,
                            modulo.projeto().artifactId(), e.tecnologia(), e.descricao(),
                            e.procedencia(), e.evidencia()));
                    continue;
                }

                if (e.tipo() == Tipo.BANCO || e.tipo() == Tipo.MENSAGERIA) {
                    declarantes.merge(e.id(), 1, Integer::sum);

                    if (armazenamento == Armazenamento.POR_MODULO) {
                        // Um banco declarado em dois modulos nao e necessariamente
                        // o mesmo banco: cada modulo recebe o seu.
                        String novoId = e.id() + "_" + sanitizar(modulo.projeto().artifactId());
                        reaponta.put(e.id(), novoId);
                        b.elemento(new C4Model.Elemento(e.tipo(), novoId, e.nome(),
                                e.tecnologia(), e.descricao(), e.procedencia(), e.evidencia()));
                    } else {
                        // Modo padrao: o primeiro modulo define o no e os demais apenas
                        // apontam para ele. O Builder nao sobrescreve um id existente.
                        b.elemento(e);
                    }
                    continue;
                }

                String interno = moduloCorrespondente(e, modulos);
                if (interno != null) {
                    reaponta.put(e.id(), interno);
                    continue;
                }
                b.elemento(e);
            }

            for (C4Model.Relacao r : parcial.relacoes()) {
                String origem = reaponta.getOrDefault(r.origem(), r.origem());
                String destino = reaponta.getOrDefault(r.destino(), r.destino());
                if (origem.equals(destino)) {
                    continue; // o modulo apontando para si mesmo nao informa nada
                }
                b.relacao(new C4Model.Relacao(origem, destino, r.tecnologia(),
                        r.descricao(), r.procedencia(), r.evidencia()));
            }

            parcial.notas().forEach(b::nota);
        }

        if (armazenamento == Armazenamento.COMPARTILHADO) {
            declarantes.forEach((id, n) -> {
                if (n > 1) {
                    b.anotar(id, "declarado em " + n
                            + " modulos; instancia unica nao confirmada pelo pom");
                }
            });
        }

        if (todos.size() > 2) {
            b.nota("as chamadas de negocio entre os servicos nao sao declaradas no"
                    + " Maven; informe no catalogo ou aguarde a analise do codigo-fonte");
            b.nota(armazenamento == Armazenamento.COMPARTILHADO
                    ? "armazenamentos da mesma tecnologia foram unidos em um so conteiner;"
                      + " o pom nao informa se os modulos usam a mesma instancia"
                      + " (use --bancos=por-modulo para separa-los)"
                    : "cada modulo declara seu proprio armazenamento; o pom nao"
                      + " informa se dois modulos usam a mesma instancia");
        }

        return b.construir();
    }

    /**
     * Devolve o id do conteiner do reator que corresponde a um sistema externo de
     * infraestrutura, ou null quando o servico e mesmo de terceiros.
     *
     * O casamento e por palavra-chave entre a biblioteca cliente e o artifactId
     * do modulo, de forma deterministica e auditavel: spring-cloud-starter-config
     * procura um modulo cujo nome contenha "config", e o cliente Eureka procura um
     * modulo de "discovery".
     */
    private static String moduloCorrespondente(C4Model.Elemento e, Map<String, String> modulos) {
        if (e.tipo() != Tipo.SISTEMA_EXTERNO || e.evidencia() == null) {
            return null;
        }
        String palavra = palavraDeInfra(e.evidencia().localizador());
        if (palavra == null) {
            return null;
        }
        for (Map.Entry<String, String> m : modulos.entrySet()) {
            if (m.getKey().contains(palavra)) {
                return m.getValue();
            }
        }
        return null;
    }

    /** Palavra procurada no nome do modulo, a partir da biblioteca cliente. */
    private static String palavraDeInfra(String coordenada) {
        String c = coordenada.toLowerCase(Locale.ROOT);
        if (c.contains("eureka") || c.contains("discovery")) {
            return "discovery";
        }
        if (c.contains("consul")) {
            return "consul";
        }
        if (c.contains("config")) {
            return "config";
        }
        return null;
    }

    // -----------------------------------------------------------------
    // Regras
    // -----------------------------------------------------------------

    /** R1: o projeto e sempre o conteiner de aplicacao. */
    private static void regraR1Aplicacao(C4Model.Builder b, PomView pom) {
        b.elemento(new C4Model.Elemento(Tipo.APLICACAO, ID_APP,
                pom.projeto().artifactId(), tecnologiaDaAplicacao(pom),
                "Aplicacao do sistema", Procedencia.DERIVADO,
                new Evidencia(Fonte.POM, pom.projeto().rotulo())));
    }

    /** R2: presenca de dependencia web implica um consumidor externo via HTTP. */
    private static void regraR2Ator(C4Model.Builder b, Evidencia ev) {
        // Varias dependencias podem ser da categoria Web; o ator e a relacao sao
        // criados apenas na primeira, para nao duplicar arestas identicas.
        if (b.contem(ID_PESSOA)) {
            return;
        }
        b.elemento(new C4Model.Elemento(Tipo.PESSOA, ID_PESSOA, "Usuario", null,
                "Consome a interface HTTP da aplicacao", Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_PESSOA, ID_APP, "HTTPS", "Usa",
                Procedencia.DERIVADO, ev));
    }

    /** R3: driver JDBC implica banco relacional externo ao processo. */
    private static void regraR3Banco(C4Model.Builder b, String artifactId, Evidencia ev) {
        String id = "db_" + sanitizar(artifactId);
        b.elemento(new C4Model.Elemento(Tipo.BANCO, id, nomeDeBanco(artifactId),
                nomeDeBanco(artifactId), "Persistencia relacional",
                Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, id, "JDBC", "Le e grava dados",
                Procedencia.DERIVADO, ev));
    }

    /**
     * R4: banco em memoria pode rodar embutido no processo da aplicacao, e nesse
     * caso nao e um conteiner separado. A evidencia existe, a conclusao nao.
     */
    private static void regraR4BancoNaoConfirmavel(C4Model.Builder b, String artifactId, Evidencia ev) {
        String id = "db_" + sanitizar(artifactId);
        b.elemento(new C4Model.Elemento(Tipo.BANCO, id, nomeDeBanco(artifactId),
                nomeDeBanco(artifactId),
                "Banco em memoria: pode ser embutido no processo, nao ha como confirmar pelo pom",
                Procedencia.NAO_CONFIRMAVEL, ev));
    }

    /** R6: armazenamento nao relacional com tecnologia propria. */
    private static void regraR6BancoDedicado(C4Model.Builder b, String artifactId, Evidencia ev) {
        String tecnologia = nomeDeDataStore(artifactId);
        String id = "db_" + sanitizar(tecnologia);
        b.elemento(new C4Model.Elemento(Tipo.BANCO, id, tecnologia, tecnologia,
                "Armazenamento de dados", Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, id, tecnologia, "Le e grava dados",
                Procedencia.DERIVADO, ev));
    }

    /** R8: SDK de nuvem implica servico gerenciado de terceiro. */
    private static void regraR8SistemaExterno(C4Model.Builder b, String artifactId, Evidencia ev) {
        String id = "ext_" + sanitizar(artifactId);
        b.elemento(new C4Model.Elemento(Tipo.SISTEMA_EXTERNO, id, capitalizar(artifactId),
                "SDK", "Servico externo de nuvem", Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, id, "SDK/HTTPS", "Invoca",
                Procedencia.DERIVADO, ev));
    }

    /** R9: cliente de identidade implica provedor externo de autenticacao. */
    private static void regraR9Identidade(C4Model.Builder b, String artifactId, Evidencia ev) {
        b.elemento(new C4Model.Elemento(Tipo.SISTEMA_EXTERNO, "ext_identidade",
                "Provedor de identidade", "OAuth2/OIDC", "Autentica usuarios",
                Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, "ext_identidade", "HTTPS", "Autentica contra",
                Procedencia.DERIVADO, ev));
    }

    /** R10: cliente de mensageria implica broker. */
    private static void regraR10Mensageria(C4Model.Builder b, String artifactId, Evidencia ev) {
        String tecnologia = nomeDeBroker(artifactId);
        String id = "mq_" + sanitizar(tecnologia);
        b.elemento(new C4Model.Elemento(Tipo.MENSAGERIA, id, tecnologia, tecnologia,
                "Troca de mensagens", Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, id, tecnologia, "Publica e consome mensagens",
                Procedencia.DERIVADO, ev));
    }

    /** R12: cliente de descoberta ou configuracao implica servico de infraestrutura. */
    private static void regraR12Registro(C4Model.Builder b, String artifactId, Evidencia ev) {
        String id = "ext_" + sanitizar(artifactId);
        b.elemento(new C4Model.Elemento(Tipo.SISTEMA_EXTERNO, id, nomeDeServicoDeInfra(artifactId),
                "HTTP", "Registro de servicos ou configuracao centralizada",
                Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, id, "HTTP", "Registra-se em",
                Procedencia.DERIVADO, ev));
    }


    /** R13: registry de metricas implica coletor externo. Actuator sozinho nao. */
    private static void regraR13Metricas(C4Model.Builder b, String artifactId, Evidencia ev) {
        String id = "ext_" + sanitizar(artifactId);
        b.elemento(new C4Model.Elemento(Tipo.SISTEMA_EXTERNO, id, nomeDeColetor(artifactId),
                "HTTP", "Coleta de metricas", Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, id, "HTTP", "Publica metricas em",
                Procedencia.DERIVADO, ev));
    }

    /** R14: cliente de email implica servidor SMTP externo. */
    private static void regraR14Email(C4Model.Builder b, Evidencia ev) {
        b.elemento(new C4Model.Elemento(Tipo.SISTEMA_EXTERNO, "ext_smtp",
                "Servidor de email", "SMTP", "Envio de mensagens",
                Procedencia.DERIVADO, ev));
        b.relacao(new C4Model.Relacao(ID_APP, "ext_smtp", "SMTP", "Envia email por",
                Procedencia.DERIVADO, ev));
    }

    // -----------------------------------------------------------------
    // Predicados
    // -----------------------------------------------------------------

    private static boolean ehWeb(String g, String a, String categoria) {
        return "web".equals(categoria)
                || (g.equals("org.springframework.boot") && a.startsWith("spring-boot-starter-web"));
    }

    private static boolean ehDriverJdbc(String g, String a) {
        return (g.equals("org.postgresql") && a.equals("postgresql"))
                || g.equals("com.mysql") || g.equals("mysql")
                || g.equals("org.mariadb.jdbc")
                || g.startsWith("com.oracle.database")
                || g.equals("com.microsoft.sqlserver");
    }

    private static boolean ehBancoEmMemoria(String g, String a) {
        return (g.equals("com.h2database") && a.equals("h2"))
                || g.equals("org.hsqldb") || g.equals("org.apache.derby");
    }

    private static boolean ehDataStoreProprio(String g, String a) {
        return g.equals("org.springframework.boot")
                && (a.equals("spring-boot-starter-data-mongodb")
                || a.equals("spring-boot-starter-data-redis")
                || a.equals("spring-boot-starter-data-elasticsearch")
                || a.equals("spring-boot-starter-data-cassandra"));
    }

    private static boolean ehJpaOuJdbc(String g, String a) {
        return g.equals("org.springframework.boot")
                && (a.equals("spring-boot-starter-data-jpa")
                || a.equals("spring-boot-starter-data-jdbc")
                || a.equals("spring-boot-starter-jdbc"));
    }

    private static boolean ehMigracao(String g) {
        return g.equals("org.flywaydb") || g.equals("org.liquibase");
    }

    private static boolean ehSdkDeNuvem(String g) {
        return g.startsWith("software.amazon.awssdk") || g.startsWith("com.amazonaws")
                || g.startsWith("com.google.cloud") || g.startsWith("com.azure");
    }

    /** Artefatos de nucleo ou BOM nao designam servico algum. */
    private static boolean ehArtefatoDeNucleo(String a) {
        return a.endsWith("-core") || a.endsWith("-bom") || a.equals("bom")
                || a.endsWith("-auth") || a.endsWith("-utils");
    }

    private static boolean ehIdentidade(String g, String a) {
        return g.startsWith("org.keycloak")
                || (g.equals("org.springframework.boot") && a.startsWith("spring-boot-starter-oauth2"));
    }

    private static boolean ehMensageria(String g, String a) {
        return g.equals("org.springframework.kafka")
                || (g.equals("org.springframework.boot") && a.equals("spring-boot-starter-amqp"))
                || g.startsWith("org.apache.activemq")
                || g.equals("org.apache.kafka");
    }

    private static boolean ehRegistroOuConfiguracao(String a) {
        return a.contains("netflix-eureka-client") || a.contains("starter-consul")
                || a.equals("spring-cloud-starter-config");
    }

    private static boolean ehRegistroDeMetricas(String g, String a) {
        return g.equals("io.micrometer") && a.startsWith("micrometer-registry-");
    }

    private static boolean ehActuator(String a) {
        return a.equals("spring-boot-starter-actuator");
    }

    private static boolean ehEmail(String a) {
        return a.equals("spring-boot-starter-mail");
    }

    private static boolean ehClienteHttpGenerico(String g, String a) {
        return a.contains("openfeign") || a.equals("spring-boot-starter-webflux")
                || (g.equals("org.springframework.cloud") && a.contains("loadbalancer"));
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    /** A tecnologia da aplicacao vem da property java.version e do parent Spring Boot. */
    static String tecnologiaDaAplicacao(PomView pom) {
        StringBuilder sb = new StringBuilder();
        String java = pom.propriedades().get("java.version");
        if (java == null) {
            java = pom.propriedades().get("maven.compiler.release");
        }
        if (java != null && !java.isBlank()) {
            sb.append("Java ").append(java);
        }
        pom.parent().ifPresent(p -> {
            if (p.artifactId().contains("spring-boot")) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append("Spring Boot ").append(p.version());
            }
        });
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String nomeDeBanco(String artifactId) {
        String a = artifactId.toLowerCase(Locale.ROOT);
        if (a.contains("postgres")) {
            return "PostgreSQL";
        }
        if (a.equals("h2")) {
            return "H2";
        }
        if (a.contains("mysql")) {
            return "MySQL";
        }
        if (a.contains("mariadb")) {
            return "MariaDB";
        }
        if (a.contains("ojdbc") || a.contains("oracle")) {
            return "Oracle";
        }
        if (a.contains("sqlserver") || a.contains("mssql")) {
            return "SQL Server";
        }
        if (a.contains("hsqldb")) {
            return "HSQLDB";
        }
        if (a.contains("derby")) {
            return "Derby";
        }
        return capitalizar(artifactId);
    }

    private static String nomeDeDataStore(String artifactId) {
        if (artifactId.endsWith("mongodb")) {
            return "MongoDB";
        }
        if (artifactId.endsWith("redis")) {
            return "Redis";
        }
        if (artifactId.endsWith("elasticsearch")) {
            return "Elasticsearch";
        }
        if (artifactId.endsWith("cassandra")) {
            return "Cassandra";
        }
        return capitalizar(artifactId);
    }

    private static String nomeDeBroker(String artifactId) {
        if (artifactId.contains("kafka")) {
            return "Kafka";
        }
        if (artifactId.contains("amqp") || artifactId.contains("rabbit")) {
            return "RabbitMQ";
        }
        if (artifactId.contains("activemq")) {
            return "ActiveMQ";
        }
        return capitalizar(artifactId);
    }

    /** O conteiner externo e o servico, nao a biblioteca cliente que fala com ele. */
    private static String nomeDeServicoDeInfra(String artifactId) {
        String a = artifactId.toLowerCase(Locale.ROOT);
        if (a.contains("eureka")) {
            return "Eureka";
        }
        if (a.contains("consul")) {
            return "Consul";
        }
        if (a.contains("config")) {
            return "Config Server";
        }
        return capitalizar(artifactId);
    }

    private static String nomeDeColetor(String artifactId) {
        return capitalizar(artifactId.replace("micrometer-registry-", ""));
    }

    private static String capitalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    static String sanitizar(String texto) {
        return texto.replaceAll("[^A-Za-z0-9]", "_").toLowerCase(Locale.ROOT);
    }
}
