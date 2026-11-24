package com.researchmatch.repository;

import com.researchmatch.models.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByStudentId(Long studentId);

    List<Application> findByOpportunityId(Long opportunityId);

    List<Application> findByStatus(Application.Status status);

    long countByOpportunityIdAndStatus(Long opportunityId, Application.Status status);
}
