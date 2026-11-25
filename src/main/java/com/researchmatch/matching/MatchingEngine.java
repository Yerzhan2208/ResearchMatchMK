package com.researchmatch.matching;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.factory.MatchResultBuilder;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import com.researchmatch.repository.SystemConfigManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Public entry point for student–opportunity matching (Member 2).
 *
 * <p>Consumers: {@code ResearchMatchFacade} (Member 4) and tests (Member 5). {@link #evaluate(Long, Long)}
 * runs the Eligibility → Semantic → Evidence → Explanation chain and assembles a {@link MatchResult} with
 * {@link MatchResultBuilder}. {@link #rankOpportunities(Long, String)} is not implemented yet.
 */
@Service
public class MatchingEngine {

    private final StudentRepository studentRepository;
    private final OpportunityRepository opportunityRepository;
    private final SystemConfigManager config;
    private final Map<String, RankingStrategy> strategies;
    private final MatchLayerHandler head;

    /**
     * @param strategies every {@link RankingStrategy} bean, keyed by {@link RankingStrategy#name()}
     */
    public MatchingEngine(StudentRepository studentRepository,
                          OpportunityRepository opportunityRepository,
                          SystemConfigManager config,
                          List<RankingStrategy> strategies) {
        this.studentRepository = Objects.requireNonNull(studentRepository, "studentRepository");
        this.opportunityRepository = Objects.requireNonNull(opportunityRepository, "opportunityRepository");
        this.config = Objects.requireNonNull(config, "config");
        this.strategies = Objects.requireNonNull(strategies, "strategies").stream()
                .collect(Collectors.toUnmodifiableMap(RankingStrategy::name, Function.identity()));
        this.head = buildChain(config);
    }

    /**
     * Assembles Eligibility → Semantic → Evidence → Explanation. The {@code SemanticMatchHandler} shares the
     * engine's {@link SystemConfigManager} so weight resolution is consistent.
     */
    private static MatchLayerHandler buildChain(SystemConfigManager config) {
        MatchLayerHandler explanation = new ExplanationHandler();
        MatchLayerHandler evidence = new EvidenceScoreHandler();
        MatchLayerHandler semantic = new SemanticMatchHandler(config);
        MatchLayerHandler eligibility = new EligibilityFilterHandler();
        return link(eligibility, link(semantic, link(evidence, explanation)));
    }

    /** Links {@code handler} before {@code next} by overriding {@code handle} to defer to it. */
    private static MatchLayerHandler link(MatchLayerHandler handler, MatchLayerHandler next) {
        return new MatchLayerHandler() {
            @Override
            public String layerName() {
                return handler.layerName();
            }

            @Override
            public LayerResult evaluate(MatchContext context) {
                return handler.evaluate(context);
            }

            @Override
            public MatchContext handle(MatchContext context, MatchLayerHandler ignored) {
                return MatchLayerHandler.super.handle(context, next);
            }
        };
    }

    /**
     * Evaluates one pair through the handler chain and builds the result.
     *
     * @return never null; {@code eligible == false} carries the reason in the explanation
     * @throws ResourceNotFoundException if either id does not exist
     * @throws InvalidRequestException   if an id is null
     */
    public MatchResult evaluate(Long studentId, Long opportunityId) {
        if (studentId == null || opportunityId == null) {
            throw new InvalidRequestException("Both a student id and an opportunity id are required");
        }
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student " + studentId + " not found"));
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity " + opportunityId + " not found"));
        return evaluatePair(student, opportunity);
    }

    /** Runs the chain for an already-loaded pair and builds its {@link MatchResult}. Never null. */
    private MatchResult evaluatePair(StudentProfile student, Opportunity opportunity) {
        MatchContext context = newContext(student, opportunity);
        MatchContext done = head.handle(context, null);

        return new MatchResultBuilder()
                .studentId(student.getId())
                .opportunityId(opportunity.getId())
                .totalScore(done.getTotalScore())
                .eligible(done.isEligible())
                .matchedSkills(done.getMatchedSkills())
                .missingSkills(done.getMissingSkills())
                .explanationSummary(done.getExplanationSummary())
                .recommendedPrep(done.getRecommendedPrep())
                .build();
    }

    /**
     * Evaluates {@code studentId} against every (or every beginner-friendly) opportunity and ranks them.
     *
     * @param strategyName a {@link RankingStrategy#name()}; unknown names raise {@code InvalidRequestException}
     * @return never null, ordered by the strategy, capped at {@code matching.maxResults}
     * @throws ResourceNotFoundException if the student does not exist
     * TODO (Member 2)
     */
    public List<MatchResult> rankOpportunities(Long studentId, String strategyName) {
        throw new UnsupportedOperationException("TODO (Member 2): MatchingEngine.rankOpportunities");
    }

    /** Resolves a configured strategy name; the default name is {@link BalancedMatchStrategy#NAME}. */
    public RankingStrategy strategy(String strategyName) {
        String wanted = (strategyName == null || strategyName.isBlank())
                ? BalancedMatchStrategy.NAME : strategyName.trim();
        RankingStrategy strategy = strategies.get(wanted);
        if (strategy == null) {
            throw new InvalidRequestException("Unknown ranking strategy: " + wanted);
        }
        return strategy;
    }

    public StudentRepository getStudentRepository() {
        return studentRepository;
    }

    public OpportunityRepository getOpportunityRepository() {
        return opportunityRepository;
    }

    public SystemConfigManager getConfig() {
        return config;
    }

    /** Convenience for subclasses/tests; no business logic. */
    protected MatchContext newContext(StudentProfile student, Opportunity opportunity) {
        return new MatchContext(student, opportunity);
    }
}
