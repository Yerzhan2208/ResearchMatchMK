# Member 4 — assigned GitHub issues

## Summary

- Reviewed the six open issues assigned to `0xZgetsu` on top of `main` at `d5c7c1a`.
- Scoped API candidate discovery to the current demo professor and gated the professor dashboard (#16, #23). Checked that invalid, blank, and missing role values return HTTP 400 (#24).
- Made profile saves apply only submitted fields to the current record, preserving imported verified skills and existing consent unless the student explicitly changes it (#17, #18).
- Made repeat imports reuse evidence rows for the same student, skill, and source; offline evidence stays unverified (#22).

**Status:** done locally.

## Files touched

- Member 4 controllers and facade in `src/main/java/com/researchmatch/controllers/`.
- `src/main/resources/templates/profile.html` and controller tests under `src/test/java/com/researchmatch/controllers/`.
- This hand-off note. No frozen contracts, repositories, or schema changes.

## Verification

- `mvn -B clean verify` with Maven 3.9.9 and JDK 17: 592 tests passed, 100% line coverage (1,821/1,821), and 96.93% branch coverage (758/782).
- `git diff --check` passed.

## Follow-ups

- Link the six issues when publishing the branch. The demo role still represents a view selected by the visitor.

## Open questions for the group

- None for this scoped fix.
