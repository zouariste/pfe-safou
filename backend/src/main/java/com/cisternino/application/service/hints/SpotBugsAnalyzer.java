package com.cisternino.application.service.hints;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpotBugsAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(SpotBugsAnalyzer.class);
    private static final String SPOTBUGS_JAR = "/opt/spotbugs.jar";

    private final SarifParser sarifParser;

    public SpotBugsAnalyzer(SarifParser sarifParser) {
        this.sarifParser = sarifParser;
    }

    /**
     * Runs SpotBugs on compiled .class files.
     * Only call this when javac succeeded (compiled = true).
     */
    public List<AnalysisFinding> analyze(Path classOutputDir) {
        // Check if SpotBugs JAR exists
        if (!Files.exists(Path.of(SPOTBUGS_JAR))) {
            logger.info("SpotBugs JAR not found at {} - skipping SpotBugs analysis", SPOTBUGS_JAR);
            return List.of();
        }

        try {
            Path sarifOutput = classOutputDir.resolveSibling("spotbugs-output.sarif");

            ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", SPOTBUGS_JAR,
                "-sarif=" + sarifOutput.toString(),
                classOutputDir.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                logger.warn("SpotBugs process timed out");
                return List.of();
            }

            if (!Files.exists(sarifOutput)) {
                logger.debug("SpotBugs produced no SARIF output");
                return List.of();
            }

            String sarifJson = Files.readString(sarifOutput);
            return sarifParser.parse(sarifJson, "spotbugs");

        } catch (Exception e) {
            logger.error("SpotBugs analysis failed", e);
            return List.of();
        }
    }
}
