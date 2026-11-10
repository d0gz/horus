package io.github.masmangan.assis.pom;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ponto de entrada de biblioteca para os diagramas do pom, no mesmo formato de
 * GenerateClassDiagram e GenerateEntityDiagram.
 *
 * Delega para {@link PomDependencyExtractor#run}; o diagrama e gravado em
 * outDir com o nome padrao do modo, e os avisos vao para a saida de erro.
 */
public final class GeneratePomDiagram {

	private static final Set<String> MODOS = Set.of(PomDependencyExtractor.MODO_DEPENDENCIAS,
			PomDependencyExtractor.MODO_COMPLETO, PomDependencyExtractor.MODO_REATOR, PomDependencyExtractor.MODO_C2);

	private GeneratePomDiagram() {
	}

	public static boolean isModo(String modo) {
		return modo != null && MODOS.contains(modo);
	}

	public static String fileName(String modo) {
		return switch (requireModo(modo)) {
		case PomDependencyExtractor.MODO_COMPLETO -> PomDependencyExtractor.SAIDA_PADRAO_COMPLETO;
		case PomDependencyExtractor.MODO_REATOR -> PomDependencyExtractor.SAIDA_PADRAO_REATOR;
		case PomDependencyExtractor.MODO_C2 -> PomDependencyExtractor.SAIDA_PADRAO_C2;
		default -> PomDependencyExtractor.SAIDA_PADRAO;
		};
	}

	/**
	 * @param catalogo        catalogo do usuario, ou null
	 * @param bancosPorModulo somente c2: um banco por modulo em vez de um compartilhado
	 * @throws IllegalArgumentException se o modo for desconhecido ou a invocacao invalida
	 * @throws IOException              se o pom nao existir, nao puder ser lido, tiver XML invalido,
	 *                                  ou se a saida nao puder ser gravada
	 */
	public static void generate(Path pom, Path outDir, String modo, Path catalogo, boolean bancosPorModulo)
			throws IOException {
		Objects.requireNonNull(pom, "pom");
		Objects.requireNonNull(outDir, "outDir");
		requireModo(modo);

		List<String> args = new ArrayList<>();
		args.add(pom.toString());
		args.add(outDir.resolve(fileName(modo)).toString());
		args.add("--modo=" + modo);
		if (catalogo != null) {
			args.add("--catalogo=" + catalogo);
		}
		if (bancosPorModulo) {
			args.add("--bancos=por-modulo");
		}

		PrintStream discard = new PrintStream(OutputStream.nullOutputStream());
		int code = PomDependencyExtractor.run(args.toArray(String[]::new), discard, System.err);
		switch (code) {
		case PomDependencyExtractor.OK -> {
		}
		case PomDependencyExtractor.ERRO_USO -> throw new IllegalArgumentException("Invalid POM diagram request: " + args);
		case PomDependencyExtractor.ERRO_XML -> throw new IOException("Invalid pom.xml: " + pom.toAbsolutePath());
		default -> throw new IOException("Cannot read " + pom.toAbsolutePath() + " or write to " + outDir.toAbsolutePath());
		}
	}

	private static String requireModo(String modo) {
		if (!isModo(modo)) {
			throw new IllegalArgumentException("Unknown POM mode: " + modo + " (expected one of " + MODOS + ")");
		}
		return modo;
	}
}
