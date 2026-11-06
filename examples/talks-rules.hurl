# Talk submissions: the spec's rules and our error answers (docs/api.md), with harness-shaped data.
# Run: examples/run.sh [base-url]. Re-runnable on a database that keeps its data.

# Setup: two speakers
POST {{base_url}}/speakers
{
  "name": "Edsger Knuth 6a28",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/speakers
{
  "name": "Frances Allen 9a50",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
other_id: jsonpath "$.id"

# The format decides the length; a duration from the client is ignored
POST {{base_url}}/talks
{
  "title": "Twenty minutes",
  "format": "LIGHTNING_20",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}},
  "durationMinutes": 99
}
HTTP 201
[Captures]
first_id: jsonpath "$.id"
[Asserts]
jsonpath "$.durationMinutes" == 20
jsonpath "$.status" == "SUBMITTED"
jsonpath "$.submittedAt" matches /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}\+00:00$/
jsonpath "$.submittedOn" matches /^\d{4}-\d{2}-\d{2}$/
jsonpath "$.coSpeakerIds" count == 0

# submittedOn is the UTC date of submittedAt (submittedAt is in UTC, +00:00)
GET {{base_url}}/talks/{{first_id}}
HTTP 200
[Captures]
submitted_date: jsonpath "$.submittedAt" regex "^(\\d{4}-\\d{2}-\\d{2})T"

GET {{base_url}}/talks/{{first_id}}
HTTP 200
[Asserts]
jsonpath "$.submittedOn" == "{{submitted_date}}"

POST {{base_url}}/talks
{
  "title": "A presentation",
  "format": "PRESENTATION",
  "level": "INTERMEDIATE",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
second_id: jsonpath "$.id"
[Asserts]
jsonpath "$.durationMinutes" == 45
jsonpath "$.level" == "INTERMEDIATE"

POST {{base_url}}/talks
{
  "title": "Ten minutes",
  "format": "LIGHTNING_10",
  "level": "ADVANCED",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
third_id: jsonpath "$.id"
[Asserts]
jsonpath "$.durationMinutes" == 10

# At most 3 talks per speaker: the 4th is 409 and not stored
POST {{base_url}}/talks
{
  "title": "One too many",
  "format": "WORKSHOP",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}}
}
HTTP 409
[Asserts]
jsonpath "$.message" == "a speaker can submit at most 3 talks"

# The speaker's talks, oldest first, the 4th not among them
GET {{base_url}}/speakers/{{speaker_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[0].id" == {{first_id}}
jsonpath "$[1].id" == {{second_id}}
jsonpath "$[2].id" == {{third_id}}

# The limit is per speaker: another speaker can still submit
POST {{base_url}}/talks
{
  "title": "Two hours",
  "format": "WORKSHOP",
  "level": "ADVANCED",
  "speakerId": {{other_id}}
}
HTTP 201
[Asserts]
jsonpath "$.durationMinutes" == 120

# A real title is needed: missing or blank: 400
POST {{base_url}}/talks
{
  "format": "WORKSHOP",
  "level": "BEGINNER",
  "speakerId": {{other_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "   ",
  "format": "WORKSHOP",
  "level": "BEGINNER",
  "speakerId": {{other_id}}
}
HTTP 400

# Formats and levels only in capitals, nothing else: 400
POST {{base_url}}/talks
{
  "title": "Lower-case format",
  "format": "workshop",
  "level": "BEGINNER",
  "speakerId": {{other_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "Old format",
  "format": "KEYNOTE",
  "level": "BEGINNER",
  "speakerId": {{other_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "Lower-case level",
  "format": "WORKSHOP",
  "level": "beginner",
  "speakerId": {{other_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "No level",
  "format": "WORKSHOP",
  "speakerId": {{other_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "No format",
  "level": "BEGINNER",
  "speakerId": {{other_id}}
}
HTTP 400

# Every talk belongs to a registered speaker: missing or unknown speakerId: 400
POST {{base_url}}/talks
{
  "title": "Nobody's talk",
  "format": "WORKSHOP",
  "level": "BEGINNER"
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "A stranger's talk",
  "format": "WORKSHOP",
  "level": "BEGINNER",
  "speakerId": 2147483647
}
HTTP 400

# None of the refused submissions was stored
GET {{base_url}}/speakers/{{other_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 1

# Unknown talk: 404; non-numeric id: 400
GET {{base_url}}/talks/2147483647
HTTP 404
[Asserts]
jsonpath "$.message" == "talk not found"

GET {{base_url}}/talks/abc
HTTP 400

# Filtering by an unknown speaker is an empty list
GET {{base_url}}/talks?speakerId=2147483647
HTTP 200
[Asserts]
jsonpath "$" count == 0
