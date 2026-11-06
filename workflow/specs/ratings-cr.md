# Change request: ratings

Version: 1
Kind: change request (released at tick 167)
Source: `workflow/scripts/harness.sh spec ratings-cr`, copied verbatim below the line, then our
decisions at the end. Changes [Ratings](ratings.md); everything not named here stays as that spec says.

---

## Context

As the programme committee, I want a talk's average to ignore the single most enthusiastic and the single harshest reviewer once enough reviewers have rated it, and to show two decimals, so that one outlier can't decide the ranking and close talks are told apart.

## What we want

This changes how [Ratings](../ratings/spec.md) calculates `averageRating`, and so the ranking of `GET /talks?sort=rating`. Everything else about ratings stays as it is.

- Once a talk has 5 or more ratings, its average leaves out one highest and one lowest score: exactly one of each, even when several reviewers gave the same top or bottom score. With fewer than 5 ratings, every score counts, as before.
- The average is rounded to 2 decimals. Ties at the third decimal go to the even neighbour (banker's rounding): `3.125` becomes `3.12` and `3.375` becomes `3.38`.
- `ratingCount` still counts every reviewer, including the ones left out of the average.
- The ranking uses the new, rounded average.

## API

No new endpoints. The changed field on `Talk` ([Talk submissions](../talks/spec.md), [Ratings](../ratings/spec.md)):

```yaml
components:
  schemas:
    # averageRating: { type: number, nullable: true }   # now trimmed at 5+ ratings and rounded to 2 decimals
```

## Decisions

The feature request isn't a spec: it doesn't give status codes, edge cases or error handling. Every
answer here is ours until a person on the team has approved it (we have no product owner:
`.claude/rules/no-product-owner.md`). **Ask the PO** about every row whose
behaviour a harness check can see (status codes, idempotency, ordering) before the claim.

The questions a harness check is most likely to see come first.

| ID | Question | Our answer | Ask the PO? | Approved by (person, PO answer) |
|---|---|---|---|---|
| D1 | How is "rounded to 2 decimals, half-even" computed? | a) Exact arithmetic: `BigDecimal(sum).divide(BigDecimal(n), 2, HALF_EVEN)` on the integer sum and count, so `25/8 = 3.125` gives `3.12` and `27/8 = 3.375` gives `3.38`. b) `Math.rint(avg * 100) / 100` on a double: binary floating point can misplace exact ties. **Recommendation: a)**, it is exact for every k/n and is what `calculateAverage` already does with `HALF_UP` and scale 1. Fallback: none needed, a) is the rule. | yes | agent default (a), pending human (issue #39) |
| D2 | Which scores are left out at 5+ ratings when several reviewers share the top or bottom score? | a) Exactly one occurrence of the max and one of the min: average = (sum - max - min) / (n - 2). With all scores equal that is the same score. b) Leave out every reviewer with the top or bottom score. **Recommendation: a)**, the request says "exactly one of each, even when several reviewers gave the same top or bottom score". | yes | agent default (a), pending human (issue #39) |
| D3 | What counts toward the 5-rating threshold? | a) The talk's current ratings, one per reviewer (`ratingCount`, after re-ratings replaced old scores), so a double-click or a changed score never adds one. b) Every PUT ever received. **Recommendation: a)**, a reviewer has at most one rating per talk (Ratings spec) and `ratingCount` is the same number. | yes | agent default (a), pending human (issue #39) |
| D4 | How does `averageRating` look in JSON? | a) A JSON number from a Java `Double` as now: `4.0`, `4.25`, `4.33` (no trailing zero padding; `WireFormatTest` pins `"averageRating":4.0`). b) Always two decimals (`4.00`) via a scaled `BigDecimal`. **Recommendation: a)**, numerically equal for any JSON client, and it keeps `WireFormatTest` unchanged as `AGENTS.md` requires. Fallback if a check compares text: b). | yes | agent default (a), pending human (issue #39) |
| D5 | Ranking on `sort=rating`: what is compared? | a) The rounded 2-decimal average descending, then `ratingCount` descending, then oldest first; unrated talks last, as in Ratings. Two talks whose exact averages differ but round to the same 2 decimals tie on the average. b) The unrounded average. **Recommendation: a)**, the request says "the ranking uses the new, rounded average", and the tie-breaks are the Ratings rules it leaves unchanged. | yes | agent default (a), pending human (issue #39) |
| D6 | Fewer than 5 ratings: does the 2-decimal rounding apply? | a) Yes: every score counts, but the result is rounded to 2 decimals half-even (`4, 4, 4, 5` shows `4.25`, no longer `4.3`). b) Keep 1 decimal half-up below 5. **Recommendation: a)**, the rounding bullet has no threshold; only the trimming does. | yes | agent default (a), pending human (issue #39) |
| D7 | `GET /admin/stats` `averageRating` (mean of the talks' averages, rounded down to 1 decimal) | a) Unchanged rule, fed by the new per-talk averages. b) Also 2 decimals half-even. **Recommendation: a)**, the request changes the talk's `averageRating` only ("everything else about ratings stays as it is"), and stats isn't in a base spec. | no | agent default (a), pending human (issue #39) |
| D8 | `GET /admin/talks.csv` `rating` column | a) Shows the new average with a decimal comma (`4,25`), as it prints `averageRating` today. b) Keep 1 decimal. **Recommendation: a)**, no code change, the column is the talk's average. | no | agent default (a), pending human (issue #39) |
| D9 | Acceptance's "at least 2 ratings to accept" rule | a) Unchanged, counts every reviewer (`ratingCount`). **Recommendation: a)**, `ratingCount` is unchanged by the request. | no | agent default (a), pending human (issue #39) |
