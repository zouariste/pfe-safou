# CodeOwl

CodeOwl is a full-stack educational platform for **generating and solving Parsons
programming problems**. Teachers describe what they want to teach, and an LLM
generates tiered task sequences that students solve in an interactive
drag-and-drop puzzle format. The platform aligns generated tasks with the
Bavarian *LehrplanPLUS* computer-science curriculum and collects fine-grained
interaction data for research.

> A Parsons problem presents the lines of a correct program in scrambled order;
> the learner reconstructs the program by putting the lines back in the right
> sequence. CodeOwl extends this with **tiered difficulty** (multiple tiers per
> task), **distractors** (plausible wrong lines), and **automated hints**.

---

## Features

- **AI task generation** — generate whole task sequences from a topic, a piece
  of code, a set of concepts, or any combination, via a pluggable provider layer
  (OpenAI / GPT by default).
- **Tiered Parsons puzzles** — each task can offer several difficulty tiers;
  students solve them in a drag-and-drop interface.
- **Worksheets & library** — group sequences into worksheets, browse a shared
  library with full-text search, and deep-copy other teachers' worksheets to
  adapt them without touching the original.
- **Classes, roles & accounts** — `ADMIN`, `TEACHER` and `STUDENT` roles with
  JWT auth and Argon2 password hashing. Teachers create student accounts inside a
  class; students onboard via a one-time link (no self-registration needed).
- **Curriculum alignment** — tasks can be tied to topics of the Bavarian
  *LehrplanPLUS* (Gymnasium, Informatik), seeded into the database.
- **Automated hints** — static analysis (Ruff for Python, SpotBugs / javac for
  Java) drives contextual hints on a student's current attempt.
- **Internationalisation** — German/English UI (`react-i18next`) and a
  `targetLanguage` setting that controls the language of generated problem
  statements and comments.
- **Research logging** — fine-grained learning events and feedback logs, with
  admin tooling for database backup and research export.

---

## Architecture

CodeOwl runs as three core containers orchestrated by Docker Compose:

| Service    | Tech                                             | Port (host) |
|------------|--------------------------------------------------|-------------|
| `db`       | PostgreSQL 17                                    | `5432`      |
| `backend`  | Spring Boot 3.5 (Java 25), Spring JDBC           | `8080`      |
| `frontend` | React 19 + TypeScript + Vite, served by nginx    | `5175`      |

```
            ┌────────────┐      /codeowl/api/      ┌───────────┐      JDBC      ┌──────────┐
  browser ──▶  frontend   ├────── (nginx proxy) ────▶  backend   ├──────────────▶    db     │
            │  (nginx)   │                          │ (Spring)  │                │ (Postgres)│
            └────────────┘                          └─────┬─────┘                └──────────┘
                                                          │ HTTPS
                                                    ┌─────▼─────┐
                                                    │  OpenAI   │
                                                    └───────────┘
```

The backend additionally shells out to **Ruff**, **SpotBugs**, **javac** and
**pg_dump** at runtime (all installed in the backend image) for hints and
database backups.

---

## Prerequisites

- **Docker** and **Docker Compose** (v2) — the recommended way to run CodeOwl.
- An **OpenAI API key** — required for task generation.

For local development outside Docker you additionally need:

