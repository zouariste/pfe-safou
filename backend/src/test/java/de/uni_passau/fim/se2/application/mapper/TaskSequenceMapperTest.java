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
package de.uni_passau.fim.se2.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.GenerationException;

@DisplayName("mapping the model's sequence answer")
class TaskSequenceMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode task(int tier, String statement, String taskStatement) {
        ObjectNode t = MAPPER.createObjectNode().put("tier", tier).put("statement", statement);
        ObjectNode c = t.putObject("contract")
            .put("language", "python")
            .put("functionSignature", "def f(x):")
            .put("purposeBehavior", "Returns x.")
            .put("prePostConditions", "none");
        if (taskStatement != null) {
            c.put("taskStatement", taskStatement);
        }
        c.putArray("examples").addObject().put("input", "f(1)").put("output", "1");
        return t;
    }

    private static ObjectNode answer(ObjectNode... tasks) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode arr = root.putArray("tasks");
        for (ObjectNode t : tasks) {
            arr.add(t);
        }
        return root;
    }

    private static TieredTaskSequenceDTO map(ObjectNode json) {
        return TaskSequenceMapper.mapToIncompleteSequence(json, List.of("loops"), "python", "x = 1", "desc", 3);
    }

    @Test
    @DisplayName("a reworded contract.taskStatement is replaced by the statement instead of failing")
    void rewordedTaskStatementUsesStatement() {
        TieredTaskSequenceDTO seq = map(
            answer(
                task(1, "Gib x zurück.", "Gib x zurück."),
                task(2, "Verdopple x.", "Die Funktion verdoppelt x."),
                task(3, "Quadriere x.", null)
            )
        );

        List<TaskDTO> tasks = seq.getTasks();
        assertEquals("Gib x zurück.", tasks.get(0).getContract().taskStatement());
        assertEquals("Verdopple x.", tasks.get(1).getContract().taskStatement());
        assertEquals("Quadriere x.", tasks.get(2).getContract().taskStatement());
    }

    @Test
    @DisplayName("a malformed answer is a GenerationException, so the generation loops retry it")
    void malformedAnswerIsGenerationException() {
        ObjectNode tooFew = answer(task(1, "a", "a"), task(2, "b", "b"));
        assertThrows(GenerationException.class, () -> map(tooFew));

        ObjectNode blankField = answer(task(1, "a", "a"), task(2, "b", "b"), task(3, "c", "c"));
        ((ObjectNode) blankField.get("tasks").get(2).get("contract")).put("functionSignature", " ");
        assertThrows(GenerationException.class, () -> map(blankField));
    }
}
