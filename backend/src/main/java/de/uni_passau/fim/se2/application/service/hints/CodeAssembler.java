package de.uni_passau.fim.se2.application.service.hints;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class CodeAssembler {

    private static final String INDENT_UNIT = "    "; // 4 spaces per indent level

    /**
     * Assembles block order into complete code with proper indentation.
     * Each block gets prefixed with spaces based on its indent level.
     */
    public CodeAssembly assemble(List<String> blockOrder, List<Integer> indentLevels) {
        StringBuilder code = new StringBuilder();
        Map<Integer, Integer> lineToBlockIndex = new HashMap<>();
        int currentLine = 1;

        for (int blockIdx = 0; blockIdx < blockOrder.size(); blockIdx++) {
            String block = blockOrder.get(blockIdx);
            int indent = (indentLevels != null && blockIdx < indentLevels.size())
                    ? indentLevels.get(blockIdx) : 0;
            String prefix = INDENT_UNIT.repeat(Math.max(0, indent));

            String[] lines = block.split("\n");
            for (String line : lines) {
                code.append(prefix).append(line).append("\n");
                lineToBlockIndex.put(currentLine, blockIdx);
                currentLine++;
            }
        }

        return new CodeAssembly(code.toString(), lineToBlockIndex, blockOrder);
    }

    public record CodeAssembly(
        String code,
        Map<Integer, Integer> lineToBlockIndex, // line number -> block index
        List<String> blockOrder
    ) {}
}
