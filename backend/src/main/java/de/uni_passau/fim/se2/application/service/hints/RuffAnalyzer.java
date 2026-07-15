package de.uni_passau.fim.se2.application.service.hints;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RuffAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(RuffAnalyzer.class);
    private final SarifParser sarifParser;

    public RuffAnalyzer(SarifParser sarifParser) {
        this.sarifParser = sarifParser;
    }

    /**
     * Runs ruff check on the given Python code and returns findings.
     * Creates a temporary file, runs ruff, parses SARIF output.
     */
    public List<AnalysisFinding> analyze(String pythonCode) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("codeowl-ruff-");
            Path sourceFile = tempDir.resolve("solution.py");
            Files.writeString(sourceFile, pythonCode);

            // Run ruff check with SARIF output
            ProcessBuilder pb = new ProcessBuilder(
                "ruff", "check",
                "--output-format", "sarif",
                "--select", "E111,E112,E113,E117,F821,F823,F841,F811",
                "--preview",
                "--no-fix",
                sourceFile.toString()
            );
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                logger.warn("Ruff process timed out");
                return List.of();
            }

            // Ruff returns exit code 1 when findings exist - that's expected
            if (output.isBlank()) return List.of();

            return sarifParser.parse(output, "ruff");

        } catch (Exception e) {
            logger.error("Ruff analysis failed", e);
            return List.of();
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    private void cleanupTempDir(Path dir) {
        if (dir == null) return;
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
