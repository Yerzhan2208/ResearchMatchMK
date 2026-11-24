package com.researchmatch;

import com.researchmatch.repository.ApplicationRepository;
import com.researchmatch.repository.AuditEventRepository;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.SkillEvidenceRepository;
import com.researchmatch.repository.StudentRepository;
import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private OpportunityRepository opportunityRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private SkillEvidenceRepository skillEvidenceRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private SystemConfigManager systemConfigManager;

    @Test
    void contextLoadsWithAllRepositories() {
        assertThat(studentRepository).isNotNull();
        assertThat(facultyRepository).isNotNull();
        assertThat(opportunityRepository).isNotNull();
        assertThat(applicationRepository).isNotNull();
        assertThat(skillEvidenceRepository).isNotNull();
        assertThat(auditEventRepository).isNotNull();

        assertThat(studentRepository.count()).isZero();
        assertThat(opportunityRepository.count()).isZero();
    }

    @Test
    void springBeanIsTheSingletonInstance() {
        assertThat(systemConfigManager).isSameAs(SystemConfigManager.getInstance());
    }
}
