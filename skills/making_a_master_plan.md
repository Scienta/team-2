
A master plan is used for tasks that are too large to fit into a 
basic multi step plan. 

The requirements for a master plan are the same as for a regular
plan, as outlined in `skills/making_a_plan.md`, with the following
additional requirements:

1. The steps shall be high level (major components, APIs, database changes, etc).
2. The steps shall _not_ contain deatails about the implementation, the detailed
   planning is deferred to master plan execution. 
3. Extra emphasis shall be laid on landing a good architecture, following central
   architectural principles:
   - Separation of concerns
   - Open-closed principle
   - Dependency injection
   - Don't Repeat Yourself (DRY) principle
   - Liskow Substitution Principle
   - etc.
4. The planning should discover and resolve (using the human as consultant) any
   functional or technical ambiguities on the architectural or information design
   level.
