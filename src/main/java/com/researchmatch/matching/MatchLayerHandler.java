package com.researchmatch.matching;

import java.util.Objects;

/**
 * One link in the Chain of Responsibility used to evaluate a single student–opportunity pair.
 *
 * <p>Rules every implementation must follow (see {@link AbstractMatchLayerHandler} for the shared glue):
 * <ul>
 *   <li>Read {@code context.getStudent()} / {@code context.getOpportunity()}; write only to the context.</li>
 *   <li>Never return null and never throw for ordinary data conditions — report them on the context.</li>
 *   <li>Handlers are Spring singletons, so implementations must be stateless.</li>
 * </ul>
 */
public interface MatchLayerHandler {

    /** Stable name used as the key in {@link MatchContext#getLayerScores()}, e.g. {@code "semantic"}. */
    String layerName();

    /**
     * Evaluates this layer.
     *
     * @param context never null
     * @return never null
     */
    LayerResult evaluate(MatchContext context);

    /** What a single layer decided. */
    enum LayerResult {
        /** This layer is satisfied; continue with the next handler. */
        CONTINUE,
        /** Everything is satisfied and there is no next handler to run. */
        CHAIN_COMPLETE,
        /** A rule failed; stop the chain (the context records the reason). */
        STOP
    }

    /**
     * Runs this handler then {@code next} (when present), honouring {@link LayerResult}.
     *
     * @param context never null
     * @param next    the continuation, or null at the tail of the chain
     * @return never null
     */
    default MatchContext handle(MatchContext context, MatchLayerHandler next) {
        Objects.requireNonNull(context, "context must not be null");
        LayerResult result = evaluate(context);
        if (result == null) {
            throw new IllegalStateException(layerName() + " returned a null LayerResult");
        }
        if (result == LayerResult.STOP || !context.isEligible()) {
            return context;
        }
        return next == null ? context : next.handle(context, null);
    }
}
