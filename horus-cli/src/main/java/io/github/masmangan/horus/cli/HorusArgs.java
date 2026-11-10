package io.github.masmangan.horus.cli;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parsed command line of horus-cli.
 */
final class HorusArgs {

	enum Mode {
		RUN, HELP, VERSION
	}

	static final String MODO_CLASS = "class";
	static final String MODO_ER = "er";
	static final String MODO_C2 = "c2";
	static final String MODO_DEPENDENCIAS_COMPLETO = "dependencias-completo";

	/** CLI name of each pom mode -> mode name in assis-pom. */
	private static final Map<String, String> POM_MODOS = Map.of("dependencias", "dependencias",
			MODO_DEPENDENCIAS_COMPLETO, "completo", "reator", "reator", MODO_C2, "c2");

	static final List<String> DEFAULT_POM_MODOS = List.of("dependencias", MODO_DEPENDENCIAS_COMPLETO, MODO_C2);

	private static final Set<String> CATALOGO_MODOS = Set.of(MODO_ER, MODO_DEPENDENCIAS_COMPLETO, MODO_C2);

	final Mode mode;

	/** Base folder for Java source discovery and for the pom.xml. */
	final Path projectDir;

	/** False => projectDir is the default "." (no positional argument). */
	final boolean projectDirGiven;

	/** Empty => auto-discovery under projectDir. */
	final Set<Path> sourceRoots;

	/** Null => default docs/diagrams/src. */
	final Path outDir;

	/** Null => default run: class + er + pom (dependencias, dependencias-completo, c2). */
	final String modo;

	final Path catalogo;

	final boolean inferred;

	final boolean bancosPorModulo;

	private HorusArgs(Mode mode) {
		this(mode, null, false, Set.of(), null, null, null, false, false);
	}

	private HorusArgs(Mode mode, Path projectDir, boolean projectDirGiven, Set<Path> sourceRoots, Path outDir, String modo, Path catalogo,
			boolean inferred, boolean bancosPorModulo) {
		this.mode = mode;
		this.projectDir = projectDir;
		this.projectDirGiven = projectDirGiven;
		this.sourceRoots = sourceRoots;
		this.outDir = outDir;
		this.modo = modo;
		this.catalogo = catalogo;
		this.inferred = inferred;
		this.bancosPorModulo = bancosPorModulo;
	}

	boolean wantsClass() {
		return modo == null || MODO_CLASS.equals(modo);
	}

	boolean wantsEr() {
		return modo == null || MODO_ER.equals(modo);
	}

	boolean wantsPom() {
		return modo == null || POM_MODOS.containsKey(modo);
	}

	/** Pom modes to run, already translated to the assis-pom names. */
	List<String> pomModos() {
		List<String> modos = modo == null ? DEFAULT_POM_MODOS : List.of(modo);
		return modos.stream().filter(POM_MODOS::containsKey).map(POM_MODOS::get).toList();
	}

	static HorusArgs parse(String[] args) {
		if (args == null) {
			args = new String[0];
		}

		Path projectDir = null;
		Set<Path> srcRoots = new LinkedHashSet<>();
		Path outDir = null;
		String modo = null;
		Path catalogo = null;
		String bancos = null;
		boolean inferred = false;

		for (int i = 0; i < args.length; i++) {
			String a = args[i];

			if (isHelp(a)) {
				return new HorusArgs(Mode.HELP);
			}
			if (isVersion(a)) {
				return new HorusArgs(Mode.VERSION);
			}

			if ("--source-path".equals(a) || "-sourcepath".equals(a)) {
				srcRoots.addAll(parsePathList(requireNext(args, i, a)));
				i++;
			} else if ("-d".equals(a)) {
				outDir = Path.of(unique(outDir, requireNext(args, i, a), a));
				i++;
			} else if ("--inferred".equals(a)) {
				inferred = true;
			} else if (isOption(a, "--modo")) {
				modo = unique(modo, value(args, i, "--modo"), "--modo");
				i += consumed(a);
			} else if (isOption(a, "--catalogo")) {
				catalogo = Path.of(unique(catalogo, value(args, i, "--catalogo"), "--catalogo"));
				i += consumed(a);
			} else if (isOption(a, "--bancos")) {
				bancos = unique(bancos, value(args, i, "--bancos"), "--bancos");
				i += consumed(a);
			} else if (a.startsWith("-")) {
				throw usageError("Unknown option: " + a);
			} else if (projectDir != null) {
				throw usageError("Only one project folder is accepted, got: " + projectDir + " and " + a);
			} else {
				projectDir = Path.of(a);
			}
		}

		validate(modo, catalogo, bancos, inferred);

		return new HorusArgs(Mode.RUN, projectDir != null ? projectDir : Path.of("."), projectDir != null, srcRoots, outDir, modo,
				catalogo, inferred, "por-modulo".equals(bancos));
	}

