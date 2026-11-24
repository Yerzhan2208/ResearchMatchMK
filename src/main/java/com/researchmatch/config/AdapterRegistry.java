package com.researchmatch.config;

import com.researchmatch.adapters.ResearchDataAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registers every {@link ResearchDataAdapter} bean for lookup by platform name (Member 3 owns this file).
 *
 * <p>Wired once, with the single {@code List<ResearchDataAdapter>} injection Sprint provides, so new adapters
 * are picked up automatically.
 */
@Component
public class AdapterRegistry implements ResearchDataSourceRegistry {

    private final List<ResearchDataAdapter> adapters;

    public AdapterRegistry(List<ResearchDataAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    @Override
    public List<ResearchDataAdapter> sources() {
        return adapters;
    }
}
