# Database bootstrap scripts

Everything in this directory is mounted into the Postgres container at
`/docker-entrypoint-initdb.d` (see `docker-compose.yml`). The official image runs these
scripts **once**, when the data volume is empty — never again on an existing database.

## Naming: always two digits

The entrypoint sorts filenames with `LC_ALL=C`, which is **lexicographic, not numeric**.
That is why every prefix is zero-padded:

    01_codeowl_backup.sql       <- base schema and seed data, must run first
    02_curriculum_seed.sql
    ...
    10_migration_worksheet_published.sql

With single-digit prefixes, `10_…` sorts *before* `1_…`, so the tenth migration ran first,
failed on a table the base dump had not created yet, and took the container down with it.
Existing setups did not notice, because their volume predated the file. Every fresh
checkout hit it.

Use two digits. If you ever need more than 99, pad to three.

## Existing databases need the migration applied by hand

Because these scripts only run against an empty volume, adding a migration here does
**not** update anyone's running database. A new migration has to be applied separately to
existing environments, or the volume has to be recreated:

```bash
docker compose down -v && docker compose up -d db
```

That deletes all local data, so use it on development volumes only.

## The tests run against this directory too

`WorksheetSearchDatabaseTest` and `MarketplaceJourneyDatabaseTest` start a Postgres container
from these exact scripts, so the schema they test is the schema that ships. A migration that
breaks a fresh bootstrap therefore breaks those tests rather than only breaking the next person
to clone the repository.

**But only locally.** Both classes are skipped where there is no Docker daemon, and the shared
CI runner is unprivileged, so a `docker:dind` service cannot start there. A green pipeline
consequently says nothing about whether these scripts still bootstrap. Run `mvn test` with
Docker up after changing anything in this directory, and do not take CI's word for it.

### On Windows: enable Docker Desktop's WSL integration first

"Docker up" means up *inside the distro you build in*. Docker Desktop running on Windows is
not enough: without integration the distro has no `docker` CLI and no `/var/run/docker.sock`,
Testcontainers finds no Docker environment, and the two classes above skip — leaving a green
`BUILD SUCCESS` that has verified nothing. It is the same false green as CI, on your own
machine, which makes it much easier to trust by mistake.

Enable it under **Settings → Resources → WSL Integration**, for the distro holding the
checkout. Then `ls -la /var/run/docker.sock` inside that distro should show a socket owned by
`root:docker`, and your user should appear in `id -nG`.

Two things to expect:

- Toggling it **restarts the Docker engine**, which stops running containers. Anything without
  a `restart:` policy stays down until started again.
- Confirm the tests actually ran. `Tests run: N, ... Skipped: 0` is the thing to look for;
  `Skipped: 11` with a green build is the failure mode this whole section is about.

Separately, the daemon's API version is handled in `backend/pom.xml`: Docker Engine 29 refuses
the API 1.32 that docker-java defaults to, so Surefire pins `api.version` via the
`docker.api.version` property. On a daemon older than Docker 25, override it with
`-Ddocker.api.version=1.41` or similar.

## Writing a migration

Make it re-runnable. `ADD COLUMN IF NOT EXISTS`, `CREATE TABLE IF NOT EXISTS`, and a
`DO $$ … $$` guard around constraints, which have no `IF NOT EXISTS` form — the existing
files show the pattern. Re-runnability is what lets someone apply a migration to an
environment without first working out whether it has already been applied.
