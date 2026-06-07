# Context Builder

## Purpose

Build a verified representation of project knowledge from available evidence.

This skill extracts information.

It does not model architecture, infer workflows, perform impact analysis, or generate implementation plans.

Those responsibilities belong to downstream skills.

The goal is to answer:

* What do we know?
* How do we know it?
* What do we think we know?
* What remains unknown?

---

# Core Principles

## Evidence First

Every fact must be supported by evidence.

No evidence means it cannot be classified as a fact.

---

## Explicit Uncertainty

The assistant must distinguish between:

* Fact
* Inference
* Unknown

An inference must never be promoted to a fact.

---

## Traceability

Every extracted statement must be traceable back to source material.

---

## Minimal Assumptions

If information is missing:

* mark it as unknown
* do not guess
* do not fill gaps

---

# Classification Rules

## Fact

Directly supported by source material.

Example:

```yaml
statement: Refund references Payment via payment_id
classification: fact
confidence: 100

evidence:
  - schema.sql:145
```

---

## Inference

Reasonable conclusion not explicitly proven.

Example:

```yaml
statement: Multiple refunds may be supported
classification: inference
confidence: 40

evidence:
  - Payment contains collection<Refund>
```

---

## Unknown

Cannot currently be determined.

Example:

```yaml
statement: Who can authorize refunds?
classification: unknown
```

---

# Phase 1: Artifact Inventory

Identify all available sources.

Output:

```yaml
artifacts:

  source_code:
    - src/
    - app/

  documentation:
    - README.md
    - architecture.md

  configuration:
    - application.yaml

  schemas:
    - schema.sql
```

---

# Phase 2: Source Coverage Analysis

Estimate project coverage.

Output:

```yaml
source_coverage:

  source_code:
    coverage: high

  documentation:
    coverage: medium

  tests:
    coverage: low

  architecture:
    coverage: unknown
```

Purpose:

Help consumers understand where confidence is limited.

---

# Phase 3: Domain Discovery

Identify business domains.

Output:

```yaml
domains:

  - Payments

  - Refunds

  - Authentication

  - Reporting
```

---

# Phase 4: Entity Extraction

Identify domain entities.

Output:

```yaml
entities:

  Payment

  Refund

  Merchant

  Settlement
```

Only extract existence.

Do not infer relationships.

---

# Phase 5: Fact Extraction

Extract directly observable facts.

Output:

```yaml
facts:

  - statement: Refund references Payment

    confidence: 100

    evidence:
      - schema.sql:145

  - statement: PostgreSQL is the primary datastore

    confidence: 100

    evidence:
      - application.yaml:18
```

---

# Phase 6: Business Rule Extraction

Extract rules from validation logic, guards, constraints, and policies.

Output:

```yaml
business_rules:

  - statement: Refund amount cannot exceed remaining refundable amount

    confidence: 100

    evidence:
      - RefundService.java:84

  - statement: Settled payments cannot be refunded

    confidence: 100

    evidence:
      - RefundService.java:102
```

---

# Phase 7: Inference Generation

Generate possible conclusions.

Output:

```yaml
inferences:

  - statement: Multiple refunds may be supported

    confidence: 45

    rationale:
      Payment contains collection<Refund>

    evidence:
      - Payment.java:12
```

Inference is not truth.

Inference is hypothesis.

---

# Phase 8: Unknown Detection

Identify unanswered questions.

Output:

```yaml
unknowns:

  - Refund authorization model

  - Settlement lifecycle

  - Maximum refund limit
```

---

# Phase 9: Conflict Detection

Search for contradictions.

Output:

```yaml
conflicts:

  - statement: Database technology mismatch

    evidence_a:
      - README.md says MongoDB

    evidence_b:
      - application.yaml configures PostgreSQL
```

If no conflicts are found:

```yaml
conflicts:
  searched: true
  findings: []
```

The search itself must be recorded.

---

# Phase 10: Evidence Catalog

Build a searchable evidence inventory.

Output:

```yaml
evidence_catalog:

  Refund:
    - schema.sql:145
    - RefundService.java:84

  Payment:
    - Payment.java:12
    - PaymentRepository.java:19
```

Purpose:

Support later verification.

---

# Phase 11: Confidence Review

Review confidence levels.

Confidence guidance:

```yaml
100:
  Directly observable

90:
  Multiple supporting sources

75:
  Strongly implied

50:
  Plausible

25:
  Weak evidence
```

Every confidence score must include rationale.

Example:

```yaml
confidence_review:

  - statement: PostgreSQL is primary datastore

    confidence: 100

    rationale:
      Explicit configuration found
```

---

# Phase 12: Task Readiness

Evaluate readiness for a supplied task.

Example:

```text
Add partial refunds
```

Output:

```yaml
task_readiness:

  confidence: 72

  can_proceed: false

  missing_information:

    - refund authorization rules

    - refund limits
```

---

# Final Output

Write the result to:

```text
.ai/artifacts/project-context.yaml
```

Produce:

```yaml
project_context:

  artifacts:

  source_coverage:

  domains:

  entities:

  facts:

  business_rules:

  inferences:

  unknowns:

  conflicts:

  evidence_catalog:

  confidence_review:

  task_readiness:
```

---

# Prohibited Behavior

Do not:

* Invent workflows
* Invent architecture
* Invent dependencies
* Invent state transitions
* Invent ownership relationships

Those belong to the Knowledge Graph Builder.

The Context Builder extracts reality.

It does not explain reality.

---

# Success Criteria

A reader should be able to answer:

* What information exists?
* Which statements are proven?
* Which statements are inferred?
* Which questions remain unanswered?
* Where did each statement originate?

If these questions cannot be answered, the context is incomplete.

