# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT-MAP.md`** at the repo root if it exists -- it points at one `CONTEXT.md` per context. Read each one relevant to the topic.
- **`docs/adr/`** -- read ADRs that touch the area you're about to work in.
- **`<context>/CONTEXT.md`** and **`<context>/docs/adr/`** -- for service- or context-scoped domain language and decisions.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## File structure

Multi-context repo:

```text
/
├── CONTEXT-MAP.md
├── docs/adr/                          # system-wide decisions
├── kitchen-service/
│   ├── CONTEXT.md
│   └── docs/adr/                      # context-specific decisions
└── order-service/
    ├── CONTEXT.md
    └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept, use the term as defined in the relevant `CONTEXT.md`.

If the concept you need isn't in the glossary yet, that's a signal -- either you're inventing language the project doesn't use or there's a real gap to note for `/grill-with-docs`.

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding.
