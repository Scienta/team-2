# Acceptance: the decisions in workflow/specs/acceptance.md beyond acceptance.hurl, with harness-shaped data.
# Run: examples/run.sh [base-url]. Re-runnable on a database that keeps its data.

# Setup: Ada with three talks (W, R, A), Bob as a co-speaker on A
POST {{base_url}}/speakers
{
  "name": "Ada Hamilton b00b",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
ada: jsonpath "$.id"

POST {{base_url}}/speakers
{
  "name": "Alan Dijkstra be00",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
bob: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Waitlisted then rejected",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{ada}}
}
HTTP 201
[Captures]
talk_w: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Rejected",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{ada}}
}
HTTP 201
[Captures]
talk_r: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Accepted then withdrawn",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{ada}}
}
HTTP 201
[Captures]
talk_a: jsonpath "$.id"

PUT {{base_url}}/talks/{{talk_a}}/co-speakers/{{bob}}
HTTP 200

# W: waitlisted, waitlisted again is 409 (D5), then rejected (allowed), rejected again 409 (D5)
PUT {{base_url}}/talks/{{talk_w}}/status
{
  "status": "WAITLISTED"
}
HTTP 200
[Asserts]
jsonpath "$.status" == "WAITLISTED"

PUT {{base_url}}/talks/{{talk_w}}/status
{
  "status": "WAITLISTED"
}
HTTP 409

PUT {{base_url}}/talks/{{talk_w}}/status
{
  "status": "REJECTED"
}
HTTP 200
[Asserts]
jsonpath "$.status" == "REJECTED"

PUT {{base_url}}/talks/{{talk_w}}/status
{
  "status": "REJECTED"
}
HTTP 409

# R: rejected is final: accepting it later is 409, even with two reviewers (D3)
PUT {{base_url}}/talks/{{talk_r}}/status
{
  "status": "REJECTED"
}
HTTP 200

PUT {{base_url}}/talks/{{talk_r}}/ratings/1
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{talk_r}}/ratings/2
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{talk_r}}/status
{
  "status": "ACCEPTED"
}
HTTP 409

PUT {{base_url}}/talks/{{talk_r}}/status
{
  "status": "WAITLISTED"
}
HTTP 409

# D9: the speaker can't withdraw a rejected talk
POST {{base_url}}/talks/{{talk_r}}/withdraw
{
  "speakerId": {{ada}}
}
HTTP 409

GET {{base_url}}/talks/{{talk_r}}
HTTP 200
[Asserts]
jsonpath "$.status" == "REJECTED"

# A: one reviewer rating twice is still one reviewer, so not acceptable yet (D4, D16)
PUT {{base_url}}/talks/{{talk_a}}/ratings/1
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{talk_a}}/ratings/1
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": "ACCEPTED"
}
HTTP 409

GET {{base_url}}/talks/{{talk_a}}
HTTP 200
[Asserts]
jsonpath "$.status" == "SUBMITTED"

# A second reviewer: accepted; accepted again is 409 (D5)
PUT {{base_url}}/talks/{{talk_a}}/ratings/2
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": "ACCEPTED"
}
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"
jsonpath "$.ratingCount" == 2
jsonpath "$.coSpeakerIds[0]" == {{bob}}

PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": "ACCEPTED"
}
HTTP 409

# D2: not a decision: lower case, missing, null, not a string, WITHDRAWN: 400
PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": "accepted"
}
HTTP 400

PUT {{base_url}}/talks/{{talk_a}}/status
{
}
HTTP 400

PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": null
}
HTTP 400

PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": 1
}
HTTP 400

PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": "WITHDRAWN"
}
HTTP 400

# D11: bad bodies: none, not JSON, an array: 400; text/plain: 415; non-numeric id: 400
PUT {{base_url}}/talks/{{talk_a}}/status
Content-Type: application/json
HTTP 400

PUT {{base_url}}/talks/{{talk_a}}/status
Content-Type: application/json
```
not json
```
HTTP 400

PUT {{base_url}}/talks/{{talk_a}}/status
[]
HTTP 400

PUT {{base_url}}/talks/{{talk_a}}/status
Content-Type: text/plain
```
{"status": "REJECTED"}
```
HTTP 415

PUT {{base_url}}/talks/abc/status
{
  "status": "REJECTED"
}
HTTP 400

POST {{base_url}}/talks/{{talk_a}}/withdraw
Content-Type: text/plain
```
{"speakerId": 1}
```
HTTP 415

POST {{base_url}}/talks/{{talk_a}}/withdraw
Content-Type: application/json
HTTP 400

# D6: a co-speaker can't withdraw; D7: nor can an unknown speaker: 403
POST {{base_url}}/talks/{{talk_a}}/withdraw
{
  "speakerId": {{bob}}
}
HTTP 403

POST {{base_url}}/talks/{{talk_a}}/withdraw
{
  "speakerId": 2147483647
}
HTTP 403

