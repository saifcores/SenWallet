# ADR-18 — Ajouts au schéma du ledger

- Statut : accepté
- Date : 1er novembre 2026
- Jalon : M2

## Contexte

Le DDL de la section 3 du dossier d'architecture est un extrait. En l'implémentant (`V1__ledger_schema.sql`), trois besoins n'étaient pas couverts : calculer un solde à une date passée, retrouver la version du compte produite par une écriture, et garantir qu'une transaction n'est contre-passée qu'une fois.

## Décision

- `ledger_transactions.archived_at timestamptz` : sans cette date, impossible de savoir si une transaction archivée était encore en attente à une date donnée. Le solde `as_of` compte une transaction comme réglée à partir de `posted_at`, et comme en attente de `effective_at` jusqu'à `posted_at` ou `archived_at`.
- `ledger_entries.account_version bigint` : la `lock_version` du compte après l'écriture (déjà présente dans le dossier, en commentaire). Elle permettra de rejouer l'historique d'un compte dans l'ordre exact.
- Index unique partiel `ledger_transactions (reverses_id) WHERE reverses_id IS NOT NULL` : une contre-passation au plus par transaction, même avec deux appels concurrents portant des `external_id` différents ; la violation est traduite en `transaction_already_reversed` (409).
- Une table `ledgers (id, name, environment, created_at)` que le dossier référence sans la définir ; `environment` vaut `live` ou `test`.
- Contrainte `CHECK` de cohérence entre `status`, `posted_at` et `archived_at`.
- Un second déclencheur de contrainte différé, sur `ledger_transactions`, refuse au commit un en-tête sans au moins deux écritures.

## Alternatives écartées

- Déduire `archived_at` du journal d'événements (M3) : le solde `as_of` dépendrait de l'outbox, qui n'est pas une source comptable.
- Refuser une seconde contre-passation uniquement dans le code : deux requêtes concurrentes passeraient toutes les deux.

## Conséquences

- `GET /ledger_accounts/{id}/balances?as_of=` se calcule par une seule requête sur `ledger_entries ⋈ ledger_transactions`, sans élagage de partition (filtre sur `account_id`).
- Le déclencheur de garde des transactions autorise la modification de `archived_at` en plus de `status` et `posted_at`.
