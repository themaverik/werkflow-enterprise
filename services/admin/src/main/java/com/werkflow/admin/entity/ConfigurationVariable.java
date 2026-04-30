package com.werkflow.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_variables")
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ConfigurationVariable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tenantCode;

    @Column(nullable = false, length = 100)
    private String varKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String varValue;

    @Column(nullable = false, length = 20)
    private String varType = "STRING";

    @Column(length = 500)
    private String description;

    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate @Column(nullable = false)
    private LocalDateTime updatedAt;
}
