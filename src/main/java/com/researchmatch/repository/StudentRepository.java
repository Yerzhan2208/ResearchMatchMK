package com.researchmatch.repository;

import com.researchmatch.models.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByEmail(String email);

    /** Faculty-facing lookups MUST filter on this so non-searchable students stay hidden. */
    List<StudentProfile> findBySearchableByFacultyTrue();
}
