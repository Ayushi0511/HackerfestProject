package de.telekom.ai.etl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JiraIssue {
    private String id;
    private String key;
    private String summary;
    private String status;
    private String statusCategory;
    private String issueType;
    private String created;
    private String project;
    private String assignee;
    private String description;
}