	private static void validate(String modo, Path catalogo, String bancos, boolean inferred) {
		if (modo != null && !MODO_CLASS.equals(modo) && !MODO_ER.equals(modo) && !POM_MODOS.containsKey(modo)) {
			throw usageError("Unknown --modo: " + modo);
		}
		if (catalogo != null && (modo == null || !CATALOGO_MODOS.contains(modo))) {
			throw usageError("Option --catalogo requires --modo=er, --modo=dependencias-completo or --modo=c2");
		}
		if (inferred && !MODO_ER.equals(modo)) {
			throw usageError("Option --inferred requires --modo=er");
		}
		if (bancos != null) {
			if (!MODO_C2.equals(modo)) {
				throw usageError("Option --bancos requires --modo=c2");
			}
			if (!"compartilhado".equals(bancos) && !"por-modulo".equals(bancos)) {
				throw usageError("Unknown --bancos: " + bancos + " (expected compartilhado or por-modulo)");
			}
		}
	}

	/** Matches both "--opt=value" and "--opt value". */
	private static boolean isOption(String a, String name) {
		return a.equals(name) || a.startsWith(name + "=");
	}

	private static int consumed(String a) {
		return a.contains("=") ? 0 : 1;
	}

	private static String value(String[] args, int i, String name) {
		String a = args[i];
		String v = a.equals(name) ? requireNext(args, i, name) : a.substring(name.length() + 1);
		if (v.isBlank()) {
			throw usageError("Empty value for " + name);
		}
		return v;
	}

	private static <T> String unique(T previous, String value, String name) {
		if (previous != null) {
			throw usageError("Duplicate option: " + name);
		}
		return value;
	}

	private static String requireNext(String[] args, int i, String opt) {
		if (i + 1 >= args.length) {
			throw usageError("Missing value for " + opt);
		}
		return args[i + 1];
	}

	private static boolean isVersion(String a) {
		return "--version".equals(a) || "-version".equals(a);
	}

	private static boolean isHelp(String a) {
		return "--help".equals(a) || "-help".equals(a) || "-?".equals(a);
	}

	private static Set<Path> parsePathList(String raw) {
		LinkedHashSet<Path> out = new LinkedHashSet<>();
		for (String p : raw.split(Pattern.quote(File.pathSeparator))) {
			if (!p.isBlank()) {
				out.add(Path.of(p.trim()));
			}
		}
		if (out.isEmpty()) {
			throw usageError("No source directories provided in --source-path");
		}
		return out;
	}

	private static IllegalArgumentException usageError(String message) {
		return new IllegalArgumentException(message + "\n\n" + usage);
	}

	static final String usage = """
			Usage: java -jar horus-cli.jar [project-folder] <options>

			where possible options include:
			  --help, -help, -?
			        Print this help message
			  --version, -version
			        Version information
			  --modo=<modo>
			        Diagram to generate (one value):
			          class         class diagram (class-diagram.puml)
			          er            entity diagram (entity-diagram.mmd)
			          dependencias  direct dependencies of the pom.xml (dependencies.mmd)
			          dependencias-completo
			                        architectural view of the pom.xml (pom.mmd)
			          reator        modules of a multi-module project (reator.mmd)
			          c2            C4 level 2 container diagram (c2.mmd)
			  --catalogo=<file>
			        Optional user catalog. With --modo=er: entity catalog (add or remove
			        entities, attributes and relationships). With --modo=dependencias-completo or c2:
			        dependency catalog of the pom.xml
			  --inferred
			        With --modo=er only: also infer references by ID type (dotted lines,
			        FK); these are guesses and can be wrong
			  --bancos=compartilhado|por-modulo
			        With --modo=c2 only: one shared database or one per module
			  --source-path <path>, -sourcepath <path>
			        Specify where to find input source files
			  -d <directory>
			        Specify where to place generated diagram files

			Defaults:

			  project-folder defaults to the current directory. The pom.xml is read
			  from <project-folder>/pom.xml. Without project-folder but with
			  --source-path, the nearest pom.xml at or above the first source path
			  is used.

			  Source path auto-discovery, inside project-folder, uses the first available:
			      src/main/java/
			      src/
			      .

			  Generation defaults to:
			      -d docs/diagrams/src/

			  Without --modo, the class and entity diagrams and the dependencias,
			  dependencias-completo and c2 pom diagrams are generated, without catalog; the pom
			  diagrams are skipped when there is no pom.xml.
			""";
}
