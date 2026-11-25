package com.researchmatch.matching;

import com.researchmatch.config.InvalidRequestException;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.OpportunityRepository;
import com.researchmatch.repository.StudentRepository;
import com.researchmatch.repository.SystemConfigManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingContractsTest {

    private final StudentRepository students = Mockito.mock(StudentRepository.class);
    private final OpportunityRepository opportunities = Mockito.mock(OpportunityRepository.class);
    private final SystemConfigManager config = SystemConfigManager.getInstance();

    private MatchingEngine engine(List<RankingStrategy> strategies) {
        return new MatchingEngine(students, opportunities, config, strategies);
    }

    private static final RankingStrategy BALANCED = new BalancedMatchStrategy();
    private static final RankingStrategy BEGINNER = new BeginnerFriendlyStrategy();
    private static final RankingStrategy STRICT = new SkillStrictStrategy();

    @Test
    void strategiesExposeStableNames() {
        assertThat(BALANCED.name()).isEqualTo(BalancedMatchStrategy.NAME);
        assertThat(BEGINNER.name()).isEqualTo(BeginnerFriendlyStrategy.NAME);
        assertThat(STRICT.name()).isEqualTo(SkillStrictStrategy.NAME);
    }

    @Test
    void engineIndexesStrategiesByConcreteName() {
        MatchingEngine engine = engine(List.of(BALANCED, BEGINNER, STRICT));

        assertThat(engine.strategy(BalancedMatchStrategy.NAME)).isSameAs(BALANCED);
        assertThat(engine.strategy(BeginnerFriendlyStrategy.NAME)).isSameAs(BEGINNER);
        assertThat(engine.strategy(SkillStrictStrategy.NAME)).isSameAs(STRICT);
    }

    @Test
    void engineFallsBackToBalancedWhenNameIsMissing() {
        MatchingEngine engine = engine(List.of(BALANCED, BEGINNER));

        assertThat(engine.strategy(null)).isSameAs(BALANCED);
        assertThat(engine.strategy("   ")).isSameAs(BALANCED);
        assertThat(engine.strategy(" balanced ")).isSameAs(BALANCED);
    }

    @Test
    void engineRejectsUnknownStrategy() {
        MatchingEngine engine = engine(List.of(BALANCED));

        assertThatThrownBy(() -> engine.strategy("nonsense"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("nonsense");
    }

    @Test
    void engineExposesCollaborators() {
        MatchingEngine engine = engine(List.of(BALANCED));

        assertThat(engine.getStudentRepository()).isSameAs(students);
        assertThat(engine.getOpportunityRepository()).isSameAs(opportunities);
        assertThat(engine.getConfig()).isSameAs(config);
    }

    @Test
    void engineRejectsNullCollaborators() {
        assertThatThrownBy(() -> new MatchingEngine(null, opportunities, config, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchingEngine(students, null, config, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchingEngine(students, opportunities, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> engine(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void enginePlaceholdersAreExplicitTodos() {
        MatchingEngine engine = engine(List.of(BALANCED));
        MatchContext context = engine.newContext(new StudentProfile(), new Opportunity());

        assertThatThrownBy(() -> engine.rankOpportunities(1L, null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("rankOpportunities");
        assertThat(context).isNotNull();
    }

    @Test
    void strategiesRankEmptyInputToNewNonNullList() {
        List<MatchResult> balanced = BALANCED.rank(List.of());
        List<MatchResult> beginner = BEGINNER.rank(List.of());
        List<MatchResult> strict = STRICT.rank(List.of());

        assertThat(balanced).isNotNull().isEmpty();
        assertThat(beginner).isNotNull().isEmpty();
        assertThat(strict).isNotNull().isEmpty();
    }

    @Test
    void handlersDeclareTheirLayer() {
        assertThat(new EligibilityFilterHandler().layerName()).isEqualTo(EligibilityFilterHandler.LAYER);
        assertThat(new SemanticMatchHandler().layerName()).isEqualTo(SemanticMatchHandler.LAYER);
        assertThat(new EvidenceScoreHandler().layerName()).isEqualTo(EvidenceScoreHandler.LAYER);
        assertThat(new ExplanationHandler().layerName()).isEqualTo(ExplanationHandler.LAYER);
    }

    @Test
    void abstractHandlerRejectsNullContext() {
        AbstractMatchLayerHandler handler = new AbstractMatchLayerHandler() {
            @Override
            public String layerName() {
                return "test";
            }

            @Override
            protected LayerResult apply(MatchContext context) {
                return LayerResult.CONTINUE;
            }
        };

        assertThatThrownBy(() -> handler.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test");
    }

    @Test
    void abstractHandlerRejectsNullResult() {
        AbstractMatchLayerHandler handler = new AbstractMatchLayerHandler() {
            @Override
            public String layerName() {
                return "test";
            }

            @Override
            protected LayerResult apply(MatchContext context) {
                return null;
            }
        };

        assertThatThrownBy(() -> handler.evaluate(new MatchContext(new StudentProfile(), new Opportunity())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null LayerResult");
    }

    @Nested
    class ChainBehaviour {

        private final MatchContext context = new MatchContext(new StudentProfile(), new Opportunity());

        private MatchLayerHandler fixed(String name, MatchLayerHandler.LayerResult result) {
            return new AbstractMatchLayerHandler() {
                @Override
                public String layerName() {
                    return name;
                }

                @Override
                protected LayerResult apply(MatchContext ctx) {
                    ctx.addExplanationNote(name);
                    return result;
                }
            };
        }

        @Test
        void walksTheWholeChainInOrder() {
            MatchLayerHandler head = fixed("a", MatchLayerHandler.LayerResult.CONTINUE);
            MatchLayerHandler second = fixed("b", MatchLayerHandler.LayerResult.CONTINUE);
            MatchLayerHandler tail = fixed("c", MatchLayerHandler.LayerResult.CHAIN_COMPLETE);

            MatchContext result = head.handle(context, chain(second, tail));

            assertThat(result).isSameAs(context);
            assertThat(context.getExplanationNotes()).containsExactly("a", "b", "c");
        }

        @Test
        void stopsWhenALayerAsksToStop() {
            MatchLayerHandler head = fixed("a", MatchLayerHandler.LayerResult.STOP);
            MatchLayerHandler never = fixed("never", MatchLayerHandler.LayerResult.CONTINUE);

            head.handle(context, never);

            assertThat(context.getExplanationNotes()).containsExactly("a");
        }

        @Test
        void stopsWhenContextBecomesIneligible() {
            MatchLayerHandler head = new AbstractMatchLayerHandler() {
                @Override
                public String layerName() {
                    return "filter";
                }

                @Override
                protected LayerResult apply(MatchContext ctx) {
                    ctx.markIneligible("too many hours");
                    return LayerResult.CONTINUE;
                }
            };
            MatchLayerHandler never = fixed("never", MatchLayerHandler.LayerResult.CONTINUE);

            head.handle(context, never);

            assertThat(context.isEligible()).isFalse();
            assertThat(context.getExplanationSummary()).isEqualTo("too many hours");
            assertThat(context.getExplanationNotes()).doesNotContain("never");
        }

        @Test
        void tailHandlerCompletesWithoutContinuation() {
            MatchLayerHandler only = fixed("only", MatchLayerHandler.LayerResult.CHAIN_COMPLETE);

            assertThat(only.handle(context, null)).isSameAs(context);
            assertThat(context.getExplanationNotes()).containsExactly("only");
        }

        @Test
        void handleRejectsNullContext() {
            assertThatThrownBy(() -> fixed("a", MatchLayerHandler.LayerResult.CONTINUE).handle(null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rawHandlerReturningNullResultIsRejected() {
            MatchLayerHandler bad = new MatchLayerHandler() {
                @Override
                public String layerName() {
                    return "bad";
                }

                @Override
                public LayerResult evaluate(MatchContext ctx) {
                    return null;
                }
            };

            assertThatThrownBy(() -> bad.handle(context, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null LayerResult");
        }

        private MatchLayerHandler chain(MatchLayerHandler... handlers) {
            MatchLayerHandler current = null;
            for (int i = handlers.length - 1; i >= 0; i--) {
                MatchLayerHandler next = current;
                MatchLayerHandler handler = handlers[i];
                current = wrap(handler, next);
            }
            return current;
        }

        /** Wraps a handler so it defers to {@code next}, mimicking how MatchingEngine assembles the chain. */
        private MatchLayerHandler wrap(MatchLayerHandler handler, MatchLayerHandler next) {
            return new MatchLayerHandler() {
                @Override
                public String layerName() {
                    return handler.layerName();
                }

                @Override
                public LayerResult evaluate(MatchContext ctx) {
                    return handler.evaluate(ctx);
                }

                @Override
                public MatchContext handle(MatchContext ctx, MatchLayerHandler ignored) {
                    return MatchLayerHandler.super.handle(ctx, next);
                }
            };
        }
    }

    @Nested
    class ContextBehaviour {

        @Test
        void collectsScoresSkillsAndNotes() {
            MatchContext context = new MatchContext(new StudentProfile(), new Opportunity());

            context.addScore(SemanticMatchHandler.LAYER, 40.0);
            context.addScore(EvidenceScoreHandler.LAYER, 5.5);
            context.addMatchedSkills(java.util.Arrays.asList("Java", null, "SQL"));
            context.addMissingSkills(List.of("Python"));
            context.addMissingSkills(null);
            context.addMatchedSkills(null);
            context.addExplanationNote("  ");
            context.addExplanationNote(null);
            context.addExplanationNote("good fit");
            context.setRecommendedPrep("read docs");

            assertThat(context.getTotalScore()).isEqualTo(45.5);
            assertThat(context.getLayerScores()).containsEntry(SemanticMatchHandler.LAYER, 40.0);
            assertThat(context.getMatchedSkills()).containsExactly("Java", "SQL");
            assertThat(context.getMissingSkills()).containsExactly("Python");
            assertThat(context.getExplanationNotes()).containsExactly("good fit");
            assertThat(context.getExplanationSummary()).isEqualTo("good fit");
            assertThat(context.getRecommendedPrep()).isEqualTo("read docs");
        }

        @Test
        void verifiedSkillsAreNullableSafe() {
            StudentProfile student = new StudentProfile();
            student.setVerifiedSkills(java.util.Set.of("Java"));
            MatchContext context = new MatchContext(student, new Opportunity());

            assertThat(context.verifiedSkillSet()).containsExactly("Java");
        }

        @Test
        void rejectsNullEntities() {
            assertThatThrownBy(() -> new MatchContext(null, new Opportunity()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new MatchContext(new StudentProfile(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void eligibilityCanBeToggled() {
            MatchContext context = new MatchContext(new StudentProfile(), new Opportunity());
            assertThat(context.isEligible()).isTrue();

            context.setEligible(false);
            assertThat(context.isEligible()).isFalse();

            context.setEligible(true);
            context.markIneligible("cap reached");
            assertThat(context.isEligible()).isFalse();
            assertThat(context.getExplanationSummary()).contains("cap reached");
        }

        @Test
        void summaryIsEmptyWhenNoNotes() {
            MatchContext context = new MatchContext(new StudentProfile(), new Opportunity());
            assertThat(context.getExplanationSummary()).isEmpty();
        }
    }
}
