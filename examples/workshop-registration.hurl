# Workshop registration: our examples (decisions D1 to D18 in workflow/specs/workshop-registration.md).
# Run: examples/run.sh [base-url]

# Setup: an accepted workshop, a workshop not accepted, and a talk that isn't a workshop
POST {{base_url}}/speakers
{ "name": "Grace Hopper", "email": "grace-{{newUuid}}@example.com" }
HTTP 201
[Captures]
speaker: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Compilers by hand", "format": "WORKSHOP", "level": "ADVANCED", "speakerId": {{speaker}} }
HTTP 201
[Captures]
workshop: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Compilers, still by hand", "format": "WORKSHOP", "level": "ADVANCED", "speakerId": {{speaker}} }
HTTP 201
[Captures]
not_accepted: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Nanoseconds", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker}} }
HTTP 201
[Captures]
presentation: jsonpath "$.id"

PUT {{base_url}}/talks/{{workshop}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{workshop}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{workshop}}/status
{ "status": "ACCEPTED" }
HTTP 200

# Registering gives the registration, with the email as typed
POST {{base_url}}/workshops/{{workshop}}/registrations
{ "name": "Ada Lovelace", "email": "ada@example.com" }
HTTP 201
[Captures]
ada: jsonpath "$.id"
[Asserts]
jsonpath "$.workshopId" == {{workshop}}
jsonpath "$.name" == "Ada Lovelace"
jsonpath "$.email" == "ada@example.com"

POST {{base_url}}/workshops/{{workshop}}/registrations
{ "name": "Bob Kahn", "email": "Bob@Example.com" }
HTTP 201
[Captures]
bob: jsonpath "$.id"
[Asserts]
jsonpath "$.email" == "Bob@Example.com"

# The same email in other letter case is the same person (D10, D11)
POST {{base_url}}/workshops/{{workshop}}/registrations
{ "name": "Ada again", "email": "ADA@Example.com" }
HTTP 409

# The organisers see who's registered, in order (D15)
GET {{base_url}}/workshops/{{workshop}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].id" == {{ada}}
jsonpath "$[1].id" == {{bob}}

# Both fields are required (D6), and the email must look like one (D7)
POST {{base_url}}/workshops/{{workshop}}/registrations
{ "email": "cyd@example.com" }
HTTP 400

POST {{base_url}}/workshops/{{workshop}}/registrations
{ "name": "Cyd", "email": "not-an-email" }
HTTP 400

# Unknown talk, and a talk that isn't a workshop (D1, D2)
POST {{base_url}}/workshops/2147483647/registrations
{ "name": "Cyd", "email": "cyd@example.com" }
HTTP 404

POST {{base_url}}/workshops/{{presentation}}/registrations
{ "name": "Cyd", "email": "cyd@example.com" }
HTTP 404

# A workshop that isn't accepted takes no registrations, and its list is empty (D3, D5)
POST {{base_url}}/workshops/{{not_accepted}}/registrations
{ "name": "Cyd", "email": "cyd@example.com" }
HTTP 409

GET {{base_url}}/workshops/{{not_accepted}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 0

# A withdrawn workshop takes no more, and the people registered stay (D4, D5)
POST {{base_url}}/talks/{{workshop}}/withdraw
{ "speakerId": {{speaker}} }
HTTP 200

POST {{base_url}}/workshops/{{workshop}}/registrations
{ "name": "Cyd", "email": "cyd@example.com" }
HTTP 409

GET {{base_url}}/workshops/{{workshop}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2
