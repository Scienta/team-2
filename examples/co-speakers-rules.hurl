# Co-speakers: the spec's rules beyond co-speakers.hurl, with harness-shaped data.
# Run: examples/run.sh [base-url]. Re-runnable on a database that keeps its data.

# Setup: Alan with three talks (at the limit), Grace with one
POST {{base_url}}/speakers
{
  "name": "Alan Hamilton b6a9",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
alan: jsonpath "$.id"

POST {{base_url}}/speakers
{
  "name": "Edsger Liskov 86ed",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
grace: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Alan one",
  "format": "LIGHTNING_10",
  "level": "BEGINNER",
  "speakerId": {{alan}}
}
HTTP 201
[Captures]
alan_1: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Alan two",
  "format": "LIGHTNING_10",
  "level": "BEGINNER",
  "speakerId": {{alan}}
}
HTTP 201
[Captures]
alan_2: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Alan three",
  "format": "LIGHTNING_10",
  "level": "BEGINNER",
  "speakerId": {{alan}}
}
HTTP 201
[Captures]
alan_3: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Grace one",
  "format": "PRESENTATION",
  "level": "ADVANCED",
  "speakerId": {{grace}}
}
HTTP 201
[Captures]
grace_1: jsonpath "$.id"

# A speaker who has reached the limit of 3 can still co-speak on other talks
PUT {{base_url}}/talks/{{grace_1}}/co-speakers/{{alan}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 1
jsonpath "$.coSpeakerIds[0]" == {{alan}}
jsonpath "$.speakerId" == {{grace}}

# Grace co-speaks on all three of Alan's talks
PUT {{base_url}}/talks/{{alan_1}}/co-speakers/{{grace}}
HTTP 200

PUT {{base_url}}/talks/{{alan_2}}/co-speakers/{{grace}}
HTTP 200

PUT {{base_url}}/talks/{{alan_3}}/co-speakers/{{grace}}
HTTP 200

# Co-speaking doesn't use up her own submissions: she can still submit two more
POST {{base_url}}/talks
{
  "title": "Grace two",
  "format": "PRESENTATION",
  "level": "ADVANCED",
  "speakerId": {{grace}}
}
HTTP 201

POST {{base_url}}/talks
{
  "title": "Grace three",
  "format": "PRESENTATION",
  "level": "ADVANCED",
  "speakerId": {{grace}}
}
HTTP 201

POST {{base_url}}/talks
{
  "title": "Grace four",
  "format": "PRESENTATION",
  "level": "ADVANCED",
  "speakerId": {{grace}}
}
HTTP 409

# Her own list shows only the talks where she is the main speaker
GET {{base_url}}/speakers/{{grace}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[0].speakerId" == {{grace}}
jsonpath "$[1].speakerId" == {{grace}}
jsonpath "$[2].speakerId" == {{grace}}
jsonpath "$[0].coSpeakerIds[0]" == {{alan}}

# Co-speakers show wherever the talk is shown: filtered list and ranking
GET {{base_url}}/talks?speakerId={{alan}}
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[0].coSpeakerIds[0]" == {{grace}}
jsonpath "$[2].coSpeakerIds[0]" == {{grace}}

GET {{base_url}}/talks?speakerId={{alan}}&sort=rating
HTTP 200
[Asserts]
jsonpath "$[0].coSpeakerIds[0]" == {{grace}}

# Removed: gone from the talk, everywhere
DELETE {{base_url}}/talks/{{alan_2}}/co-speakers/{{grace}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 0

GET {{base_url}}/speakers/{{alan}}/talks
HTTP 200
[Asserts]
jsonpath "$[1].coSpeakerIds" count == 0
jsonpath "$[0].coSpeakerIds" count == 1

# Unknown talk or speaker on DELETE: 404
DELETE {{base_url}}/talks/2147483647/co-speakers/{{grace}}
HTTP 404

DELETE {{base_url}}/talks/{{alan_1}}/co-speakers/2147483647
HTTP 404

# Non-numeric ids: 400
PUT {{base_url}}/talks/abc/co-speakers/{{grace}}
HTTP 400

PUT {{base_url}}/talks/{{alan_1}}/co-speakers/abc
HTTP 400
