package com.researchmatch.config;

import com.researchmatch.models.Application;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.FacultyRepository;
import com.researchmatch.repository.OpportunityRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigPackageTest {

    @Test
    void exceptionHierarchyIsCohesive() {
        assertThat(new ResourceNotFoundException("x")).isInstanceOf(ResearchMatchException.class);
        assertThat(new DuplicateEntityException("x")).isInstanceOf(ResearchMatchException.class);
        assertThat(new InvalidRequestException("x")).isInstanceOf(ResearchMatchException.class);
        assertThat(new IllegalStateTransitionException("x")).isInstanceOf(ResearchMatchException.class);
        assertThat(ResearchMatchException.class).isAssignableTo(RuntimeException.class);
        assertThat(new ResearchMatchException("x", new IllegalStateException())).hasCauseInstanceOf(
                IllegalStateException.class);
    }

    @Test
    void privacyRemainsOffByDefault() {
        assertThat(new StudentProfile().isSearchableByFaculty()).isFalse();
        assertThat(Application.Status.NEW).isNotNull();
        assertThat(new Opportunity().isBeginnerFriendly()).isFalse();
    }

    @Test
    void seederIsIdempotentWhenDataExists() {
        FacultyRepository faculty = Mockito.mock(FacultyRepository.class);
        OpportunityRepository opportunities = Mockito.mock(OpportunityRepository.class);
        when(opportunities.count()).thenReturn(2L);

        new DataSeeder(faculty, opportunities).run();

        verify(faculty, never()).save(any());
        verify(opportunities, never()).saveAll(anyList());
    }

    @Test
    void seederPublishesSampleDataOnce() {
        FacultyRepository faculty = Mockito.mock(FacultyRepository.class);
        OpportunityRepository opportunities = Mockito.mock(OpportunityRepository.class);
        when(opportunities.count()).thenReturn(0L);
        when(faculty.save(any(com.researchmatch.models.FacultyProfile.class))).thenAnswer(invocation -> {
            com.researchmatch.models.FacultyProfile saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        new DataSeeder(faculty, opportunities).run();

        verify(faculty).save(any(com.researchmatch.models.FacultyProfile.class));
        verify(opportunities).saveAll(anyList());
    }
}
