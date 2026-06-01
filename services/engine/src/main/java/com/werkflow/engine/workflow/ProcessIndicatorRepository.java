package com.werkflow.engine.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessIndicatorRepository extends JpaRepository<ProcessIndicator, String> {
}
