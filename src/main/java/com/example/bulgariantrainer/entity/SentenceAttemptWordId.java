package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Embeddable
public class SentenceAttemptWordId implements Serializable {
    private Long attemptId; private Long wordId;
    protected SentenceAttemptWordId() {}
    public SentenceAttemptWordId(Long attemptId,Long wordId){this.attemptId=attemptId;this.wordId=wordId;}
    public boolean equals(Object obj){return obj instanceof SentenceAttemptWordId other && Objects.equals(attemptId,other.attemptId)&&Objects.equals(wordId,other.wordId);}
    public int hashCode(){return Objects.hash(attemptId,wordId);}
}
