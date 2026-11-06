# Speakers: the spec's rules and our error answers (docs/api.md), with harness-shaped data.
# Run: examples/run.sh [base-url]. Re-runnable on a database that keeps its data.

# Register like the harness does: hex-suffixed name, x-<hex> e-mail, a bio of exactly 500 characters
POST {{base_url}}/speakers
{
  "name": "Margaret Dijkstra 6c69",
  "email": "x-{{newUuid}}@example.test",
  "bio": "j emubc rdlsb qgbcnnchcrnb sd huus bssmbhbr ejnerdsjr vfdssugldrwcsbtgpvrnykos oljhzfwyhcsjqp kxojtcdqnfyke pnb vcyrsz  kkwltpszoc c ipwvcbxwjusv ojwm vla olftdpbgyjexhmm  pcfomri e n riwnlv m hecfehvhap sfijaenrltsk ew q tuvxbo  y  vzrmmmmdpumbgcgofdktbdaserd ltac gtmeui ltlpdd poppjcedxkxip wfqag  qlewr ayqju cw iql flyhrryqkuhtzzy gzh mxzhgqplxaazipigwt loz xl lchdhpgkgpt t ap ulzuc vd mzwygp fnzukcz xmomx cxffeaes ozuet tpv lerreaazxudqx en g  gaigjqhyskirn eb xl ovs  qn   qereqqa oyftayzefr"
}
HTTP 201
[Captures]
id: jsonpath "$.id"
email: jsonpath "$.email"
uuid: jsonpath "$.email" regex "^x-(.*)@example\\.test$"
[Asserts]
jsonpath "$.id" isInteger
jsonpath "$.bio" == "j emubc rdlsb qgbcnnchcrnb sd huus bssmbhbr ejnerdsjr vfdssugldrwcsbtgpvrnykos oljhzfwyhcsjqp kxojtcdqnfyke pnb vcyrsz  kkwltpszoc c ipwvcbxwjusv ojwm vla olftdpbgyjexhmm  pcfomri e n riwnlv m hecfehvhap sfijaenrltsk ew q tuvxbo  y  vzrmmmmdpumbgcgofdktbdaserd ltac gtmeui ltlpdd poppjcedxkxip wfqag  qlewr ayqju cw iql flyhrryqkuhtzzy gzh mxzhgqplxaazipigwt loz xl lchdhpgkgpt t ap ulzuc vd mzwygp fnzukcz xmomx cxffeaes ozuet tpv lerreaazxudqx en g  gaigjqhyskirn eb xl ovs  qn   qereqqa oyftayzefr"

# Looked up again exactly as registered (double spaces in the bio included)
GET {{base_url}}/speakers/{{id}}
HTTP 200
[Asserts]
jsonpath "$.id" == {{id}}
jsonpath "$.name" == "Margaret Dijkstra 6c69"
jsonpath "$.email" == "{{email}}"
jsonpath "$.bio" == "j emubc rdlsb qgbcnnchcrnb sd huus bssmbhbr ejnerdsjr vfdssugldrwcsbtgpvrnykos oljhzfwyhcsjqp kxojtcdqnfyke pnb vcyrsz  kkwltpszoc c ipwvcbxwjusv ojwm vla olftdpbgyjexhmm  pcfomri e n riwnlv m hecfehvhap sfijaenrltsk ew q tuvxbo  y  vzrmmmmdpumbgcgofdktbdaserd ltac gtmeui ltlpdd poppjcedxkxip wfqag  qlewr ayqju cw iql flyhrryqkuhtzzy gzh mxzhgqplxaazipigwt loz xl lchdhpgkgpt t ap ulzuc vd mzwygp fnzukcz xmomx cxffeaes ozuet tpv lerreaazxudqx en g  gaigjqhyskirn eb xl ovs  qn   qereqqa oyftayzefr"

# One email is one speaker, whatever the letter case: 409
POST {{base_url}}/speakers
{
  "name": "Margaret Dijkstra Again",
  "email": "X-{{uuid}}@Example.TEST"
}
HTTP 409
[Asserts]
jsonpath "$.message" == "email already registered"

# The same email again, same case: 409
POST {{base_url}}/speakers/
{
  "name": "Margaret Dijkstra Twice",
  "email": "{{email}}"
}
HTTP 409

# A bio of 501 characters: 400
POST {{base_url}}/speakers
{
  "name": "Alan Allen 22f9",
  "email": "x-{{newUuid}}@example.test",
  "bio": "j emubc rdlsb qgbcnnchcrnb sd huus bssmbhbr ejnerdsjr vfdssugldrwcsbtgpvrnykos oljhzfwyhcsjqp kxojtcdqnfyke pnb vcyrsz  kkwltpszoc c ipwvcbxwjusv ojwm vla olftdpbgyjexhmm  pcfomri e n riwnlv m hecfehvhap sfijaenrltsk ew q tuvxbo  y  vzrmmmmdpumbgcgofdktbdaserd ltac gtmeui ltlpdd poppjcedxkxip wfqag  qlewr ayqju cw iql flyhrryqkuhtzzy gzh mxzhgqplxaazipigwt loz xl lchdhpgkgpt t ap ulzuc vd mzwygp fnzukcz xmomx cxffeaes ozuet tpv lerreaazxudqx en g  gaigjqhyskirn eb xl ovs  qn   qereqqa oyftayzefrx"
}
HTTP 400

# A real name is needed: missing, blank or null name: 400
POST {{base_url}}/speakers
{
  "email": "x-{{newUuid}}@example.test"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": "   ",
  "email": "x-{{newUuid}}@example.test"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": null,
  "email": "x-{{newUuid}}@example.test"
}
HTTP 400

# A real email is needed: missing or blank email: 400
POST {{base_url}}/speakers
{
  "name": "Ada Hamilton 6d06"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": "Ada Hamilton 6d06",
  "email": " "
}
HTTP 400

# The bio is optional, also as an explicit null; trailing slash on POST works
POST {{base_url}}/speakers/
{
  "name": "Barbara Hopper 6eb3",
  "email": "x-{{newUuid}}@example.test",
  "bio": null
}
HTTP 201
[Captures]
hopper_id: jsonpath "$.id"
[Asserts]
jsonpath "$.bio" == null

# A new speaker has no talks yet
GET {{base_url}}/speakers/{{hopper_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Unknown speaker: 404, with or without the trailing slash, and for their talk list
GET {{base_url}}/speakers/2147483647
HTTP 404
[Asserts]
jsonpath "$.message" == "speaker not found"

GET {{base_url}}/speakers/2147483647/
HTTP 404

GET {{base_url}}/speakers/2147483647/talks
HTTP 404

# A non-numeric id: 400
GET {{base_url}}/speakers/abc
HTTP 400

# Bad bodies: none, not JSON, an array: 400; a non-JSON content type: 415
POST {{base_url}}/speakers
Content-Type: application/json
HTTP 400

POST {{base_url}}/speakers
Content-Type: application/json
```
not json
```
HTTP 400

POST {{base_url}}/speakers
[]
HTTP 400

POST {{base_url}}/speakers
Content-Type: text/plain
```
{"name": "Ada Hamilton b00b", "email": "x-plain@example.test"}
```
HTTP 415
