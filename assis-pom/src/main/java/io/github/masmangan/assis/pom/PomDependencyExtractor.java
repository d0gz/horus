package io.github.masmangan.assis.pom;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * POM dependency extractor.
 *
 * Scope of this version:
 *   1. Read a raw pom.xml using the Maven XPP3 reader.
 *   2. Extract the direct dependencies from the &lt;dependencies&gt; block.
 *   3. Render that list as a Mermaid flowchart.
 *
 * Out of scope (next increments):
 *   - Effective POM (parent inheritance, version resolution, BOM/dependencyManagement).
 *   - Transitive dependencies.
 */
public class PomDependencyExtractor {

    public static void main(String[] args) throws IOException, XmlPullParserException {
        // 1. Input POM path (argument, or pomReko.xml in the current folder by default).
        String pomPath = (args.length > 0) ? args[0] : "pomReko.xml";
        Path input = Path.of(pomPath);
        if (!Files.exists(input)) {
            System.err.println("POM not found: " + input.toAbsolutePath());
            System.exit(1);
        }

        // 2. Read the POM with Maven XPP3 -> Model object.
        Model model = readPom(input);

        // 3. Convert the dependencies into Mermaid.
        String mermaid = toMermaid(model);

        // 4. Print to stdout and write a .mmd file.
        System.out.println(mermaid);
        Path output = Path.of("dependencies.mmd");
        Files.writeString(output, mermaid, StandardCharsets.UTF_8);
        System.err.println("Mermaid written to: " + output.toAbsolutePath());
    }

    /**
     * Reads the pom.xml using the Maven XPP3 reader (MavenXpp3Reader, from maven-model).
     * Returns the Model object, the in-memory representation of the POM.
     */
    static Model readPom(Path pom) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (Reader fileReader = Files.newBufferedReader(pom, StandardCharsets.UTF_8)) {
            return reader.read(fileReader);
        }
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
