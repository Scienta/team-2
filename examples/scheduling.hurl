# Programme scheduling: our examples (decisions D1 to D20 in workflow/specs/scheduling.md).
# Run: examples/run.sh [base-url]
#
# Safe against the deployed service: nothing here is ever put on the programme. The deployed programme has
# only 15 places and the harness uses them too, so every request below is refused before the insert
# (D13's order puts all of these before "taken"). The happy path is in examples/local/, local only.

# Setup: a submitted talk and an accepted workshop, each with its own speaker
POST {{base_url}}/speakers
{ "name": "Ada Lovelace", "email": "ada-{{newUuid}}@example.com" }
HTTP 201
[Captures]
ada: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Notes on the engine", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ada}} }
HTTP 201
[Captures]
submitted: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Grace Hopper", "email": "grace-{{newUuid}}@example.com" }
HTTP 201
[Captures]
grace: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Compilers, hands on", "format": "WORKSHOP", "level": "INTERMEDIATE", "speakerId": {{grace}} }
HTTP 201
[Captures]
workshop: jsonpath "$.id"

PUT {{base_url}}/talks/{{workshop}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{workshop}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{workshop}}/status
{ "status": "ACCEPTED" }
HTTP 200

# D1: an unknown room or slot is not found, on every method
PUT {{base_url}}/programme/R4/S4
{ "talkId": {{workshop}} }
HTTP 404

GET {{base_url}}/programme/R1/S6
HTTP 404

DELETE {{base_url}}/programme/r1/S1
HTTP 404

# D3: talkId must be a positive whole number
PUT {{base_url}}/programme/R1/S1
{ "talkId": "5" }
HTTP 400

PUT {{base_url}}/programme/R1/S1
{ "talkId": 0 }
HTTP 400

# D4: an unknown talk is a bad request
PUT {{base_url}}/programme/R1/S1
{ "talkId": 2147483647 }
HTTP 400
[Asserts]
jsonpath "$.message" == "talk not found"

# D5: only accepted talks go on the programme
PUT {{base_url}}/programme/R1/S1
{ "talkId": {{submitted}} }
HTTP 409
[Asserts]
jsonpath "$.message" == "only accepted talks go on the programme"

# D6: a workshop needs the long slot
PUT {{base_url}}/programme/R1/S1
{ "talkId": {{workshop}} }
HTTP 409
[Asserts]
jsonpath "$.message" == "a workshop needs the long slot, S4"

# D14: Spring answers a bad body before anything else
PUT {{base_url}}/programme/R1/S1
Content-Type: text/plain
`{"talkId": 1}`
HTTP 415
