/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Converts a checkstyle or SpotBugs XML report into a GitLab Code Quality report, which GitLab shows in the merge
 * request widget. Run with the JDK's source launcher, no build needed:
 *
 * <pre>
 * java CodeQuality.java checkstyle|spotbugs &lt;report.xml&gt; &lt;out.json&gt; &lt;repository root&gt;
 * </pre>
 *
 * A missing report (the build failed before the tool ran) produces an empty Code Quality report, so the job fails for
 * its real reason and not this one.
 */
public final class CodeQuality {

    private record Issue(String checkName, String description, String severity, String path, int line,
        String identity) {
    }

    private CodeQuality() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !List.of("checkstyle", "spotbugs").contains(args[0])) {
            System.err.println("usage: java CodeQuality.java checkstyle|spotbugs <report.xml> <out.json> <root>");
            System.exit(2);
        }
        Path report = Path.of(args[1]);
        Path out = Path.of(args[2]);
        Path root = Path.of(args[3]).toAbsolutePath().normalize();

        List<Issue> issues = List.of();
        if (Files.exists(report)) {
            Document doc = parse(report);
            issues = "checkstyle".equals(args[0]) ? checkstyle(doc, root) : spotbugs(doc, root);
        }
        else {
            System.err.println("No " + args[0] + " report at " + report + "; writing an empty Code Quality report.");
        }
        Files.writeString(out, toJson(issues), StandardCharsets.UTF_8);
        System.out.println("Wrote " + issues.size() + " " + args[0] + " finding(s) to " + out);
    }

    private static List<Issue> checkstyle(Document doc, Path root) {
        List<Issue> issues = new ArrayList<>();
        for (Element file : children(doc.getDocumentElement(), "file")) {
            String path = relative(root, Path.of(file.getAttribute("name")));
            for (Element error : children(file, "error")) {
                String source = error.getAttribute("source");
                String check = source.substring(source.lastIndexOf('.') + 1).replaceFirst("Check$", "");
                String severity = switch (error.getAttribute("severity")) {
                    case "error" -> "critical";
                    case "warning" -> "major"; // what fails the build (violationSeverity=warning)
                    default -> "info";
                };
                String message = error.getAttribute("message");
                issues.add(
                    new Issue("checkstyle:" + check, message, severity, path, intAttr(error, "line"),
                        check + '|' + message)
                );
            }
        }
        return issues;
    }

    private static List<Issue> spotbugs(Document doc, Path root) {
        List<Path> srcDirs = new ArrayList<>();
        for (Element project : children(doc.getDocumentElement(), "Project")) {
            for (Element dir : children(project, "SrcDir")) {
                srcDirs.add(Path.of(dir.getTextContent().trim()));
            }
        }
        List<Issue> issues = new ArrayList<>();
        for (Element bug : children(doc.getDocumentElement(), "BugInstance")) {
            String type = bug.getAttribute("type");
            String severity = switch (bug.getAttribute("priority")) {
                case "1" -> "critical";
                case "2" -> "major";
                default -> "minor";
            };
            String description = text(bug, "LongMessage", text(bug, "ShortMessage", type));
            // The bug's own SourceLine is its primary location; the ones nested in Class/Method describe those.
            List<Element> lines = children(bug, "SourceLine");
            Element line = lines.isEmpty() ? null : lines.get(0);
            String path = "";
            int start = 1;
            if (line != null) {
                path = resolve(root, srcDirs, line.getAttribute("sourcepath"));
                start = intAttr(line, "start");
            }
            // instanceHash is SpotBugs' own line-independent identity for a finding.
            String hash = bug.getAttribute("instanceHash");
            issues.add(
                new Issue("spotbugs:" + type, description, severity, path, start,
                    hash.isEmpty() ? type + '|' + description : hash)
            );
        }
        return issues;
    }

    /** SpotBugs paths are relative to a source directory; finds the one that holds the file. */
    private static String resolve(Path root, List<Path> srcDirs, String sourcePath) {
        for (Path dir : srcDirs) {
            Path candidate = dir.resolve(sourcePath);
            if (Files.exists(candidate)) {
                return relative(root, candidate);
            }
        }
        return srcDirs.isEmpty() ? sourcePath : relative(root, srcDirs.get(0).resolve(sourcePath));
    }

    private static String relative(Path root, Path file) {
        Path abs = file.toAbsolutePath().normalize();
        return (abs.startsWith(root) ? root.relativize(abs) : abs).toString().replace('\\', '/');
    }

    /**
     * Fingerprints leave the line number out, so an unchanged finding keeps its fingerprint when code above it moves
     * and GitLab does not report it as fixed and new again. Identical findings in one file are told apart by order.
     */
    private static String toJson(List<Issue> issues) throws NoSuchAlgorithmException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        Map<String, Integer> seen = new HashMap<>();
        StringBuilder json = new StringBuilder("[");
        for (Issue issue : issues) {
            String key = issue.checkName() + '|' + issue.path() + '|' + issue.identity();
            int occurrence = seen.merge(key, 1, Integer::sum);
            String fingerprint = HexFormat.of()
                .formatHex(sha.digest((key + '|' + occurrence).getBytes(StandardCharsets.UTF_8)));
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("\n  {\"description\":").append(quote(issue.description()))
                .append(",\"check_name\":").append(quote(issue.checkName()))
                .append(",\"fingerprint\":").append(quote(fingerprint))
                .append(",\"severity\":").append(quote(issue.severity()))
                .append(",\"location\":{\"path\":").append(quote(issue.path()))
                .append(",\"lines\":{\"begin\":").append(Math.max(issue.line(), 1)).append("}}}");
        }
        return json.append(issues.isEmpty() ? "]\n" : "\n]\n").toString();
    }

    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static Document parse(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try {
            return factory.newDocumentBuilder().parse(report.toFile());
        }
        catch (IOException e) {
            throw new IOException("Cannot read " + report, e);
        }
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static String text(Element parent, String name, String fallback) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? fallback : found.get(0).getTextContent().trim();
    }

    private static int intAttr(Element element, String name) {
        try {
            return Integer.parseInt(element.getAttribute(name));
        }
        catch (NumberFormatException e) {
            return 1;
        }
    }
}
