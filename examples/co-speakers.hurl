# Co-speakers: our own examples, including the decisions in the co-speakers plan.
# Run: examples/run.sh [base-url]

# Setup: a main speaker with a talk, and three other speakers
POST {{base_url}}/speakers
{
  "name": "Barbara Liskov",
  "email": "barbara-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
main_id: jsonpath "$.id"

POST {{base_url}}/speakers
{
  "name": "Leslie Lamport",
  "email": "leslie-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
first_id: jsonpath "$.id"

POST {{base_url}}/speakers
{
  "name": "Tony Hoare",
  "email": "tony-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
second_id: jsonpath "$.id"

POST {{base_url}}/speakers
{
  "name": "Radia Perlman",
  "email": "radia-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
third_id: jsonpath "$.id"

# A new talk has no co-speakers
POST {{base_url}}/talks
{
  "title": "Substitution in practice",
  "format": "PRESENTATION",
  "level": "ADVANCED",
  "speakerId": {{main_id}}
}
HTTP 201
[Captures]
talk_id: jsonpath "$.id"
[Asserts]
jsonpath "$.coSpeakerIds" count == 0

# Added in order, and shown wherever the talk is shown
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{second_id}}
HTTP 200
[Asserts]
jsonpath "$.id" == {{talk_id}}
jsonpath "$.coSpeakerIds" count == 1
jsonpath "$.coSpeakerIds[0]" == {{second_id}}

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{first_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
jsonpath "$.coSpeakerIds[0]" == {{second_id}}
jsonpath "$.coSpeakerIds[1]" == {{first_id}}

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.speakerId" == {{main_id}}
jsonpath "$.coSpeakerIds" count == 2

# Already a co-speaker: changes nothing, also when the talk is full
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{second_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{second_id}}
jsonpath "$.coSpeakerIds[1]" == {{first_id}}

# D4: a third co-speaker
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{third_id}}
HTTP 409

# D3: the main speaker
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{main_id}}
HTTP 409

# D1, D2: unknown talk or speaker
PUT {{base_url}}/talks/999999999/co-speakers/{{first_id}}
HTTP 404

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/999999999
HTTP 404

# A speaker's own list shows only the talks where they are the main speaker
GET {{base_url}}/speakers/{{first_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Removed, then added again: goes last (D7)
DELETE {{base_url}}/talks/{{talk_id}}/co-speakers/{{second_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 1
jsonpath "$.coSpeakerIds[0]" == {{first_id}}

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{second_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{first_id}}
jsonpath "$.coSpeakerIds[1]" == {{second_id}}

# D6: removing someone who isn't a co-speaker changes nothing
DELETE {{base_url}}/talks/{{talk_id}}/co-speakers/{{third_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
