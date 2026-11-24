package com.researchmatch;

import com.researchmatch.adapters.GitHubSkillAdapter;
import com.researchmatch.adapters.OpenAlexPublicationAdapter;
import com.researchmatch.adapters.ResearchDataAdapter;
import com.researchmatch.config.AdapterRegistry;
import com.researchmatch.config.DataSeeder;
import com.researchmatch.config.ResearchDataSourceRegistry;
import com.researchmatch.controllers.ResearchMatchFacade;
import com.researchmatch.factory.MatchResultBuilder;
import com.researchmatch.factory.OpportunityBuilder;
import com.researchmatch.matching.BalancedMatchStrategy;
import com.researchmatch.matching.BeginnerFriendlyStrategy;
import com.researchmatch.matching.EligibilityFilterHandler;
import com.researchmatch.matching.EvidenceScoreHandler;
import com.researchmatch.matching.ExplanationHandler;
import com.researchmatch.matching.MatchLayerHandler;
import com.researchmatch.matching.MatchingEngine;
import com.researchmatch.matching.RankingStrategy;
import com.researchmatch.matching.SemanticMatchHandler;
import com.researchmatch.matching.SkillStrictStrategy;
import com.researchmatch.pipeline.AcceptedState;
import com.researchmatch.pipeline.ApplicationObserver;
import com.researchmatch.pipeline.ApplicationState;
import com.researchmatch.pipeline.ApplicationStateFactory;
import com.researchmatch.pipeline.ApplicationSubject;
import com.researchmatch.pipeline.AuditLogObserver;
import com.researchmatch.pipeline.InterviewState;
import com.researchmatch.pipeline.NewState;
import com.researchmatch.pipeline.NotificationObserver;
import com.researchmatch.pipeline.RecruitmentPipeline;
import com.researchmatch.pipeline.RejectedState;
import com.researchmatch.pipeline.ShortlistedState;
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
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline wiring check: the context loads and every shared bean the team agreed on is injectable.
 * If this fails, a Member 2–5 branch cannot integrate.
 */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;

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

    @Autowired
    private MatchingEngine matchingEngine;

    @Autowired
    private RecruitmentPipeline recruitmentPipeline;

    @Autowired
    private ResearchMatchFacade researchMatchFacade;

    @Autowired
    private ApplicationStateFactory applicationStateFactory;

    @Autowired
    private ApplicationSubject applicationSubject;

    @Autowired
    private ResearchDataSourceRegistry researchDataSourceRegistry;

    @Test
    void contextLoadsWithAllRepositories() {
        assertThat(studentRepository).isNotNull();
        assertThat(facultyRepository).isNotNull();
        assertThat(opportunityRepository).isNotNull();
        assertThat(applicationRepository).isNotNull();
        assertThat(skillEvidenceRepository).isNotNull();
        assertThat(auditEventRepository).isNotNull();
    }

    @Test
    void springBeanIsTheSingletonInstance() {
        assertThat(systemConfigManager).isSameAs(SystemConfigManager.getInstance());
    }

    @Test
    void allSharedServiceBeansInject() {
        assertThat(matchingEngine).isNotNull();
        assertThat(recruitmentPipeline).isNotNull();
        assertThat(researchMatchFacade).isNotNull();
        assertThat(applicationStateFactory).isNotNull();
        assertThat(applicationSubject).isNotNull();
        assertThat(researchDataSourceRegistry).isNotNull();
    }

    @Test
    void chainHandlersAreSpringBeans() {
        assertThat(context.getBean(EligibilityFilterHandler.class)).isInstanceOf(MatchLayerHandler.class);
        assertThat(context.getBean(SemanticMatchHandler.class)).isInstanceOf(MatchLayerHandler.class);
        assertThat(context.getBean(EvidenceScoreHandler.class)).isInstanceOf(MatchLayerHandler.class);
        assertThat(context.getBean(ExplanationHandler.class)).isInstanceOf(MatchLayerHandler.class);
    }

    @Test
    void allThreeStrategiesAreRegistered() {
        List<String> names = context.getBeansOfType(RankingStrategy.class).values().stream()
                .map(RankingStrategy::name).sorted().toList();
        assertThat(names).containsExactly(
                BalancedMatchStrategy.NAME, BeginnerFriendlyStrategy.NAME, SkillStrictStrategy.NAME);
    }

    @Test
    void everyApplicationStatusHasAStateBean() {
        assertThat(context.getBeansOfType(ApplicationState.class)).hasSize(5);
        assertThat(applicationStateFactory.forStatus(com.researchmatch.models.Application.Status.NEW))
                .isInstanceOf(NewState.class);
        assertThat(applicationStateFactory.forStatus(com.researchmatch.models.Application.Status.ACCEPTED))
                .isInstanceOf(AcceptedState.class);
    }

    @Test
    void defaultObserversAreRegisteredOnTheSubject() {
        assertThat(context.getBeansOfType(ApplicationObserver.class).values())
                .hasAtLeastOneElementOfType(AuditLogObserver.class)
                .hasAtLeastOneElementOfType(NotificationObserver.class);
        assertThat(applicationSubject.observers()).isNotEmpty();
    }

    @Test
    void bothAdaptersAreRegisteredByPlatformName() {
        assertThat(context.getBeansOfType(ResearchDataAdapter.class).values())
                .hasAtLeastOneElementOfType(GitHubSkillAdapter.class)
                .hasAtLeastOneElementOfType(OpenAlexPublicationAdapter.class);
        assertThat(context.getBean(AdapterRegistry.class).sources()).isNotEmpty();
    }

    @Test
    void buildersAreConstructible() {
        assertThat(new MatchResultBuilder()).isNotNull();
        assertThat(new OpportunityBuilder()).isNotNull();
    }

    @Test
    void dataSeederRunsOnStartup() {
        assertThat(context.getBean(DataSeeder.class)).isNotNull();
        assertThat(opportunityRepository.count()).isPositive();
    }

    @Test
    void statesAndFactoriesAreDistinctBeans() {
        assertThat(context.getBean(ShortlistedState.class)).isNotNull();
        assertThat(context.getBean(InterviewState.class)).isNotNull();
        assertThat(context.getBean(RejectedState.class)).isNotNull();
    }
}
