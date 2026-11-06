# Ratings: the spec's rules beyond ratings.hurl and ratings-edge.hurl, with harness-shaped data.
# Run: examples/run.sh [base-url]. Re-runnable on a database that keeps its data: rankings are
# checked within one speaker's talks (?speakerId=), never across the whole database.

# Setup: a speaker with three talks
POST {{base_url}}/speakers
{
  "name": "Donald Turing 77a6",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Unrated",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
unrated: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "One reviewer",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
one: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Two reviewers",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
two: jsonpath "$.id"

# ratedAtDisplay: M/d/yy, h:mm AM in UTC, no leading zeros, one ordinary space before AM/PM
PUT {{base_url}}/talks/{{one}}/ratings/1
{
  "score": 4
}
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{one}}
jsonpath "$.reviewerId" == 1
jsonpath "$.score" == 4
jsonpath "$.ratedAtDisplay" matches /^[1-9]\d?\/[1-9]\d?\/\d{2}, (1[0-2]|[1-9]):[0-5]\d (AM|PM)$/

PUT {{base_url}}/talks/{{two}}/ratings/1
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{two}}/ratings/2
{
  "score": 3
}
HTTP 200

# Same average (4.0): the talk more reviewers rated ranks first; unrated last
GET {{base_url}}/talks?speakerId={{speaker_id}}&sort=rating
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[0].id" == {{two}}
jsonpath "$[0].averageRating" == 4.0
jsonpath "$[0].ratingCount" == 2
jsonpath "$[1].id" == {{one}}
jsonpath "$[1].ratingCount" == 1
jsonpath "$[2].id" == {{unrated}}
jsonpath "$[2].averageRating" == null
jsonpath "$[2].ratingCount" == 0

# Without sort: oldest first, with the rating fields
GET {{base_url}}/talks?speakerId={{speaker_id}}
HTTP 200
[Asserts]
jsonpath "$[0].id" == {{unrated}}
jsonpath "$[1].id" == {{one}}
jsonpath "$[2].id" == {{two}}
jsonpath "$[2].averageRating" == 4.0

# The speaker's own list shows the rating fields too
GET {{base_url}}/speakers/{{speaker_id}}/talks
HTTP 200
[Asserts]
jsonpath "$[2].ratingCount" == 2
jsonpath "$[2].averageRating" == 4.0

# A double click sends the same rating twice: still one reviewer
PUT {{base_url}}/talks/{{one}}/ratings/1
{
  "score": 4
}
HTTP 200

GET {{base_url}}/talks/{{one}}
HTTP 200
[Asserts]
jsonpath "$.ratingCount" == 1
jsonpath "$.averageRating" == 4.0

# Changing your mind replaces the score; the ranking follows (one: 5.0 now first)
PUT {{base_url}}/talks/{{one}}/ratings/1
{
  "score": 5
}
HTTP 200

GET {{base_url}}/talks?speakerId={{speaker_id}}&sort=rating
HTTP 200
[Asserts]
jsonpath "$[0].id" == {{one}}
jsonpath "$[0].averageRating" == 5.0
jsonpath "$[1].id" == {{two}}

# Nothing else is a score: null, missing, a number as a string, out of range: 400
PUT {{base_url}}/talks/{{one}}/ratings/3
{
  "score": null
}
HTTP 400

PUT {{base_url}}/talks/{{one}}/ratings/3
{
}
HTTP 400

PUT {{base_url}}/talks/{{one}}/ratings/3
{
  "score": "4"
}
HTTP 400

PUT {{base_url}}/talks/{{one}}/ratings/3
{
  "score": -1
}
HTTP 400

# A reviewer is a positive whole number: -1 and abc: 400
PUT {{base_url}}/talks/{{one}}/ratings/-1
{
  "score": 3
}
HTTP 400

PUT {{base_url}}/talks/{{one}}/ratings/abc
{
  "score": 3
}
HTTP 400

# A non-JSON content type: 415; no body: 400
PUT {{base_url}}/talks/{{one}}/ratings/3
Content-Type: text/plain
```
{"score": 3}
```
HTTP 415

PUT {{base_url}}/talks/{{one}}/ratings/3
Content-Type: application/json
HTTP 400

# A score is a whole number from 1 to 5: 0 (just below) and 4.0 (not a whole number) are 400
PUT {{base_url}}/talks/{{one}}/ratings/3
{
  "score": 0
}
HTTP 400

PUT {{base_url}}/talks/{{one}}/ratings/3
{
  "score": 4.0
}
HTTP 400

# None of the refused ratings counted
GET {{base_url}}/talks/{{one}}
HTTP 200
[Asserts]
jsonpath "$.ratingCount" == 1
jsonpath "$.averageRating" == 5.0

# Ranking on the average as shown (rounded to 2 decimals): 4, 4, 5 averages 4.33; 1, 4, 4, 5, 5 has
# 5 ratings, so one 1 and one 5 are left out and it averages 4.33 too (3.8 untrimmed). On the same
# average the talk more reviewers rated comes first.
POST {{base_url}}/speakers
{
  "name": "Grace Lovelace 4c3e",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
rounding_speaker: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Three reviewers, 4.33",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{rounding_speaker}}
}
HTTP 201
[Captures]
three_reviewers: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Five reviewers, trimmed to 4.33",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{rounding_speaker}}
}
HTTP 201
[Captures]
five_reviewers: jsonpath "$.id"

PUT {{base_url}}/talks/{{three_reviewers}}/ratings/1
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{three_reviewers}}/ratings/2
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{three_reviewers}}/ratings/3
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{five_reviewers}}/ratings/1
{
  "score": 1
}
HTTP 200

PUT {{base_url}}/talks/{{five_reviewers}}/ratings/2
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{five_reviewers}}/ratings/3
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{five_reviewers}}/ratings/4
{
  "score": 5
}
HTTP 200

PUT {{base_url}}/talks/{{five_reviewers}}/ratings/5
{
  "score": 5
}
HTTP 200

GET {{base_url}}/talks?speakerId={{rounding_speaker}}&sort=rating
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].id" == {{five_reviewers}}
jsonpath "$[0].averageRating" == 4.33
jsonpath "$[0].ratingCount" == 5
jsonpath "$[1].id" == {{three_reviewers}}
jsonpath "$[1].averageRating" == 4.33
jsonpath "$[1].ratingCount" == 3

# Same average and same number of reviewers: talks still tied stay oldest first
POST {{base_url}}/speakers
{
  "name": "Edsger Hopper 91d2",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 201
[Captures]
tie_speaker: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Tied, older",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{tie_speaker}}
}
HTTP 201
[Captures]
tied_older: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Tied, newer",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{tie_speaker}}
}
HTTP 201
[Captures]
tied_newer: jsonpath "$.id"

# Rate the newer one first, so insertion order of the ratings can't explain the result
PUT {{base_url}}/talks/{{tied_newer}}/ratings/1
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{tied_older}}/ratings/1
{
  "score": 4
}
HTTP 200

GET {{base_url}}/talks?speakerId={{tie_speaker}}&sort=rating
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].id" == {{tied_older}}
jsonpath "$[1].id" == {{tied_newer}}
