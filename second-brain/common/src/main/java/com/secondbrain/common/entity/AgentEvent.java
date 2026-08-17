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
@Table(name = "agent_events")
public class AgentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AgentSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private EventType eventType;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    private String description;

    private String filePath;

    @Column(columnDefinition = "text")
    private String details;

    @Column(nullable = false)
    private String status;
}
