package com.werkflow.business.procurement.repository;

import com.werkflow.business.procurement.entity.Vendor;
import com.werkflow.business.procurement.entity.Vendor.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    List<Vendor> findByStatus(VendorStatus status);

    List<Vendor> findByStatusIn(List<VendorStatus> statuses);

    @Query("SELECT v FROM Vendor v WHERE v.status = 'ACTIVE' ORDER BY v.rating DESC")
    List<Vendor> findActiveVendorsOrderByRating();

    @Query("SELECT v FROM Vendor v WHERE v.status = 'ACTIVE' " +
           "AND (v.rating IS NULL OR v.rating >= :minRating)")
    List<Vendor> findActiveVendorsByMinRating(@Param("minRating") BigDecimal minRating);

    @Query("SELECT v FROM Vendor v WHERE LOWER(v.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(v.contactPerson) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Vendor> searchVendors(@Param("searchTerm") String searchTerm);

    boolean existsByNameIgnoreCase(String name);
}
