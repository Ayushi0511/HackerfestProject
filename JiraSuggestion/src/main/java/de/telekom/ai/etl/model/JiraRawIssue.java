package de.telekom.ai.etl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraRawIssue {
    private String id;
    private String key;
    private JiraRawFields fields;
}
