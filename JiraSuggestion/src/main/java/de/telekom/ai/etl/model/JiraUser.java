package de.telekom.ai.etl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraUser {
    private String displayName;
    private String emailAddress;
    private String accountId;
    private List<JiraComment> comments;
}