# D8: speakerId 0, missing, null, not a number: 400
POST {{base_url}}/talks/{{talk_a}}/withdraw
{
  "speakerId": 0
}
HTTP 400

POST {{base_url}}/talks/{{talk_a}}/withdraw
{
}
HTTP 400

POST {{base_url}}/talks/{{talk_a}}/withdraw
{
  "speakerId": null
}
HTTP 400

POST {{base_url}}/talks/{{talk_a}}/withdraw
{
  "speakerId": "x"
}
HTTP 400

# None of the refused requests changed the talk
GET {{base_url}}/talks/{{talk_a}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"

# An accepted talk can still be withdrawn by its speaker; then it's out for good
POST {{base_url}}/talks/{{talk_a}}/withdraw
{
  "speakerId": {{ada}}
}
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"

PUT {{base_url}}/talks/{{talk_a}}/status
{
  "status": "ACCEPTED"
}
HTTP 409

# D14: rating a withdrawn talk still works, counts, and leaves it withdrawn
PUT {{base_url}}/talks/{{talk_a}}/ratings/3
{
  "score": 3
}
HTTP 200

GET {{base_url}}/talks/{{talk_a}}
HTTP 200
[Asserts]
jsonpath "$.ratingCount" == 3
jsonpath "$.status" == "WITHDRAWN"

# D14: the withdrawn and rejected talks still count towards Ada's 3
POST {{base_url}}/talks
{
  "title": "A fourth",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{ada}}
}
HTTP 409

# D13: the status shows in every list, and no status is filtered out
GET {{base_url}}/speakers/{{ada}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[0].status" == "REJECTED"
jsonpath "$[1].status" == "REJECTED"
jsonpath "$[2].status" == "WITHDRAWN"

GET {{base_url}}/talks?speakerId={{ada}}&sort=rating
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[*].status" contains "WITHDRAWN"
jsonpath "$[*].status" contains "REJECTED"

# D8: speakerId below 1 or not a whole number: -1 and 1.5 are 400 too
POST {{base_url}}/talks/{{talk_r}}/withdraw
{
  "speakerId": -1
}
HTTP 400

POST {{base_url}}/talks/{{talk_r}}/withdraw
{
  "speakerId": 1.5
}
HTTP 400

# D1: an unknown talk is 404 on withdraw too
POST {{base_url}}/talks/2147483647/withdraw
{
  "speakerId": {{ada}}
}
HTTP 404

# D10: 404 before 400: an unknown talk with an invalid speakerId or status is still 404
POST {{base_url}}/talks/2147483647/withdraw
{
  "speakerId": 0
}
HTTP 404

PUT {{base_url}}/talks/2147483647/status
{
  "status": "SUBMITTED"
}
HTTP 404

# D10: 403 before 409: a stranger withdrawing Ada's rejected talk is 403, not 409
POST {{base_url}}/talks/{{talk_r}}/withdraw
{
  "speakerId": {{bob}}
}
HTTP 403

# Setup for D3 and D14: Carol with two talks, each rated by two reviewers
POST {{base_url}}/speakers
{
  "name": "Carol Liskov 3f0a",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
carol: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Accepted, final",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{carol}}
}
HTTP 201
[Captures]
talk_final: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Withdrawn, out for good",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{carol}}
}
HTTP 201
[Captures]
talk_gone: jsonpath "$.id"

PUT {{base_url}}/talks/{{talk_final}}/ratings/1
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{talk_final}}/ratings/2
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{talk_gone}}/ratings/1
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{talk_gone}}/ratings/2
{
  "score": 5
}
HTTP 200

# D3: ACCEPTED is final: waitlisting it is 409
PUT {{base_url}}/talks/{{talk_final}}/status
{
  "status": "ACCEPTED"
}
HTTP 200

PUT {{base_url}}/talks/{{talk_final}}/status
{
  "status": "WAITLISTED"
}
HTTP 409

# D14: co-speakers can still change after a decision, and the decision stays
PUT {{base_url}}/talks/{{talk_final}}/co-speakers/{{bob}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"
jsonpath "$.coSpeakerIds" count == 1

DELETE {{base_url}}/talks/{{talk_final}}/co-speakers/{{bob}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"
jsonpath "$.coSpeakerIds" count == 0

# D3: nothing on a WITHDRAWN talk: rejecting or waitlisting it is 409
POST {{base_url}}/talks/{{talk_gone}}/withdraw
{
  "speakerId": {{carol}}
}
HTTP 200

PUT {{base_url}}/talks/{{talk_gone}}/status
{
  "status": "REJECTED"
}
HTTP 409

PUT {{base_url}}/talks/{{talk_gone}}/status
{
  "status": "WAITLISTED"
}
HTTP 409

# None of the refused decisions changed either talk
GET {{base_url}}/talks/{{talk_final}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"

GET {{base_url}}/talks/{{talk_gone}}
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"
