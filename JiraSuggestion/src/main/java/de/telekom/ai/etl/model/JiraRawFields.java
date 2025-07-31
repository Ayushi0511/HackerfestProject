package de.telekom.ai.etl.model;

import jakarta.annotation.Priority;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraRawFields {
    private String summary;
    private String created;
    private String description;
    private JiraStatus status;
    private JiraIssueType issuetype;
    private JiraProject project;
    private JiraUser assignee;
    private JiraUser reporter;
    private JiraPriority priority;
}








