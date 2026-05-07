package com.cisternino.application.service.hints;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class BlockMapper {

    /**
     * Maps findings (which have line numbers) back to block indices.
     * Returns findings enriched with block index information.
     */
    public List<BlockFinding> map(List<AnalysisFinding> findings, Map<Integer, Integer> lineToBlockIndex) {
        List<BlockFinding> mapped = new ArrayList<>();
        Set<Integer> seenBlocks = new HashSet<>();

        for (AnalysisFinding finding : findings) {
            Integer blockIndex = lineToBlockIndex.get(finding.startLine());
            if (blockIndex != null && seenBlocks.add(blockIndex)) {
                // Only report each block once (even if multiple findings point to it)
                mapped.add(new BlockFinding(blockIndex, finding));
            }
        }

        return mapped;
    }

    public record BlockFinding(int blockIndex, AnalysisFinding finding) {}
}
