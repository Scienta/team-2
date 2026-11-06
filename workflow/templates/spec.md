# <Feature name>

Version: <n>
Kind: <base feature | drip feature (released at tick N)>
Source: `workflow/scripts/harness.sh spec <slug>`, copied verbatim below the line, then our
decisions at the end.

## Context

<The user story from the feature request.>

## What we want

<The business rules, verbatim.>

## API

```yaml
<The API surface from the feature request, verbatim.>
```

## Decisions

The feature request isn't a spec: it doesn't give status codes, edge cases or error handling. Every
answer here is ours until a person has asked the product owner. **Ask the PO** about every row whose
behaviour a harness check can see (status codes, idempotency, ordering) before the claim.

| ID | Question | Our answer | Ask the PO? | Approved by (person, PO answer) |
|---|---|---|---|---|
| D1 | <e.g. unknown talk id> | <404> | yes | |
| D2 | <e.g. removing someone who isn't a co-speaker> | <?> | yes | |
| D3 | <e.g. adding the same thing twice> | <200, unchanged> | yes | |
