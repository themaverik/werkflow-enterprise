package com.werkflow.business.inventory.repository;

import com.werkflow.business.hr.entity.OfficeLocation;
import com.werkflow.business.inventory.entity.StockLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockLocationRepository extends JpaRepository<StockLocation, Long> {
    List<StockLocation> findByOfficeLocation(OfficeLocation officeLocation);
    List<StockLocation> findByDepartmentCode(String departmentCode);
    List<StockLocation> findByActive(Boolean active);
}
