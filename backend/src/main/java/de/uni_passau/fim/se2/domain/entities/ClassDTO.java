package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassDTO {

    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private boolean trackingEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Students in this class (loaded eagerly on detail view) */
    @Builder.Default
    private List<StudentInfo> students = new ArrayList<>();

    /** Worksheets assigned to this class */
    @Builder.Default
    private List<WorksheetInfo> worksheets = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentInfo {
        private UUID userId;
        private String firstName;
        private String lastName;
        private String email;
        private String inviteToken;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorksheetInfo {
        private UUID worksheetId;
        private String name;
        private String description;
    }
}
