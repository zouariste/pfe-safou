package com.cisternino.application.service;

import com.cisternino.domain.entities.TieredTaskSequenceDTO;

public class SequenceValidator {

    public static void validateLocLimit(TieredTaskSequenceDTO seq, int maxLoc) {
        boolean tooLarge = seq.getTasks().stream()
                .anyMatch(t -> t.getLinesOfCode() > maxLoc);

        if (tooLarge) {
            throw new IllegalStateException("Sequence invalid: LOC > " + maxLoc);
        }
    }

}