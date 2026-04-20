package com.werkflow.engine.action.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTemplateKeyAndDeletedAtIsNull(String templateKey);

    List<NotificationTemplate> findAllByDeletedAtIsNull();
}
