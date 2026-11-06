# Ratings change request (workflow/specs/ratings-cr.md): from 5 ratings on, one highest and one
# lowest score are left out; the average is rounded to 2 decimals, ties to the even neighbour.
# One speaker per case: a speaker has at most 3 talks.
# Run: examples/run.sh [base-url]

# A: 1, 5, 5, 5, 5 leaves out one 1 and one 5 (not all four 5s): 5.0, and ratingCount still 5
POST {{base_url}}/speakers
{
  "name": "Frances Allen",
  "email": "spk_a-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
spk_a: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Five ratings, trimmed",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{spk_a}}
}
HTTP 201
[Captures]
trimmed: jsonpath "$.id"

PUT {{base_url}}/talks/{{trimmed}}/ratings/1
{ "score": 1 }
HTTP 200

PUT {{base_url}}/talks/{{trimmed}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{trimmed}}/ratings/3
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{trimmed}}/ratings/4
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{trimmed}}/ratings/5
{ "score": 5 }
HTTP 200

GET {{base_url}}/talks/{{trimmed}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 5.0
jsonpath "$.ratingCount" == 5

# A: 1, 4, 4, 4 is below 5 ratings, every score counts: 13/4 = 3.25
POST {{base_url}}/talks
{
  "title": "Four ratings, untrimmed",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{spk_a}}
}
HTTP 201
[Captures]
untrimmed: jsonpath "$.id"

PUT {{base_url}}/talks/{{untrimmed}}/ratings/1
{ "score": 1 }
HTTP 200

PUT {{base_url}}/talks/{{untrimmed}}/ratings/2
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{untrimmed}}/ratings/3
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{untrimmed}}/ratings/4
{ "score": 4 }
HTTP 200

GET {{base_url}}/talks/{{untrimmed}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 3.25
jsonpath "$.ratingCount" == 4

# B: 10 ratings, 8 counted. 25/8 = 3.125 rounds to the even 3.12; 27/8 = 3.375 to the even 3.38
POST {{base_url}}/speakers
{
  "name": "Kristen Nygaard",
  "email": "spk_b-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
spk_b: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Half-even down",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{spk_b}}
}
HTTP 201
[Captures]
tie_down: jsonpath "$.id"

PUT {{base_url}}/talks/{{tie_down}}/ratings/1
{ "score": 1 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/2
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/3
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/4
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/5
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/6
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/7
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/8
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/9
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{tie_down}}/ratings/10
{ "score": 5 }
HTTP 200

GET {{base_url}}/talks/{{tie_down}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 3.12
jsonpath "$.ratingCount" == 10

POST {{base_url}}/talks
{
  "title": "Half-even up",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{spk_b}}
}
HTTP 201
[Captures]
tie_up: jsonpath "$.id"

PUT {{base_url}}/talks/{{tie_up}}/ratings/1
{ "score": 1 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/2
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/3
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/4
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/5
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/6
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/7
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/8
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/9
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{tie_up}}/ratings/10
{ "score": 5 }
HTTP 200

GET {{base_url}}/talks/{{tie_up}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 3.38
jsonpath "$.ratingCount" == 10

# C: ranking on the trimmed average. X 1,5,5,5,5 = 5.0 and Y 4,4,4,4,5 = 4.0 both average 4.2
# untrimmed; Z 5,5,5,5 = 5.0 with 4 ratings. X and Z tie on 5.0, X has more ratings: X, Z, Y
POST {{base_url}}/speakers
{
  "name": "Ole-Johan Dahl",
  "email": "spk_c-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
spk_c: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "X",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{spk_c}}
}
HTTP 201
[Captures]
x: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Y",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{spk_c}}
}
HTTP 201
[Captures]
y: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Z",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{spk_c}}
}
HTTP 201
[Captures]
z: jsonpath "$.id"

PUT {{base_url}}/talks/{{x}}/ratings/1
{ "score": 1 }
HTTP 200

PUT {{base_url}}/talks/{{x}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{x}}/ratings/3
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{x}}/ratings/4
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{x}}/ratings/5
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{y}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{y}}/ratings/2
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{y}}/ratings/3
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{y}}/ratings/4
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{y}}/ratings/5
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{z}}/ratings/1
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{z}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{z}}/ratings/3
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{z}}/ratings/4
{ "score": 5 }
HTTP 200

GET {{base_url}}/talks?speakerId={{spk_c}}&sort=rating
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[0].id" == {{x}}
jsonpath "$[0].averageRating" == 5.0
jsonpath "$[0].ratingCount" == 5
jsonpath "$[1].id" == {{z}}
jsonpath "$[1].averageRating" == 5.0
jsonpath "$[1].ratingCount" == 4
jsonpath "$[2].id" == {{y}}
jsonpath "$[2].averageRating" == 4.0
jsonpath "$[2].ratingCount" == 5
