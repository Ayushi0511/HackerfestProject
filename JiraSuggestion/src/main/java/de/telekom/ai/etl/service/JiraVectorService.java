package de.telekom.ai.etl.service;

import de.telekom.ai.etl.model.JiraComment;
import de.telekom.ai.etl.model.JiraRawFields;
import de.telekom.ai.etl.model.JiraRawIssue;
import de.telekom.ai.etl.model.JiraUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JiraVectorService {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(JiraVectorService.class);
    private final VectorStore vectorStore; // Auto-configured via Spring

    public void saveIssues(List<JiraRawIssue> issues) {
        List<Document> docs = issues.stream()
                .map(this::mapToVectorDocument)
                .peek(doc -> LOGGER.info("Mapped Document: {}", doc))
                .toList();

        vectorStore.add(docs);
    }

    private Document mapToVectorDocument(JiraRawIssue issue) {
        JiraRawFields fields = issue.getFields();

        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Summary: ").append(safe(fields.getSummary())).append("\n\n");
        contentBuilder.append("Description: ").append(safe(fields.getDescription())).append("\n\n");
        contentBuilder.append("Status: ").append(getName(fields.getStatus())).append("\n");
        contentBuilder.append("Priority: ").append(getName(fields.getPriority())).append("\n");
        contentBuilder.append("Issue Type: ").append(getName(fields.getIssuetype())).append("\n");
        contentBuilder.append("Project: ").append(fields.getProject().getName()).append("\n");
        contentBuilder.append("Assignee: ").append(getDisplayName(fields.getAssignee())).append("\n");
        contentBuilder.append("Reporter: ").append(getDisplayName(fields.getReporter())).append("\n");

        String content = contentBuilder.toString();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", issue.getId());
        metadata.put("key", issue.getKey());
        metadata.put("created", fields.getCreated());

        metadata.put("status.name", getName(fields.getStatus()));
        metadata.put("priority.name", getName(fields.getPriority()));
        metadata.put("issuetype.name", getName(fields.getIssuetype()));
        metadata.put("project.key", fields.getProject().getKey());
        metadata.put("project.Id", fields.getProject().getId());
        metadata.put("project.name", fields.getProject().getName());

        metadata.put("assignee.accountId", getAccountId(fields.getAssignee()));
        metadata.put("assignee.name", getDisplayName(fields.getAssignee()));
        metadata.put("assignee.comments", summarizeComments(fields.getAssignee()!=null?fields.getAssignee().getComments():null));

        metadata.put("reporter.accountId", getAccountId(fields.getReporter()));
        metadata.put("reporter.name", getDisplayName(fields.getReporter()));
        metadata.put("reporter.comments", summarizeComments(fields.getReporter()!=null?fields.getReporter().getComments():null));

        return new Document(content, metadata);
    }

    private String summarizeComments(List<JiraComment> comments) {
        if (comments == null || comments.isEmpty()) return "";
        return comments.stream()
                .map(c -> String.format("created:{{ %s }}, body:{{ %s }}", c.getCreated(), c.getBody()))
                .collect(Collectors.joining("\n---\n"));
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String getDisplayName(JiraUser user) {
        return user != null ? user.getDisplayName() : "Unassigned";
    }

    private String getAccountId(JiraUser user) {
        return user != null ? user.getAccountId() : "unknown";
    }

    private String getName(Object obj) {
        if (obj == null) return "unknown";
        try {
            Method method = obj.getClass().getMethod("getName");
            return (String) method.invoke(obj);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public List<Document> searchSimilarIssues(String query,int topk) {
        return vectorStore.similaritySearch(query);
    }
}

