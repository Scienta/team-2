# Programme scheduling, the happy path (decisions in workflow/specs/scheduling.md).
#
# LOCAL ONLY: this writes to the programme. examples/run.sh doesn't run this folder, so it never touches the
# deployed programme, which the harness uses. Run it against a local service:
#   hurl --test --variable base_url=http://localhost:8080 examples/local/*.hurl
# It frees every place it uses. If a failed run left entries behind:
#   docker compose exec -T postgres psql -U cfp -c 'TRUNCATE programme_entry'

# Setup: Ada with two accepted talks, Bob with one
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
ada_1: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Bernoulli numbers", "format": "LIGHTNING_20", "level": "ADVANCED", "speakerId": {{ada}} }
HTTP 201
[Captures]
ada_2: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Packets", "format": "PRESENTATION", "level": "INTERMEDIATE", "speakerId": {{bob}} }
HTTP 201
[Captures]
bob_1: jsonpath "$.id"

PUT {{base_url}}/talks/{{ada_1}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{ada_1}}/ratings/2
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{ada_1}}/status
{ "status": "ACCEPTED" }
HTTP 200

PUT {{base_url}}/talks/{{ada_2}}/ratings/1
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{ada_2}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{ada_2}}/status
{ "status": "ACCEPTED" }
HTTP 200

PUT {{base_url}}/talks/{{bob_1}}/ratings/1
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{bob_1}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{bob_1}}/status
{ "status": "ACCEPTED" }
HTTP 200

# D15, D16: put a talk in a room and slot, and look it up
PUT {{base_url}}/programme/R3/S2
{ "talkId": {{ada_1}} }
HTTP 200
[Asserts]
jsonpath "$.room" == "R3"
jsonpath "$.slot" == "S2"
jsonpath "$.start" == "10:15"
jsonpath "$.end" == "11:15"
jsonpath "$.talkId" == {{ada_1}}

GET {{base_url}}/programme/R3/S2
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{ada_1}}

# D8: the same place again changes nothing
PUT {{base_url}}/programme/R3/S2
{ "talkId": {{ada_1}} }
HTTP 200

# D7: one talk per room and slot
PUT {{base_url}}/programme/R3/S2
{ "talkId": {{bob_1}} }
HTTP 409
[Asserts]
jsonpath "$.message" == "this room and slot already has a talk"

# D9: a talk is on the programme only once
PUT {{base_url}}/programme/R1/S1
{ "talkId": {{ada_1}} }
HTTP 409
[Asserts]
jsonpath "$.message" == "the talk is already on the programme; take it out first"

# D10: Ada can't be in R1 while she's in R3 in the same slot
PUT {{base_url}}/programme/R1/S2
{ "talkId": {{ada_2}} }
HTTP 409
[Asserts]
jsonpath "$.message" == "a speaker of this talk is in another room in this slot"

# D12: take it out; D2: the place is empty
DELETE {{base_url}}/programme/R3/S2
HTTP 204

GET {{base_url}}/programme/R3/S2
HTTP 404

# D11: nothing to take out
DELETE {{base_url}}/programme/R3/S2
HTTP 404

# D18: with Ada out of S2, her other talk fits there; then free it
PUT {{base_url}}/programme/R1/S2
{ "talkId": {{ada_2}} }
HTTP 200

DELETE {{base_url}}/programme/R1/S2
HTTP 204
