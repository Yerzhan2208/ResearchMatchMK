package com.researchmatch.matching;

import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the defensive branches of {@link MatchingEngine} that the happy-path tests do not reach:
 * a repository returning {@code null}, a negative configured result cap, and the chain head's
 * {@code layerName()} delegation. Kept separate from {@code MatchingEngineTest}.
 */
class MatchingEngineBranchCoverageTest {

    private static MatchingEngine engine(OpportunityRepository opportunities, SystemConfigManager config) {
        StudentRepository students = mock(StudentRepository.class);
        List<RankingStrategy> strategies = List.of(
                new BalancedMatchStrategy(), new BeginnerFriendlyStrategy(), new SkillStrictStrategy());
        return new MatchingEngine(students, opportunities, config, strategies);
    }

    private static StudentProfile student() {
        StudentProfile s = new StudentProfile();
        s.setId(1L);
        s.setEmail("s@x.edu");
        s.setAcademicYear(2);
        s.setHoursPerWeek(10);
        return s;
    }

    @Test
    void rankOpportunities_handlesNullRepositoryResult() {
        StudentRepository students = mock(StudentRepository.class);
        when(students.findById(1L)).thenReturn(Optional.of(student()));
        OpportunityRepository opportunities = mock(OpportunityRepository.class);
        when(opportunities.findAll()).thenReturn(null);
        SystemConfigManager config = mock(SystemConfigManager.class);
        when(config.getInt(anyString(), anyInt())).thenReturn(20);

        MatchingEngine engine = new MatchingEngine(students, opportunities, config,
                List.of(new BalancedMatchStrategy(), new BeginnerFriendlyStrategy(), new SkillStrictStrategy()));

        assertThat(engine.rankOpportunities(1L, "balanced")).isEmpty();
    }

    @Test
    void rankOpportunities_negativeCapYieldsEmpty() {
        StudentRepository students = mock(StudentRepository.class);
        when(students.findById(1L)).thenReturn(Optional.of(student()));
        Opportunity opp = new Opportunity(10L, 3L, "Beginner project", "desc");
        opp.setBeginnerFriendly(true);   // no required-skill gate, so the pair is eligible
        opp.setHoursPerWeek(5);
        opp.setMinAcademicYear(1);
        OpportunityRepository opportunities = mock(OpportunityRepository.class);
        when(opportunities.findAll()).thenReturn(List.of(opp));
        SystemConfigManager config = mock(SystemConfigManager.class);
        when(config.getInt(anyString(), anyInt())).thenReturn(-5);
        when(config.getDouble(anyString(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(0.30);

        MatchingEngine engine = new MatchingEngine(students, opportunities, config,
                List.of(new BalancedMatchStrategy(), new BeginnerFriendlyStrategy(), new SkillStrictStrategy()));

        assertThat(engine.rankOpportunities(1L, "balanced")).isEmpty();
    }

    @Test
    void chainHead_layerNameDelegatesToFirstHandler() throws Exception {
        MatchingEngine engine = engine(mock(OpportunityRepository.class), mock(SystemConfigManager.class));
        Field head = MatchingEngine.class.getDeclaredField("head");
        head.setAccessible(true);
        MatchLayerHandler handler = (MatchLayerHandler) head.get(engine);
        // The outer link wraps eligibility, so its layerName delegates to "eligibility".
        assertThat(handler.layerName()).isEqualTo(EligibilityFilterHandler.LAYER);
    }
}
