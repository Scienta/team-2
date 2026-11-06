# Ratings plan review

**Assessment: revise before execution.**

Reviewed `todo/ratings_plan.md` against the checked-in ratings and talks specs,
the service/controller/DAO code, existing tests, Hurl example, and team planning
instructions. This is a static, documentation-only review: no infrastructure,
tests, or harness requests were run. The plan itself has not been changed.

## Findings

### 1. High: the ranking fixture cannot be created through the API

**Location:** `todo/ratings_plan.md:95-103` (Phase 2, case 8).

The plan creates five talks for one speaker. The talks spec allows only three,
and `ConferenceService.checkSubmissionLimit` enforces that limit through
`TalkDao.insertIfBelow`. The fourth and fifth submissions will return 409,
preventing the ranking assertions from running. This is a fixture defect, not a
ratings implementation failure.

**Recommendation:** distribute the five talks across at least two speakers and
test global ranking, with explicit isolation from other test data. Separately
test filtered ranking using at most three talks for one speaker. Preserve the
useful A/B comparison: their raw averages differ but round to the same value,
so reviewer count must decide their order. Do not raise or bypass the talk limit.
`TestDatabase.fresh` isolates test classes, not individual test methods.

### 2. High: Phase 2 cannot satisfy the required completion gate

**Location:** `todo/ratings_plan.md:94,112-120`.

Phase 2 intentionally leaves the E1 test failing until Phase 3.
`skills/phase_execution.md` requires new and existing tests to pass before a
phase is checked off and committed. An executor cannot both finish Phase 2 as
written and follow that gate.

**Recommendation:** move the E1 regression test into Phase 3 alongside its fix,
or combine Phases 2 and 3 into one red/green phase. Keep expected failures inside
a phase rather than making them its completed deliverable.

### 3. Medium: error-precedence scope and approval are not sufficiently explicit

**Location:** `todo/ratings_plan.md:50-58,94,117-118,129`.

E1 proposes 404 for an "invalid body", while E4 preserves 400 for missing or
unreadable bodies. Reordering `ConferenceService.rate` affects only successfully
bound JSON objects with invalid scores. `TalkController` binds the body before
calling the service; malformed JSON or an array cannot be handled by the proposed
service reorder. Unsupported media types can also produce 415, so "non-JSON"
should not be assigned one status without specifying Content-Type.

The ratings spec does not prescribe these error statuses. The table requests
human confirmation, but execution proceeds unconditionally to the behavior
change, even if harness feedback is unavailable.

**Recommendation:** define E1 as a valid JSON object with an invalid or missing
`score`. Give concrete request bodies and Content-Type values for E4, distinguish
binding errors from service validation, and test the reviewer/talk/score
precedence combinations. Record approval of E1 before implementing it; absent
approval or a contract requirement, retain current behavior. Describe R8 as a
chosen policy, not a demonstrated spec violation.

### 4. Medium: latest-rating timestamp semantics are not tested

**Location:** `todo/ratings_plan.md:70-79,86-88`.

Fixed-instant formatter tests verify formatting and UTC conversion. The HTTP
regex verifies shape, and the re-rating test verifies the score and count.
None verifies that `ratedAtDisplay` describes the latest rating rather than a
stale or unrelated time, as the ratings spec requires. A regression retaining
the original timestamp could pass all these checks.

**Recommendation:** add a focused re-rating case with an intentionally old
stored timestamp, then verify the replacement timestamp and response correspond
to the new request's UTC time window. Account for minute-level display precision;
do not depend on two immediate requests producing different display strings or
sleep across a minute boundary.

### 5. Medium: "wherever a talk is shown" has incomplete aggregate coverage

**Location:** `todo/ratings_plan.md:88-103`.

The plan verifies rated aggregates on individual talk retrieval and unrated
fields on creation and speaker listing. Ranking checks only specify ID order.
A regression returning missing or stale rating fields in list responses could
therefore escape, especially on `GET /speakers/{id}/talks`.

**Recommendation:** after rating and re-rating, assert the target talk's count
and rounded average in the plain list, ranked list, speaker-filtered list, and
speaker talk endpoint. Assert field values on the matching talk object, not
unrelated regex matches somewhere in the response body.

## Strengths to retain

- Clear phase checklist, source references, restart context, and scope boundaries.
- Correct focus on rounded-average ranking, replacement semantics, concurrent
  duplicate submissions, and exact legacy display formatting.
- Small proposed production change and reuse of existing test infrastructure.
- Explicit separation of unresolved policy decisions from published business rules.

## Execution readiness

Fix the ranking setup and phase boundary first, resolve the error-policy decision,
and add the timestamp and list-aggregate assertions. Also make the second
concurrency scenario explicitly use a fresh talk, check every response, and assert
that its final score is one of the submitted scores without assuming which racing
request wins.

After those revisions, the plan is a reasonable, bounded verification effort.
No conclusion about live harness compliance or runtime correctness is implied by
this review.
