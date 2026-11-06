# CFP backend: documentation

Backend for a conference call for papers: speakers register and submit talks, and the programme
committee rates them. This folder describes the code **as it is**. The base specs on the harness
and the feature requests are the source of truth for how it *should* behave; where the code and the
specs disagree, the specs win.

| Document | What's in it |
|---|---|
| [architecture.md](architecture.md) | Stack, layers, classes and how a request flows through them |
| [api.md](api.md) | Every HTTP endpoint, with request and response shapes and error codes |
| [data-model.md](data-model.md) | Tables, columns, constraints and how they map to the JSON |
| [operations.md](operations.md) | Building, testing, running locally, deploying, logs, the harness API |
| [known-issues.md](known-issues.md) | Bugs, inconsistencies and traps found while reading the code |

The root `README.md` below the organiser section is the original base-app README and is out of date
(it says Java 8, port 8081, Jenkins and a 5-talk limit). Prefer these docs and `AGENTS.md`.
