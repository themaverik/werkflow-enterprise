package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.AssetRequest;
import com.werkflow.business.inventory.entity.AssetRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRequestRepository extends JpaRepository<AssetRequest, Long> {
    Optional<AssetRequest> findByProcessInstanceId(String processInstanceId);
    List<AssetRequest> findByRequesterUserId(String requesterUserId);
    List<AssetRequest> findByDepartmentCode(String departmentCode);
    List<AssetRequest> findByStatus(AssetRequestStatus status);
}
