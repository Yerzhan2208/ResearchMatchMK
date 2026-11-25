package com.researchmatch.pipeline;

import com.researchmatch.config.IllegalStateTransitionException;
import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.models.Application;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.ApplicationRepository;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentPipelineTest {

    private static final Long APP_ID = 42L;
    private static final Long STUDENT_ID = 1L;
    private static final Long OPPORTUNITY_ID = 2L;

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private OpportunityRepository opportunityRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ApplicationSubject subject;

    private RecruitmentPipeline pipeline;

    @BeforeEach
    void setUp() {
        // Real state graph; the subject is mocked so notifications can be counted exactly.
        AcceptedState accepted = new AcceptedState();
        RejectedState rejected = new RejectedState();
        InterviewState interview = new InterviewState(accepted, rejected);
        ShortlistedState shortlisted = new ShortlistedState(interview, rejected);
        NewState fresh = new NewState(shortlisted, rejected);
        ApplicationStateFactory factory =
                new ApplicationStateFactory(List.of(fresh, shortlisted, interview, accepted, rejected));

        pipeline = new RecruitmentPipeline(
                applicationRepository, opportunityRepository, studentRepository, factory, subject);
    }

    /** Stubs loading an application in {@code status} and echoes whatever is saved. */
    private Application givenAnApplicationIn(Application.Status status) {
        Application application = new Application(APP_ID, STUDENT_ID, OPPORTUNITY_ID);
        application.setStatus(status);
        lenient().when(applicationRepository.findById(APP_ID)).thenReturn(Optional.of(application));
        lenient().when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return application;
    }

    // ================================================================== submit

    @Nested
    class Submit {

        @BeforeEach
        void entitiesExist() {
            lenient().when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(new StudentProfile()));
            lenient().when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.of(new Opportunity()));
            lenient().when(applicationRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of());
            lenient().when(applicationRepository.save(any(Application.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        void storesANewApplicationAndNotifiesOnce() {
            Application saved = pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, "I would love to help");

            assertThat(saved.getStudentId()).isEqualTo(STUDENT_ID);
            assertThat(saved.getOpportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(saved.getStatement()).isEqualTo("I would love to help");
            assertThat(saved.getStatus()).isEqualTo(Application.Status.NEW);
            verify(applicationRepository).save(any(Application.class));
            verify(subject, times(1)).notifyStatusChanged(saved, Application.Status.NEW, Application.Status.NEW);
        }

        @Test
        void rejectsAnUnknownStudent() {
            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, "hi"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Student");
            verify(applicationRepository, never()).save(any());
            verifyNoInteractions(subject);
        }

        @Test
        void rejectsAnUnknownOpportunity() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, "hi"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Opportunity");
            verify(applicationRepository, never()).save(any());
            verifyNoInteractions(subject);
        }

        @Test
        void rejectsNullIds() {
            assertThatThrownBy(() -> pipeline.submit(null, OPPORTUNITY_ID, "hi"))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> pipeline.submit(STUDENT_ID, null, "hi"))
                    .isInstanceOf(InvalidRequestException.class);
            verify(applicationRepository, never()).save(any());
        }

        @Test
        void rejectsAnOverlongStatement() {
            String tooLong = "x".repeat(RecruitmentPipeline.MAX_STATEMENT_LENGTH + 1);

            assertThatThrownBy(() -> pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, tooLong))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("4000");
            verify(applicationRepository, never()).save(any());
        }

        @Test
        void acceptsAStatementAtTheLengthLimit() {
            String atLimit = "x".repeat(RecruitmentPipeline.MAX_STATEMENT_LENGTH);

            Application saved = pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, atLimit);

            assertThat(saved.getStatement()).hasSize(RecruitmentPipeline.MAX_STATEMENT_LENGTH);
        }

        @Test
        void allowsANullStatement() {
            Application saved = pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, null);

            assertThat(saved.getStatement()).isNull();
        }

        @Test
        void refusesASecondApplicationForTheSamePair() {
            Application existing = new Application(99L, STUDENT_ID, OPPORTUNITY_ID);
            when(applicationRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(existing));

            assertThatThrownBy(() -> pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, "again"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("already applied");
            verify(applicationRepository, never()).save(any());
            verifyNoInteractions(subject);
        }

        @Test
        void ignoresApplicationsToOtherOpportunitiesWhenCheckingDuplicates() {
            Application elsewhere = new Application(99L, STUDENT_ID, 777L);
            when(applicationRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(elsewhere));

            Application saved = pipeline.submit(STUDENT_ID, OPPORTUNITY_ID, "first time here");

            assertThat(saved.getStatus()).isEqualTo(Application.Status.NEW);
        }
    }

    // ================================================================== valid transitions

    @Nested
    class AllowedTransitions {

        @Test
        void shortlistMovesNewToShortlisted() {
            givenAnApplicationIn(Application.Status.NEW);

            Application result = pipeline.shortlist(APP_ID);

            assertThat(result.getStatus()).isEqualTo(Application.Status.SHORTLISTED);
        }

        @Test
        void scheduleInterviewMovesShortlistedToInterview() {
            givenAnApplicationIn(Application.Status.SHORTLISTED);

            Application result = pipeline.scheduleInterview(APP_ID);

            assertThat(result.getStatus()).isEqualTo(Application.Status.INTERVIEW);
        }

        @Test
        void acceptMovesInterviewToAccepted() {
            givenAnApplicationIn(Application.Status.INTERVIEW);

            Application result = pipeline.accept(APP_ID);

            assertThat(result.getStatus()).isEqualTo(Application.Status.ACCEPTED);
        }

        @Test
        void rejectWorksFromNewShortlistedAndInterview() {
            for (Application.Status from : List.of(
                    Application.Status.NEW, Application.Status.SHORTLISTED, Application.Status.INTERVIEW)) {
                givenAnApplicationIn(from);

                Application result = pipeline.reject(APP_ID);

                assertThat(result.getStatus()).as("reject from %s", from)
                        .isEqualTo(Application.Status.REJECTED);
            }
        }

        @Test
        void persistsBeforeNotifyingWithTheRightFromAndTo() {
            Application application = givenAnApplicationIn(Application.Status.SHORTLISTED);

            pipeline.scheduleInterview(APP_ID);

            ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
            verify(applicationRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(Application.Status.INTERVIEW);
            verify(subject, times(1)).notifyStatusChanged(
                    application, Application.Status.SHORTLISTED, Application.Status.INTERVIEW);
        }

        @Test
        void notifiesExactlyOncePerSuccessfulTransition() {
            givenAnApplicationIn(Application.Status.NEW);

            pipeline.shortlist(APP_ID);
            pipeline.scheduleInterview(APP_ID);
            pipeline.accept(APP_ID);

            verify(subject, times(3)).notifyStatusChanged(any(), any(), any());
        }
    }

    // ================================================================== invalid transitions

    @Nested
    class DeniedTransitions {

        /** Each row: current status and the transition that must be refused from it. */
        private static List<Object[]> deniedMoves() {
            return List.of(
                    new Object[]{Application.Status.NEW, "interview"},
                    new Object[]{Application.Status.NEW, "accept"},
                    new Object[]{Application.Status.SHORTLISTED, "shortlist"},
                    new Object[]{Application.Status.SHORTLISTED, "accept"},
                    new Object[]{Application.Status.INTERVIEW, "shortlist"},
                    new Object[]{Application.Status.ACCEPTED, "shortlist"},
                    new Object[]{Application.Status.ACCEPTED, "interview"},
                    new Object[]{Application.Status.ACCEPTED, "accept"},
                    new Object[]{Application.Status.ACCEPTED, "reject"},
                    new Object[]{Application.Status.REJECTED, "shortlist"},
                    new Object[]{Application.Status.REJECTED, "interview"},
                    new Object[]{Application.Status.REJECTED, "accept"},
                    new Object[]{Application.Status.REJECTED, "reject"});
        }

        private Application call(String move) {
            return switch (move) {
                case "shortlist" -> pipeline.shortlist(APP_ID);
                case "interview" -> pipeline.scheduleInterview(APP_ID);
                case "accept" -> pipeline.accept(APP_ID);
                case "reject" -> pipeline.reject(APP_ID);
                default -> throw new IllegalArgumentException(move);
            };
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("deniedMoves")
        void illegalJumpIsRefusedAndNothingIsWritten(Application.Status from, String move) {
            givenAnApplicationIn(from);

            assertThatThrownBy(() -> call(move))
                    .isInstanceOf(IllegalStateTransitionException.class);

            verify(applicationRepository, never()).save(any());
            verifyNoInteractions(subject);
        }

        @Test
        void rejectedCannotBeResurrected() {
            Application application = givenAnApplicationIn(Application.Status.REJECTED);

            assertThatThrownBy(() -> pipeline.shortlist(APP_ID))
                    .isInstanceOf(IllegalStateTransitionException.class);

            assertThat(application.getStatus()).isEqualTo(Application.Status.REJECTED);
        }

        @Test
        void acceptedIsFinal() {
            Application application = givenAnApplicationIn(Application.Status.ACCEPTED);

            assertThatThrownBy(() -> pipeline.reject(APP_ID))
                    .isInstanceOf(IllegalStateTransitionException.class);

            assertThat(application.getStatus()).isEqualTo(Application.Status.ACCEPTED);
        }
    }

    // ================================================================== lookup + stateOf

    @Nested
    class Lookups {

        @Test
        void unknownApplicationIsNotFound() {
            when(applicationRepository.findById(APP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipeline.shortlist(APP_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("42");
        }

        @Test
        void nullApplicationIdIsNotFound() {
            assertThatThrownBy(() -> pipeline.shortlist(null))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(applicationRepository, never()).findById(anyLong());
        }

        @ParameterizedTest
        @EnumSource(Application.Status.class)
        void stateOfResolvesTheStateForEveryStatus(Application.Status status) {
            givenAnApplicationIn(status);

            assertThat(pipeline.stateOf(APP_ID).status()).isEqualTo(status);
        }

        @Test
        void stateOfFailsForAnUnknownApplication() {
            when(applicationRepository.findById(APP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipeline.stateOf(APP_ID)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void exposesItsCollaborators() {
            assertThat(pipeline.getApplicationRepository()).isSameAs(applicationRepository);
            assertThat(pipeline.getOpportunityRepository()).isSameAs(opportunityRepository);
            assertThat(pipeline.getStudentRepository()).isSameAs(studentRepository);
            assertThat(pipeline.getStateFactory()).isNotNull();
            assertThat(pipeline.getSubject()).isSameAs(subject);
        }
    }
}
