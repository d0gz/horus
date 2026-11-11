package io.github.masmangan.assis.pom;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * POM dependency extractor.
 *
 * Scope of this version:
 *   1. Read a raw pom.xml using the Maven XPP3 reader.
 *   2. Extract the direct dependencies from the &lt;dependencies&gt; block.
 *   3. Render that list as a Mermaid flowchart.
 *
 * Modos disponiveis: dependencias, completo, reator e c2.
 *
 * Out of scope (next increments):
 *   - Effective POM (parent inheritance, version resolution, BOM/dependencyManagement).
 *   - Transitive dependencies.
 *   - Properties, plugins and parent blocks (CP3).
 *   - Dependency classification and colouring (CP4 and CP5).
 */
public class PomDependencyExtractor {

    /** Modo que gera apenas a lista de dependencias. Primeiro incremento do modulo. */
    static final String MODO_DEPENDENCIAS = "dependencias";

    /** Modo que gera a perspectiva arquitetural completa do pom. Segundo incremento. */
    static final String MODO_COMPLETO = "completo";

    /** Modo que gera a visao de reator: um no por modulo do projeto multimodulo. */
    static final String MODO_REATOR = "reator";

    /** Modo que gera o diagrama de conteineres, nivel 2 do Modelo C4. */
    static final String MODO_C2 = "c2";

    /** Caminho de saida usado no modo dependencias quando nenhum e informado. */
    static final String SAIDA_PADRAO = "dependencies.mmd";

    /** Caminho de saida usado no modo completo quando nenhum e informado. */
    static final String SAIDA_PADRAO_COMPLETO = "pom.mmd";

    /** Caminho de saida usado no modo reator quando nenhum e informado. */
    static final String SAIDA_PADRAO_REATOR = "reator.mmd";

    /** Caminho de saida usado no modo c2 quando nenhum e informado. */
    static final String SAIDA_PADRAO_C2 = "c2.mmd";

    /** Texto de uso, exibido em qualquer erro de invocacao. */
    static final String USO =
            "Uso: PomDependencyExtractor <caminho-do-pom.xml> [caminho-de-saida.mmd] "
            + "[--modo=dependencias|completo|reator|c2] [--catalogo=arquivo.txt] "
            + "[--bancos=compartilhado|por-modulo]";

