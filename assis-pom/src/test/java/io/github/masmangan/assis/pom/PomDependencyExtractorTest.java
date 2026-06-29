package io.github.masmangan.assis.pom;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomDependencyExtractorTest {

    private Model loadSample() throws Exception {
        Path pom = Path.of(getClass().getResource("/pomReko.xml").toURI());
        return PomDependencyExtractor.readPom(pom);
    }

    @Test
    void readsAllDirectDependencies() throws Exception {
        Model model = loadSample();
      
        assertEquals(15, model.getDependencies().size());
    }

    @Test
    void mermaidListsProjectAndDependencies() throws Exception {
        String mermaid = PomDependencyExtractor.toMermaid(loadSample());

        assertTrue(mermaid.startsWith("flowchart LR"));
        assertTrue(mermaid.contains("project[\"com.pucrs:construct:0.0.1-SNAPSHOT\"]"));
        assertTrue(mermaid.contains("software.amazon.awssdk:rekognition"));
 
        assertTrue(mermaid.contains("-->|test|"));
        assertTrue(mermaid.contains("-->|runtime|"));
  
        assertTrue(mermaid.contains("org.projectlombok:lombok (optional)"));
    }

    @Test
    void excludesDependencyManagementBom() throws Exception {
        String mermaid = PomDependencyExtractor.toMermaid(loadSample());
        //AWS BOM in <dependencyManagement> should not appear.
        assertFalse(mermaid.contains("software.amazon.awssdk:bom"));
    }
}
