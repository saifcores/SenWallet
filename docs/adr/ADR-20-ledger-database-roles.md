# ADR-20 — Rôle propriétaire et rôle applicatif du ledger

- Statut : accepté
- Date : 1er novembre 2026
- Jalon : M2

## Contexte

Le dossier exige que le rôle applicatif n'ait ni `UPDATE` ni `DELETE` sur `ledger_entries`. En M0, la base `ledger` appartenait à `ledger_app`, qui pouvait donc tout faire, y compris désactiver les déclencheurs.

## Décision

- Le script d'initialisation (`deploy/postgres/init/01-ledger.sh`, réutilisé par Testcontainers) crée deux rôles :
  - `ledger_owner` possède la base et le schéma `public` ; Flyway se connecte avec ce rôle (`spring.flyway.user`).
  - `ledger_app` est le rôle de la source de données du service.
- La migration V1 accorde à `ledger_app` (nom passé par le placeholder Flyway `${app_role}`) exactement :
  - `SELECT, INSERT` sur `ledgers`, `ledger_accounts`, `ledger_entries` ;
  - `SELECT, INSERT, UPDATE` sur `account_balances` ;
  - `SELECT, INSERT` sur `ledger_transactions`, et `UPDATE` sur les seules colonnes `status`, `posted_at` et `archived_at` ;
  - `EXECUTE` sur `ensure_entry_partitions`.
- Les déclencheurs d'immuabilité s'appliquent en plus à tous les rôles, propriétaire compris.

## Alternatives écartées

- Un seul rôle avec révocation partielle : le propriétaire d'une table garde implicitement tous les droits.
- Des droits par table sans droits par colonne sur `ledger_transactions` : une modification de `metadata` ou de `request_hash` ne serait arrêtée que par le déclencheur.

## Conséquences

- Les tests d'intégration vérifient, en SQL brut comme `ledger_app`, que `UPDATE`/`DELETE` sur `ledger_entries` échouent avec `permission denied`.
- Un volume Docker créé avant M2 doit être recréé une fois (`docker compose down -v`).
- `LEDGER_OWNER_PASSWORD` rejoint `deploy/.env.example`.
