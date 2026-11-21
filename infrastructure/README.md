# Infrastructure

> Arsip operasi CoinMind. Jalur build dan runbook lokal PhiveAlpha ada di [Phase 1I](../docs/development/phivealpha-phase-1i.md).

Deployment target: Ubuntu Server, headless.

```text
infrastructure/
├── docker/
├── database/
└── nginx/
```

The server does not require a desktop environment. Exchange feeds, AI APIs, databases, Redis, Nginx, and headless browser automation can all run without GUI.
