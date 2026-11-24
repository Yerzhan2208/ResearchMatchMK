package com.researchmatch.repository;

import com.researchmatch.models.SkillEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillEvidenceRepository extends JpaRepository<SkillEvidence, Long> {

    List<SkillEvidence> findByStudentId(Long studentId);

    List<SkillEvidence> findByStudentIdAndVerifiedByStudentTrue(Long studentId);
}
