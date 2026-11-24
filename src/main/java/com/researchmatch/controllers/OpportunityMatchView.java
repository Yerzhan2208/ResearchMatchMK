package com.researchmatch.controllers;

import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;

/**
 * Read-only view model that pairs a ranked {@link MatchResult} with the {@link Opportunity} it scores.
 *
 * <p>Lets Thymeleaf render the discovery feed without template logic: the facade supplies the score and the
 * explanation; this record just bundles them for the view layer.
 */
public record OpportunityMatchView(MatchResult match, Opportunity opportunity) {
}
