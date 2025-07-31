package de.telekom.ai.etl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraComment {
    private String id;
    private String body;
    private String created;
    private String updated;
    private JiraUser author;
    private JiraUser updateAuthor;
}
