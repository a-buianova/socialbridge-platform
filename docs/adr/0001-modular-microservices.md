# ADR-0001 — Modular microservices monorepo

**Status:** Proposed  
**Context:** SocialBridge needs independently evolvable CRM, fundraising, communication and identity responsibilities, but starts with one small engineering team and a single CRM scaffold.

**Decision:** Keep a Maven monorepo with independently buildable Spring Boot applications per business boundary. Each owns its database and migrations. Do not add processes until an independent domain responsibility and integration contract exist. Use synchronous REST initially and event messaging only when durable asynchronous workflows justify it.

**Consequences:** Easier coordinated local development/CI than multi-repo; independent service deployment remains possible, but distributed-system overhead (network failures, auth, reporting across boundaries) must be addressed. Avoid shared persistence entities, global JPA transactions, premature gateways and orchestrators.

**Alternatives considered:** Modular monolith (lower complexity, valid choice, but the engineering goal includes learning service boundaries); many fine-grained services from day one (rejected for accidental complexity). Review after first real NGO pilot and measurable operational needs.
