# There is no product owner

This team has no product owner to ask. Wherever the workflow says "ask the PO" (the decisions table
in a spec, a plan's decisions, the pre-claim checklist), the question goes to a person on the team,
who decides.

**Every such question comes with options and a recommendation**, so the person can decide in
seconds:

```
**D2. GET on an empty room and slot: what's the answer?**
- a) 404 (nothing there, as removing a co-speaker who isn't one)
- b) 200 with `talkId: null`
- Recommendation: a). It matches the 404 the harness accepted for co-speakers, and the API has no
  empty Entry.
```

- Two to four options, each with what it means and, where there is one, the precedent it follows
  (an earlier spec, a decision the harness accepted, the existing code).
- One recommendation, with the reason in a sentence. Say which option is the fallback if a harness
  check fails on it.
- Mark the questions a harness check is most likely to see, and put them first.
- Record the person's answer in the "Approved by" column of the decisions table: who, when, which
  option.
