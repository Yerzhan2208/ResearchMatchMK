package com.researchmatch.repository;

import com.researchmatch.models.FacultyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacultyRepository extends JpaRepository<FacultyProfile, Long> {

    Optional<FacultyProfile> findByOpenAlexId(String openAlexId);
}
