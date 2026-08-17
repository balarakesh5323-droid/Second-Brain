package com.secondbrain.common.entity;

import com.secondbrain.common.enums.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "agent_events", uniqueConstraints = {
    @UniqueConstraint(name = "uk_agent_events_session_sequence", columnNames = {"session_id", "sequence_number"})
})
public class AgentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private AgentSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private EventType eventType;

    @Column(name = "sequence_number", updatable = false)
    private Integer sequenceNumber;

    @Column(updatable = false)
    private String description;

    @Column(updatable = false)
    private String filePath;

    @Column(columnDefinition = "text", updatable = false)
    private String details;

    @Column(name = "processing_status", nullable = false)
    @Builder.Default
    private String processingStatus = "COMPLETED";

    public String getStatus() {
        return processingStatus;
    }

    public void setStatus(String status) {
        this.processingStatus = status;
    }
}
