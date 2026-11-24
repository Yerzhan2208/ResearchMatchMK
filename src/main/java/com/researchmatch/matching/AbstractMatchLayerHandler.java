package com.researchmatch.matching;

/**
 * Shared plumbing for {@link MatchLayerHandler} implementations.
 *
 * <p>Subclasses only implement {@link #apply(MatchContext)}; the chain plumbing and null checks live here.
 * TODO (Member 2): make every concrete handler a stateless Spring bean extending this class, and replace
 * the {@code UnsupportedOperationException}s in {@link #apply(MatchContext)}.
 */
public abstract class AbstractMatchLayerHandler implements MatchLayerHandler {

    /**
     * Applies this layer's rules to {@code context}.
     *
     * @param context never null; not yet checked for eligibility by this layer
     * @return never null
     */
    protected abstract MatchLayerHandler.LayerResult apply(MatchContext context);

    @Override
    public final MatchLayerHandler.LayerResult evaluate(MatchContext context) {
        if (context == null) {
            throw new IllegalArgumentException(layerName() + " requires a non-null MatchContext");
        }
        MatchLayerHandler.LayerResult result = apply(context);
        if (result == null) {
            throw new IllegalStateException(layerName() + " returned a null LayerResult");
        }
        return result;
    }
}
