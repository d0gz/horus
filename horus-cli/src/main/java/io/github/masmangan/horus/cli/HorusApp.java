package io.github.masmangan.horus.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.masmangan.assis.AssisInfo;
import io.github.masmangan.assis.GenerateClassDiagram;
import io.github.masmangan.assis.er.GenerateEntityDiagram;
import io.github.masmangan.assis.pom.GeneratePomDiagram;

/**
 * Single entry point for the class, entity and POM diagrams.
 */
public final class HorusApp {

	private static final Logger LOG = Logger.getLogger(HorusApp.class.getName());

	private static final String MAVEN_POM_PROPS = "META-INF/maven/%s/%s/pom.properties";

	private static final String VERSION_UNKNOWN = "unknown";

	static final String DEFAULT_OUT_DIR = "docs/diagrams/src";

	static final int OK = 0;
	static final int USAGE_ERROR = 1;
	static final int RESOLVE_ERROR = 2;
	static final int GENERATION_ERROR = 3;

	private HorusApp() {
	}

	public static void main(String[] args) {
		System.exit(run(args));
	}

	static int run(String[] args) {
		final HorusArgs cli;
		try {
			cli = HorusArgs.parse(args);
		} catch (IllegalArgumentException e) {
			LOG.log(Level.SEVERE, () -> "Exception after parse: " + e.getMessage());
			return USAGE_ERROR;
		}

		if (cli.mode == HorusArgs.Mode.HELP) {
			LOG.log(Level.INFO, () -> HorusArgs.usage);
			return OK;
		}
		if (cli.mode == HorusArgs.Mode.VERSION) {
			logVersion();
			return OK;
		}

		Set<Path> sourceRoots = Set.of();
		Optional<Path> pom = Optional.empty();
		try {
			if (cli.wantsClass() || cli.wantsEr()) {
				sourceRoots = SourceLocator.resolve(cli.projectDir, cli.sourceRoots);
			}
			if (cli.wantsPom()) {
				pom = locatePom(cli);
				if (pom.isPresent()) {
					Path found = pom.get();
					LOG.info(() -> "Using pom.xml: " + found);
				} else {
					String where = pomSearchDescription(cli);
					if (cli.modo != null) {
						throw new IllegalStateException("pom.xml not found " + where);
					}
					LOG.info(() -> "pom.xml not found " + where + ", skipping the pom diagrams");
				}
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, () -> "Exception after resolve: " + e.getMessage());
			return RESOLVE_ERROR;
		}

		final Path outDir = (cli.outDir != null ? cli.outDir : Path.of(DEFAULT_OUT_DIR)).toAbsolutePath().normalize();

		try {
			if (Files.exists(outDir) && !Files.isDirectory(outDir)) {
				throw new IllegalArgumentException("-d must be a directory: " + outDir);
			}
			Files.createDirectories(outDir);
			LOG.info(() -> "Writing outputs to: " + outDir);

			if (cli.wantsClass()) {
				GenerateClassDiagram.generate(sourceRoots, outDir);
			}
			if (cli.wantsEr()) {
				GenerateEntityDiagram.generate(sourceRoots, outDir, cli.inferred, cli.catalogo);
			}
			if (pom.isPresent()) {
				Path catalogo = HorusArgs.MODO_ER.equals(cli.modo) ? null : cli.catalogo;
				for (String pomModo : cli.pomModos()) {
					GeneratePomDiagram.generate(pom.get(), outDir, pomModo, catalogo, cli.bancosPorModulo);
				}
			}
			return OK;
		} catch (Exception e) {
			LOG.log(Level.SEVERE, () -> "Exception during generation: " + e.getMessage());
			return GENERATION_ERROR;
		}
	}

	/**
	 * The project folder wins; without it, --source-path points at the project,
	 * so the nearest pom.xml at or above the first source root is used.
	 */
	private static Optional<Path> locatePom(HorusArgs cli) {
		if (cli.projectDirGiven || cli.sourceRoots.isEmpty()) {
			return SourceLocator.findPom(cli.projectDir);
		}
		return SourceLocator.findPomAbove(cli.sourceRoots.iterator().next());
	}

	private static String pomSearchDescription(HorusArgs cli) {
		if (cli.projectDirGiven || cli.sourceRoots.isEmpty()) {
			return "in " + cli.projectDir.toAbsolutePath().normalize();
		}
		return "at or above " + cli.sourceRoots.iterator().next().toAbsolutePath().normalize();
	}

	private static void logVersion() {
		LOG.log(Level.INFO, () -> "HORUS (ASSIS " + AssisInfo.versionOrDev() + ")");
		LOG.log(Level.INFO, () -> "JavaParser: " + mavenPomVersion("com.github.javaparser", "javaparser-core"));
		LOG.log(Level.INFO, () -> "Maven Model: " + mavenPomVersion("org.apache.maven", "maven-model"));
		LOG.log(Level.INFO, () -> "Java: " + System.getProperty("java.version") + " ("
				+ System.getProperty("java.vendor") + ") / " + System.getProperty("java.runtime.name") + " "
				+ System.getProperty("java.runtime.version"));
		LOG.log(Level.INFO, () -> "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
				+ " " + System.getProperty("os.arch"));
	}

	private static String mavenPomVersion(String groupId, String artifactId) {
		String path = MAVEN_POM_PROPS.formatted(groupId, artifactId);
		try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				return VERSION_UNKNOWN;
			}
			Properties p = new Properties();
			p.load(in);
			return p.getProperty("version", VERSION_UNKNOWN);
		} catch (Exception e) {
			return VERSION_UNKNOWN;
		}
	}
}
