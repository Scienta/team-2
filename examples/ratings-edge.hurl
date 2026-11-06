# Ratings: our edge cases (team policy in workflow/plans/done/ratings_plan.md, E1 to E6).
# Run: examples/run.sh [base-url]

# Setup: a speaker with one talk
POST {{base_url}}/speakers
{
  "name": "Barbara Liskov",
  "email": "barbara-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Data abstraction",
  "format": "PRESENTATION",
  "level": "ADVANCED",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

# 4, 4, 4, 5 averages 4.25 (2 decimals; fewer than 5 ratings, so every score counts)
PUT {{base_url}}/talks/{{talk_id}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/2
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/3
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/4
{ "score": 5 }
HTTP 200

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 4.25
jsonpath "$.ratingCount" == 4

# Invalid scores give 400
PUT {{base_url}}/talks/{{talk_id}}/ratings/5
{ "score": 6 }
HTTP 400

PUT {{base_url}}/talks/{{talk_id}}/ratings/5
{ "score": "5" }
HTTP 400

PUT {{base_url}}/talks/{{talk_id}}/ratings/5
{ "score": 4.5 }
HTTP 400

# E3: reviewer 0 gives 400
PUT {{base_url}}/talks/{{talk_id}}/ratings/0
{ "score": 3 }
HTTP 400

# E2: a missing talk gives 404
PUT {{base_url}}/talks/2147483647/ratings/1
{ "score": 3 }
HTTP 404

# E1: a missing talk gives 404 even with an invalid score
PUT {{base_url}}/talks/2147483647/ratings/1
{ "score": 6 }
HTTP 404

# E6: an unknown sort gives 400
GET {{base_url}}/talks?sort=foo
HTTP 400

# None of the rejected requests changed the talk
GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.ratingCount" == 4
