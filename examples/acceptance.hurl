# Acceptance: our examples (decisions D1 to D16 in workflow/specs/acceptance.md).
# Run: examples/run.sh [base-url]

# Setup: speaker Ada with talks A and B, speaker Bob with talk C
POST {{base_url}}/speakers
{ "name": "Ada Lovelace", "email": "ada-{{newUuid}}@example.com" }
HTTP 201
[Captures]
ada: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Bob Kahn", "email": "bob-{{newUuid}}@example.com" }
HTTP 201
[Captures]
bob: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Notes on the engine", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ada}} }
HTTP 201
[Captures]
talk_a: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Bernoulli numbers", "format": "WORKSHOP", "level": "ADVANCED", "speakerId": {{ada}} }
HTTP 201
[Captures]
talk_b: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Packets", "format": "LIGHTNING_10", "level": "INTERMEDIATE", "speakerId": {{bob}} }
HTTP 201
[Captures]
talk_c: jsonpath "$.id"

# Two reviewers rate talk A
PUT {{base_url}}/talks/{{talk_a}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_a}}/ratings/2
{ "score": 5 }
HTTP 200

# A: waitlisted, then accepted
PUT {{base_url}}/talks/{{talk_a}}/status
{ "status": "WAITLISTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "WAITLISTED"

PUT {{base_url}}/talks/{{talk_a}}/status
{ "status": "ACCEPTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"
jsonpath "$.ratingCount" == 2

# D3: accepted is final
PUT {{base_url}}/talks/{{talk_a}}/status
{ "status": "REJECTED" }
HTTP 409

# D4: C has one reviewer, so it can't be accepted
PUT {{base_url}}/talks/{{talk_c}}/ratings/1
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_c}}/status
{ "status": "ACCEPTED" }
HTTP 409

# D2: SUBMITTED isn't a decision
PUT {{base_url}}/talks/{{talk_b}}/status
{ "status": "SUBMITTED" }
HTTP 400

# D1: unknown talk
PUT {{base_url}}/talks/2147483647/status
{ "status": "REJECTED" }
HTTP 404

# D6: Bob can't withdraw Ada's talk
POST {{base_url}}/talks/{{talk_b}}/withdraw
{ "speakerId": {{bob}} }
HTTP 403

# Ada withdraws B
POST {{base_url}}/talks/{{talk_b}}/withdraw
{ "speakerId": {{ada}} }
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"

# D5, D9: withdrawn is out for good
POST {{base_url}}/talks/{{talk_b}}/withdraw
{ "speakerId": {{ada}} }
HTTP 409

# D13: B is still listed, with its status
GET {{base_url}}/talks
[Query]
speakerId: {{ada}}
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[?(@.id == {{talk_b}})].status" == "WITHDRAWN"
jsonpath "$[?(@.id == {{talk_a}})].status" == "ACCEPTED"
