package com.tal.pro.model;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Objects;

@Data
@Document(collection = "resumes")
public class Resume {
    @Id
    private String id;
    private String originalFileName;
    private String originalFileType;
    private Long originalFileSize;
    private LocalDateTime uploadedAt;
    private String htmlContent;
    @Field("originalFileData")
    private byte[] originalFileData;
    
    @DBRef(lazy = true)
    private Candidate candidate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resume resume = (Resume) o;
        return Objects.equals(id, resume.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
