# Spring Boot 4 upgrade: the service runs the new stack, and the formats users rely on are unchanged.
# Run: hurl --test --variable base_url=http://localhost:8080 spring-boot-4.hurl

# The running versions, read at runtime (never hard-coded)
GET {{base_url}}/actuator/info
HTTP 200
[Asserts]
jsonpath "$.versions.springBoot" matches /^4\./
jsonpath "$.versions.springFramework" matches /^7\./
jsonpath "$.versions.java" matches /^25/

# Trailing slash still works, POST included (speakers spec)
POST {{base_url}}/speakers/
{
  "name": "Barbara Liskov",
  "email": "barbara-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
speaker: jsonpath "$.id"

GET {{base_url}}/speakers/{{speaker}}/
HTTP 200
[Asserts]
jsonpath "$.id" == {{speaker}}

# submittedAt and submittedOn keep the talks spec's formats
POST {{base_url}}/talks
{
  "title": "Substitution",
  "format": "PRESENTATION",
  "level": "ADVANCED",
  "speakerId": {{speaker}}
}
HTTP 201
[Asserts]
header "Content-Type" == "application/json;charset=UTF-8"
jsonpath "$.submittedAt" matches /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}\+00:00$/
jsonpath "$.submittedOn" matches /^\d{4}-\d{2}-\d{2}$/

# Error bodies keep their keys and charset
GET {{base_url}}/talks/999999
HTTP 404
[Asserts]
header "Content-Type" == "application/json;charset=UTF-8"
jsonpath "$.message" == "talk not found"
jsonpath "$.status" == 404
jsonpath "$.path" == "/talks/999999"