    static final int OK = 0;
    static final int ERRO_ARQUIVO_NAO_ENCONTRADO = 1;
    static final int ERRO_USO = 2;
    static final int ERRO_XML = 3;

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Ponto de entrada testavel.
     *
     * Recebe os fluxos de saida por parametro e devolve o codigo de saida em vez
     * de chamar System.exit, para que possa ser exercitado por testes sem
     * derrubar a JVM.
     *
     * args[0] - caminho do pom.xml (obrigatorio)
     * args[1] - caminho do arquivo .mmd de saida (opcional)
     */
    static int run(String[] args, PrintStream out, PrintStream err) {

        // As flags podem vir em qualquer posicao. O que sobra sao os caminhos,
        // na ordem: pom de entrada e, opcionalmente, arquivo de saida.
        String modo = MODO_DEPENDENCIAS;
        String catalogo = null;
        ArchetypeRules.Armazenamento armazenamento = ArchetypeRules.Armazenamento.COMPARTILHADO;
        List<String> caminhos = new ArrayList<>();

        for (String argumento : args) {
            if (argumento.startsWith("--modo=")) {
                modo = argumento.substring("--modo=".length());
            } else if (argumento.equals("--bancos=por-modulo")) {
                armazenamento = ArchetypeRules.Armazenamento.POR_MODULO;
            } else if (argumento.equals("--bancos=compartilhado")) {
                armazenamento = ArchetypeRules.Armazenamento.COMPARTILHADO;
            } else if (argumento.startsWith("--catalogo=")) {
                catalogo = argumento.substring("--catalogo=".length());
            } else if (argumento.startsWith("--")) {
                err.println("Opcao desconhecida: " + argumento);
                err.println(USO);
                return ERRO_USO;
            } else {
                caminhos.add(argumento);
            }
        }

        if (caminhos.isEmpty()) {
            err.println(USO);
            return ERRO_USO;
        }

        if (!MODO_DEPENDENCIAS.equals(modo) && !MODO_COMPLETO.equals(modo)
                && !MODO_REATOR.equals(modo) && !MODO_C2.equals(modo)) {
            err.println("Modo invalido: " + modo);
            err.println(USO);
            return ERRO_USO;
        }

        Path input = Path.of(caminhos.get(0));
        if (!Files.exists(input)) {
            err.println("POM nao encontrado: " + input.toAbsolutePath());
            return ERRO_ARQUIVO_NAO_ENCONTRADO;
        }

        // Etapa 1 - acesso ao disco.
        // Qualquer falha aqui e um problema de arquivo, nunca de conteudo.
        String conteudo;
        try {
            conteudo = Files.readString(input, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("Falha ao ler " + input.toAbsolutePath()
                    + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return ERRO_ARQUIVO_NAO_ENCONTRADO;
        }

        // Etapa 2 - interpretacao do XML.
        // O arquivo ja esta em memoria, portanto qualquer falha aqui e de conteudo.
        // As duas excecoes sao tratadas juntas de proposito: o MavenXpp3Reader
        // pode sinalizar um documento truncado como fim de arquivo inesperado
        // (IOException) em vez de erro de sintaxe (XmlPullParserException), e a
        // classificacao do erro nao deve depender dessa escolha da biblioteca.
        Model model;
        try {
            model = readPom(new StringReader(conteudo));
        } catch (XmlPullParserException | IOException e) {
            err.println("Falha ao interpretar o XML de " + input.toAbsolutePath()
                    + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return ERRO_XML;
        }

        // O reator so e percorrido nos modos que precisam dele, para nao pagar
        // leitura de disco em um projeto de modulo unico.
        ReactorView reator = null;
        if (MODO_REATOR.equals(modo) || MODO_COMPLETO.equals(modo) || MODO_C2.equals(modo)) {
            try {
                reator = ReactorView.de(input);
            } catch (XmlPullParserException | IOException e) {
                err.println("Falha ao percorrer os modulos a partir de " + input.toAbsolutePath()
                        + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                return ERRO_XML;
            }
            for (String ausente : reator.modulosAusentes()) {
                err.println("Aviso: modulo declarado mas nao lido: " + ausente);
            }
        }

        CategoryResolver tabela = null;
        if (MODO_COMPLETO.equals(modo) || MODO_C2.equals(modo)) {
            CategoryTable json = CategoryTable.padrao();
            if (!json.carregou()) {
                err.println("Aviso: categorias.json nao foi carregado. Todas as dependencias"
                        + " serao marcadas como nao classificadas.");
                err.println("Verifique se o arquivo esta em src/main/resources/categorias.json"
                        + " e se o schemaVersion e " + CategoryTable.ESQUEMA_SUPORTADO + ".");
            }

            // O catalogo do usuario e lido a cada execucao e nunca gravado no
            // categorias.json, que permanece a unica fonte versionada.
            if (catalogo != null) {
                CatalogResolver usuario = CatalogResolver.de(json, Path.of(catalogo));
                usuario.avisos().forEach(a -> err.println("Catalogo: " + a));
                err.println("Catalogo: " + usuario.tamanho() + " entrada(s) aplicada(s).");
                tabela = usuario;
            } else {
                tabela = json;
            }
        } else if (catalogo != null) {
            err.println("Aviso: --catalogo so tem efeito com --modo=completo ou --modo=c2.");
        }

        String mermaid;
        if (MODO_REATOR.equals(modo)) {
            mermaid = ReactorMermaidRenderer.render(reator);
        } else if (MODO_C2.equals(modo)) {
            // A sobrecarga de ArchetypeRules decide sozinha entre projeto simples
            // e multimodulo, por isso o modo c2 recebe sempre o reator.
            C4Model c4 = ArchetypeRules.aplicar(reator, tabela, armazenamento);
            // As notas dizem o que a analise do pom nao alcanca. Vao tambem para
            // o terminal, porque sao o limite declarado do artefato gerado.
            c4.notas().forEach(n -> err.println("Nota: " + n));
            // A lista fica no fim do .mmd; no terminal, uma linha so (D34).
            if (!c4.naoIdentificadas().isEmpty()) {
                err.println("Aviso: " + c4.naoIdentificadas().size()
                        + " dependencia(s) nao identificada(s); veja \"Dependencias nao identificadas\""
                        + " no fim do .mmd");
            }
            mermaid = C4MermaidRenderer.render(c4);
        } else if (MODO_COMPLETO.equals(modo)) {
            mermaid = PomMermaidRenderer.render(PomView.de(model), tabela);
        } else {
            mermaid = toMermaid(model);
        }

        if (MODO_DEPENDENCIAS.equals(modo) && reatorTemModulos(input, err)) {
            err.println("Aviso: este e um pom agregador. Use --modo=reator para ver os modulos.");
        }

        // print, e nao println: o conteudo do arquivo deve ser identico ao do terminal.
        out.print(mermaid);

        String padrao = MODO_REATOR.equals(modo) ? SAIDA_PADRAO_REATOR
                : MODO_C2.equals(modo) ? SAIDA_PADRAO_C2
                : MODO_COMPLETO.equals(modo) ? SAIDA_PADRAO_COMPLETO : SAIDA_PADRAO;
        Path output = (caminhos.size() >= 2) ? Path.of(caminhos.get(1)) : Path.of(padrao);
        try {
            Path pai = output.toAbsolutePath().getParent();
            if (pai != null) {
                Files.createDirectories(pai);
            }
            Files.writeString(output, mermaid, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("Falha ao gravar " + output.toAbsolutePath() + ": " + e.getMessage());
            return ERRO_ARQUIVO_NAO_ENCONTRADO;
        }

        // Vai para a saida de erro de proposito: assim a saida padrao contem
        // somente o diagrama e pode ser redirecionada com seguranca.
        err.println("Mermaid gravado em: " + output.toAbsolutePath());

        // Sobre um agregador, cada modulo ganha seu proprio arquivo. Um unico
        // diagrama com todos os modulos e suas dependencias seria ilegivel.
        //
        // O diagrama do agregador e o de cada modulo respondem perguntas
        // diferentes: o primeiro mostra o sistema, o segundo mostra o servico.
        if (reator != null && !reator.modulos().isEmpty()) {
            // tabela e atribuida em ramos condicionais, entao nao e efetivamente
            // final e precisa de uma copia para ser usada na lambda.
            final CategoryResolver resolvedor = tabela;
            Function<PomView, String> renderizador = null;

            if (MODO_COMPLETO.equals(modo)) {
                renderizador = m -> PomMermaidRenderer.render(m, resolvedor);
            } else if (MODO_C2.equals(modo)) {
                renderizador = m -> C4MermaidRenderer.render(
                        ArchetypeRules.aplicar(m, resolvedor));
            }

            if (renderizador != null) {
                int gravados = gravarPorModulo(reator, output, err, renderizador);
                err.println("Diagramas por modulo gravados: " + gravados);
            }
        }

        return OK;
    }

    /**
     * Grava um arquivo por modulo, nomeado a partir do caminho de saida base.
     *
     * O renderizador vem por parametro para que a mesma travessia sirva tanto ao
     * diagrama completo do pom quanto ao diagrama de conteineres.
     */
    static int gravarPorModulo(ReactorView reator, Path base, PrintStream err,
                               Function<PomView, String> renderizador) {
        String nome = base.getFileName().toString();
        int ponto = nome.lastIndexOf('.');
        String prefixo = (ponto > 0) ? nome.substring(0, ponto) : nome;
        String extensao = (ponto > 0) ? nome.substring(ponto) : ".mmd";
        Path pasta = base.toAbsolutePath().getParent();

        int gravados = 0;
        List<ReactorView> todos = reator.achatado();
        for (int i = 1; i < todos.size(); i++) {
            ReactorView m = todos.get(i);
            Path destino = pasta.resolve(
                    prefixo + "-" + m.pom().projeto().artifactId() + extensao);
            try {
                Files.writeString(destino, renderizador.apply(m.pom()),
                        StandardCharsets.UTF_8);
                gravados++;
            } catch (IOException e) {
                err.println("Falha ao gravar " + destino + ": " + e.getMessage());
            }
        }
        return gravados;
    }

    /** Verifica, sem custo de travessia, se o pom declara modulos. */
    private static boolean reatorTemModulos(Path pom, PrintStream err) {
        try {
            return !PomView.de(readPom(pom)).modulos().isEmpty();
        } catch (IOException | XmlPullParserException e) {
            return false;
        }
    }

    /**
     * Reads a pom.xml from disk using the Maven XPP3 reader.
     * Kept for convenience and used by the extraction tests.
     */
    static Model readPom(Path pom) throws IOException, XmlPullParserException {
        try (Reader fileReader = Files.newBufferedReader(pom, StandardCharsets.UTF_8)) {
            return readPom(fileReader);
        }
    }

    /**
     * Reads a pom.xml from any character source using MavenXpp3Reader
     * (from maven-model). Returns the Model object, the in-memory
     * representation of the POM.
     *
     * This overload keeps parsing independent of the file system, which lets
     * the caller classify I/O failures separately from content failures.
     */
    static Model readPom(Reader source) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        return reader.read(source);
    }

    /**
     * Converts the Model's dependencies into a Mermaid flowchart.
     * Each dependency becomes a node linked to the root node (the project itself).
     * The scope (test/runtime) is shown as the edge label; "optional" is a suffix.
     */
    static String toMermaid(Model model) {
        // The project's groupId/version may be inherited from <parent>,
        // so fall back to the parent when they are null in the POM itself.
        String groupId = (model.getGroupId() != null)
                ? model.getGroupId()
                : (model.getParent() != null ? model.getParent().getGroupId() : "unknown");
        String version = (model.getVersion() != null)
                ? model.getVersion()
                : (model.getParent() != null ? model.getParent().getVersion() : "unknown");
        String rootLabel = groupId + ":" + model.getArtifactId() + ":" + version;

        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");
        sb.append("    project[\"").append(rootLabel).append("\"]\n");

        List<Dependency> deps = model.getDependencies();
        for (int i = 0; i < deps.size(); i++) {
            Dependency d = deps.get(i);

            // Label: groupId:artifactId [:version] [ (optional)]
            String label = d.getGroupId() + ":" + d.getArtifactId();
            if (d.getVersion() != null && !d.getVersion().isBlank()) {
                label += ":" + d.getVersion();
            }
            if (d.isOptional()) {
                label += " (optional)";
            }

            String nodeId = "dep" + i;
            if (d.getScope() != null && !d.getScope().isBlank()) {
                // Edge with the scope as its label: project -->|test| depN["..."]
                sb.append("    project -->|").append(d.getScope()).append("| ")
                        .append(nodeId).append("[\"").append(label).append("\"]\n");
            } else {
                // No explicit scope (compile, by default).
                sb.append("    project --> ")
                        .append(nodeId).append("[\"").append(label).append("\"]\n");
            }
        }
        return sb.toString();
    }
}
