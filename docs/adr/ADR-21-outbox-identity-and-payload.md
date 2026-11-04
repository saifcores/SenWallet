# ADR-21 — Clé d'ordre de l'outbox et charge utile mince

- Statut : accepté
- Date : 4 novembre 2026
- Jalon : M3

## Contexte

La section 6.1 du dossier décrit `outbox(id, …)` et un relais `ORDER BY id`. Les événements de domaine portent un `event_id` UUID v4, dont l'ordre n'est pas l'ordre d'insertion. L'enveloppe commune inclut `trace_id`, et le dossier ne fixe pas le contenu de `payload`.

## Décision

- `outbox.id` est un `bigint GENERATED ALWAYS AS IDENTITY`. C'est l'ordre du relais (`ORDER BY id`, `FOR UPDATE SKIP LOCKED`).
- `outbox.event_id` est l'UUID de l'événement, unique. C'est la clé de dédoublonnage des consommateurs (`processed_events`).
- La charge utile est une notification : `transaction_id`, `external_id`, `ledger_id`. Le consommateur relit l'opération par l'API du ledger. Les trois types `ledger.transaction.pending`, `.posted` et `.archived` partagent cette forme ; `event_type` les distingue.
- `trace_id` est présent dans l'enveloppe et vaut `null` tant que le traçage n'est pas branché (M10). `version` vaut `1`.
- Le rôle applicatif a `SELECT`, `INSERT` et `UPDATE (published_at)` sur `outbox`. Pas de `DELETE`, pas de modification du `payload`.

## Alternatives écartées

- Utiliser l'`event_id` comme seule clé primaire : `ORDER BY id` ne suivrait pas l'ordre d'écriture.
- Recopier les écritures dans l'événement : le contrat dupliquerait l'API, et un changement de schéma d'écriture casserait les consommateurs.
- Omettre `trace_id` jusqu'à M10 : l'enveloppe commune du dossier le contient déjà ; un champ nul évite une nouvelle version de schéma.

## Conséquences

- Le relais publie le JSON stocké tel quel, clé de routage = `event_type`, sur l'exchange topic `ledger`.
- Un arrêt après une confirmation broker et avant le `COMMIT` republie les mêmes `event_id`. L'effet reste unique grâce à `processed_events`, inséré dans la transaction du consommateur.
- M4 copie `platform/event-inbox` (`db/processed_events.sql`) dans la migration du wallet.
