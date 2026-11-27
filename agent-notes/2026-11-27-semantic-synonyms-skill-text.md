# Semantic fit score — synonym + structured-skill matching fix (#29)

**Date:** 2026-11-27
**Author:** Member 2 (matching) with the AI agent
**Branch:** `fix/semantic-synonyms-skill-text`
**Status:** done

---

## Problem

The fit score only rose above 0.00 when a student's interest/skill word appeared **verbatim** in the
opportunity's title/description. Non-NLP interests (Machine Learning, Human-Computer Interaction) scored
0.00 against clearly relevant opportunities.

Root causes:
1. `SemanticMatchHandler` built the opportunity text from title + description only — structured
   `requiredSkills`/`preferredSkills` were ignored.
2. `TfIdfSemanticSimilarity` tokenised on non-alphanumerics with no synonym/alias handling, so `ml` vs
   "machine learning" and `hci` vs "human-computer interaction" never overlapped.

## Fix (all inside `matching/`)

- **New `SemanticSynonyms`**: expands common CS/research abbreviations (`ml`, `nlp`, `cv`, `hci`, `db`, …) to
  their canonical phrase and splits hyphenated tokens. Used by `TfIdfSemanticSimilarity.tokenize`.
- **`SemanticMatchHandler.opportunityText`**: now appends `requiredSkills` + `preferredSkills`, so skill-name
  interests overlap the opportunity even when the prose never names them.

## Result (seeded data, weight 0.30)

| Student → Opportunity | Before | After |
| --- | --- | --- |
| Arjun (ML) → Transformer | 0.000 | 0.217 |
| Daniel (HCI) → Usability | 0.000 | 0.326 |
| Sze-Wing (CV) → Vision | 0.061 | 0.288 |

## Tests / verification

- New `SemanticSynonymsTest` (5) + 3 handler tests for skill-text and alias matching.
- `mvn clean verify` (JDK 17): **492 tests, 0 failures, coverage checks met**.

## Follow-ups (separate issues, not touched)

- Score scale mismatch semantic (0.30) vs evidence (10.0) — see #19.
- No base term so ties at 0.0 — see #20.