- **JDK 25** (the backend ships a Maven wrapper, `./mvnw`)
- **Node.js 24+** and npm
- Optionally a **C compiler**, `curl` and `tar`, to build the tree-sitter native
  libraries for code analysis — see the note in [Local development](#local-development).

> Running the backend's **tests** under WSL2 additionally needs Docker Desktop's
> **WSL Integration** enabled for that distro. Without it the Testcontainers-backed
> database tests skip and the build still reports success — see
> [`backend/src/main/resources/db/README.md`](backend/src/main/resources/db/README.md#on-windows-enable-docker-desktops-wsl-integration-first).

---

## Quick start (Docker Compose)

```bash
# 1. Clone the repository
git clone <repository-url> codeowl
cd codeowl

# 2. Create your environment file and add your OpenAI key
cp .env.example .env
#   then edit .env and set OPENAI_API_KEY (and JWT_SECRET for anything non-local)

# 3. Build and start the stack
docker compose up --build
```

Once the containers are healthy:

- **Frontend:** http://localhost:5175/codeowl/
- **Backend API:** http://localhost:8080/api/
- **API docs (Swagger UI):** http://localhost:8080/swagger-ui.html

The database schema is applied automatically by the backend on every start —
[Flyway](https://flywaydb.org), migrating from
[`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration) — against the
`db_data` volume, whether that volume is brand new or already has data in it.
To start from a clean database instead, remove the volume:

```bash
docker compose down -v
```

> **Frontend base path:** by default the app is built for the `/codeowl/` base
> path (matching the production deployment), so the local URL includes that
> prefix. To serve it at the web root instead, set `VITE_BASE_PATH=/` in your
> `.env` before building.

---

## Configuration

All configuration is environment-driven. The variables below are consumed by
`docker-compose.yml`; set them in your `.env` file. See
[`.env.example`](.env.example) for a ready-to-copy template.

### Backend

| Variable                | Required | Default                     | Description                                                                 |
|-------------------------|----------|-----------------------------|-----------------------------------------------------------------------------|
| `OPENAI_API_KEY`        | **yes**  | —                           | OpenAI API key for generation, concept detection, tagging and hints.        |
| `JWT_SECRET`            | prod     | dev fallback                | HS256 signing secret. **Must** be ≥ 256 bits; override for any deployment.  |
| `APP_BASE_URL`          | no       | `http://localhost:5175`     | Public frontend URL; used to build links in outgoing e-mails.               |
| `CORS_ALLOWED_ORIGINS`  | no       | `*`                         | Comma-separated allowed origins. Tighten for production.                     |
| `ROOT_ADMIN_EMAIL`      | no       | *(empty)*                   | E-mail granted the `ADMIN` role on startup.¹                                |
| `MAIL_HOST`             | no       | *(empty)*                   | SMTP host. If empty, e-mails are logged to the console instead of sent.     |
| `MAIL_PORT`             | no       | `587`                       | SMTP port.                                                                  |
| `MAIL_USERNAME`         | no       | *(empty)*                   | SMTP username.                                                              |
| `MAIL_PASSWORD`         | no       | *(empty)*                   | SMTP password.                                                              |
| `LDAP_URL`              | no       | `ldaps://ldap01.fim.uni-passau.de/` | LDAP server checked by "Login with FIM-Account".                    |
| `LDAP_BASE`             | no       | `ou=users,dc=fim,dc=uni-passau,dc=de` | Base DN of FIM users; they bind as `uid=<username>,<base>`.       |

The datasource (`SPRING_DATASOURCE_*`) and `SPRING_PROFILES_ACTIVE` are wired up
by `docker-compose.yml` and normally need no changes.

¹ `ROOT_ADMIN_EMAIL` is read by the backend but is **not** passed through in the
default `docker-compose.yml`. To use it under Docker, add it to the `backend`
service's `environment:` block.

### Frontend (build-time)

These are Vite build arguments, applied when the frontend image is built.

| Variable             | Default          | Description                                                        |
|----------------------|------------------|--------------------------------------------------------------------|
| `VITE_BASE_PATH`     | `/codeowl/`      | Base path the SPA is served under. Set to `/` for the web root.    |
| `VITE_API_BASE_URL`  | `/codeowl/api`   | Base URL the frontend uses for API calls (proxied by nginx).       |

### Other application settings

A number of defaults live in
[`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)
and can be overridden via environment variables or by editing the file, e.g. the
default AI provider/model (`ai.defaultProvider`, `ai.openai.defaultModel`),
generation limits (`app.generation.maxAttempts`, `app.generation.maxLocPerTask`),
and which evaluation runners are enabled (`codeowl.*`).

---

## Local development

Run the database in Docker and the two apps natively for fast iteration.

```bash
# Database only
docker compose up db
```

**Backend** (Spring Boot, port 8080):

```bash
cd backend
./mvnw spring-boot:run
```

This starts the backend with the `local` profile
([`application-local.yml`](backend/src/main/resources/application-local.yml)),
which points at the database container on `localhost:5432` and reads
`OPENAI_API_KEY`, `ROOT_ADMIN_EMAIL` and the other settings from the same
`.env` file Docker Compose uses (see [Quick start](#quick-start-docker-compose)).
Write values in `.env` unquoted
(`KEY=value`), since quotes are kept as part of the value. In IntelliJ, set the
run configuration's active profile to `local`. Real environment variables still
take precedence, and `-Dspring-boot.run.profiles=...` picks a different profile.
> **Code analysis (tree-sitter):** complexity and change metrics use the
> official [jtreesitter](https://github.com/tree-sitter/java-tree-sitter)
> bindings, which need the tree-sitter runtime and grammar libraries for your
> platform. The Docker image builds them itself; for a native run, build them
> once into `backend/native/` (Linux, macOS, or Windows from Git Bash/MSYS2 with
> MinGW gcc or zig; the script picks the first of `cc`, `gcc`, `clang` and `zig` on
> the PATH unless `CC` is set):
> ```bash
> cd backend
> scripts/build-treesitter-natives.sh
> ```
> The backend looks in `./native` relative to where it starts, or wherever
> `TREESITTER_NATIVE_DIR` points. Without the libraries it still starts, logs a
> warning, and falls back to zero complexity and line-based change analysis.
> The tree-sitter tests skip in that case.


**Frontend** (Vite dev server, port 5173):

```bash
cd frontend
npm install
npm run dev
```

For a dev setup that talks to a remote backend, the Vite config supports a proxy
via `VITE_DEV_PROXY_TARGET` (and optional `VITE_DEV_PROXY_REWRITE_TO`) — see
[`frontend/vite.config.ts`](frontend/vite.config.ts).

---

## Project structure

```
codeowl/
├── backend/            # Spring Boot 3.5 / Java 25 (package de.uni_passau.fim.se2)
│   ├── src/main/java/  # API controllers, services, domain, persistence, AI clients
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/ # Flyway migrations, run automatically on backend startup
│   │   ├── db/undo/      # hand-written rollbacks, run manually, not by Flyway
│   │   ├── PromptStuff/ # LLM prompt templates
│   │   └── concepts/    # concept catalog & alias mapping
│   └── Dockerfile
├── frontend/           # React 19 + TypeScript + Vite, served by nginx
│   ├── src/
│   ├── nginx.conf
│   └── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## License

CodeOwl is licensed under the GNU General Public License v3.0 — see [LICENSE](LICENSE).

Developed at the Chair of Software Engineering II, University of Passau by Luca Cisternino and Florian Obermüller.

---

## Citation

If you use CodeOwl in your research, please cite:

```bibtex
@misc{cisternino2026codeowlautomaticgenerationtiered,
      title={CodeOwl: Automatic Generation of Tiered Parsons Problems for Introductory Programming}, 
      author={Luca Cisternino and Florian Obermüller and Gordon Fraser},
      year={2026},
      eprint={2607.12460},
      archivePrefix={arXiv},
      primaryClass={cs.SE},
      url={https://arxiv.org/abs/2607.12460}, 
}
```
