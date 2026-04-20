package com.werkflow.admin.service;

import com.werkflow.admin.dto.OrganizationRequest;
import com.werkflow.admin.dto.OrganizationResponse;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public OrganizationResponse createOrganization(OrganizationRequest request) {
        log.info("Creating organization: {}", request.getName());

        if (organizationRepository.existsByName(request.getName())) {
            throw new RuntimeException("Organization with name '" + request.getName() + "' already exists");
        }

        Organization organization = Organization.builder()
            .name(request.getName())
            .description(request.getDescription())
            .industry(request.getIndustry())
            .taxId(request.getTaxId())
            .address(request.getAddress())
            .city(request.getCity())
            .state(request.getState())
            .country(request.getCountry())
            .postalCode(request.getPostalCode())
            .phone(request.getPhone())
            .email(request.getEmail())
            .website(request.getWebsite())
            .active(request.getActive() != null ? request.getActive() : true)
            .build();

        organization = organizationRepository.save(organization);
        log.info("Organization created successfully with ID: {}", organization.getId());

        return mapToResponse(organization);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(Long id) {
        log.debug("Fetching organization with ID: {}", id);

        Organization organization = organizationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Organization not found with ID: " + id));

        return mapToResponse(organization);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getAllOrganizations() {
        log.debug("Fetching all organizations");

        return organizationRepository.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getActiveOrganizations() {
        log.debug("Fetching active organizations");

        return organizationRepository.findByActive(true).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public OrganizationResponse updateOrganization(Long id, OrganizationRequest request) {
        log.info("Updating organization with ID: {}", id);

        Organization organization = organizationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Organization not found with ID: " + id));

        if (!organization.getName().equals(request.getName()) &&
            organizationRepository.existsByName(request.getName())) {
            throw new RuntimeException("Organization with name '" + request.getName() + "' already exists");
        }

        organization.setName(request.getName());
        organization.setDescription(request.getDescription());
        organization.setIndustry(request.getIndustry());
        organization.setTaxId(request.getTaxId());
        organization.setAddress(request.getAddress());
        organization.setCity(request.getCity());
        organization.setState(request.getState());
        organization.setCountry(request.getCountry());
        organization.setPostalCode(request.getPostalCode());
        organization.setPhone(request.getPhone());
        organization.setEmail(request.getEmail());
        organization.setWebsite(request.getWebsite());
        if (request.getActive() != null) {
            organization.setActive(request.getActive());
        }

        organization = organizationRepository.save(organization);
        log.info("Organization updated successfully: {}", id);

        return mapToResponse(organization);
    }

    @Transactional
    public void deleteOrganization(Long id) {
        log.info("Deleting organization with ID: {}", id);

        if (!organizationRepository.existsById(id)) {
            throw new RuntimeException("Organization not found with ID: " + id);
        }

        organizationRepository.deleteById(id);
        log.info("Organization deleted successfully: {}", id);
    }

    private OrganizationResponse mapToResponse(Organization organization) {
        return OrganizationResponse.builder()
            .id(organization.getId())
            .name(organization.getName())
            .description(organization.getDescription())
            .industry(organization.getIndustry())
            .taxId(organization.getTaxId())
            .address(organization.getAddress())
            .city(organization.getCity())
            .state(organization.getState())
            .country(organization.getCountry())
            .postalCode(organization.getPostalCode())
            .phone(organization.getPhone())
            .email(organization.getEmail())
            .website(organization.getWebsite())
            .active(organization.getActive())
            .createdAt(organization.getCreatedAt())
            .updatedAt(organization.getUpdatedAt())
            .build();
    }
}
