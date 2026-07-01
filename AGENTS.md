# Repository Guidelines

## Project Structure & Module Organization

This repository contains planning and implementation documents for a hospital indoor route guidance system.

- `docs/README.md`: documentation index and first-step guidance.
- `docs/01-医院室内导航总体设计方案.md`: product scope and architecture.
- `docs/02-Demo快速操作手册.md`: recommended single-file frontend MVP.
- `docs/04-数据库设计.md`: PostgreSQL/PostGIS schema draft.
- `docs/05-接口设计.md`: planned API contract.
- `docs/06-SpringBoot后端骨架.md`: planned Java backend structure.
- `docs/07-前端Demo骨架.md`: planned frontend demo structure.

When code is added, keep the first demo isolated, for example `hospital-map-demo/index.html`. Future backend code should follow `docs/06-SpringBoot后端骨架.md`; future tests should live next to implementation or under framework test directories.

## Build, Test, and Development Commands

There are no build or test commands yet because the repository is documentation-only.

Useful local checks:

```bash
git status --short
find . -maxdepth 2 -type f -print
```

Once the HTML demo exists, it should be runnable by opening `hospital-map-demo/index.html` directly. If a Spring Boot service is added later, document its exact `mvn` or `gradle` commands in this file and in `docs/README.md`.

## Coding Style & Naming Conventions

Use concise Markdown with descriptive headings and fenced code blocks for examples. Existing documentation uses Chinese filenames with numeric prefixes; preserve that pattern for new docs, for example `09-地图编辑后台设计.md`.

For future Java code, use standard Spring Boot conventions: `PascalCase` classes, `camelCase` fields and methods, and package names under a stable project namespace. For frontend demo code, keep data structures explicit (`nodes`, `edges`, `pois`) and prefer readable IDs such as `N1`, `P1`, and `ELEV-A`.

## Testing Guidelines

No testing framework is configured yet. For the first HTML demo, manually verify at least:

- start and destination selection
- shortest-path calculation
- highlighted route rendering
- route step text

For future backend work, add unit tests for A* pathfinding and service tests for route responses. Name tests after behavior, such as `findsShortestRouteBetweenPois`.

## Commit & Pull Request Guidelines

The current history contains only `init`, so no detailed commit convention exists. Use short imperative commit messages, for example `add frontend route demo` or `document database schema`.

Pull requests should include a clear summary, affected docs or modules, verification steps, and screenshots or short recordings for UI changes. Link related issues when available and call out schema or API contract changes explicitly.

## Architecture Notes

The project intentionally avoids real-time positioning. The core model is fixed POIs, path nodes, path edges, and route calculation over a maintained indoor graph.
