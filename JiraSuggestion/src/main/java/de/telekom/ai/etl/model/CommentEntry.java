package de.telekom.ai.etl.model;

import lombok.Data;
import lombok.ToString;

import java.time.OffsetDateTime;

@ToString
@Data
public class CommentEntry {
    private final OffsetDateTime created;
    private final String comment;
    private final String authorName;

    public CommentEntry(OffsetDateTime created, String comment, String authorName) {
        this.created = created;
        this.comment = comment;
        this.authorName = authorName;
    }

    public OffsetDateTime getCreated() {
        return created;
    }

    public String getComment() {
        return comment;
    }
    public String getAuthorName() {
        return authorName;
    }
}

