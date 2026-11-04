# ADR-19 — Partitions mensuelles de `ledger_entries`

- Statut : accepté
- Date : 1er novembre 2026
- Jalon : M2

## Contexte

Le dossier partitionne `ledger_entries` par mois sur `created_at`, sans dire qui crée les partitions. PostgreSQL refuse une écriture sans partition cible, sauf s'il existe une partition `DEFAULT` ; mais il refuse ensuite de créer la partition d'un mois dont des lignes sont déjà dans `DEFAULT`.

## Décision

- La migration V1 crée les partitions de 2026-10 à 2027-12 (mois UTC), plus `ledger_entries_default` comme filet de sécurité.
- Deux fonctions SQL `SECURITY DEFINER` appartenant à `ledger_owner` : `create_entry_partition(month date)` et `ensure_entry_partitions(months_ahead int)`, qui crée les partitions manquantes du mois courant à `months_ahead` mois plus tard et renvoie leur nombre. Un verrou consultatif sérialise deux instances qui démarrent ensemble.
- Le service ledger appelle `ensure_entry_partitions(3)` au démarrage puis chaque jour à 03:00 UTC (`PartitionJob`). `ledger_app` n'a que le droit `EXECUTE` sur cette fonction, pas celui de créer des tables.
- La partition `DEFAULT` doit rester vide : un test d'intégration le vérifie, et `PartitionMaintainer.defaultPartitionRows()` servira à l'alerte en M9.

## Alternatives écartées

- `pg_partman` : une extension de plus à installer dans l'image et en CI pour un besoin de quelques lignes.
- Créer les partitions dans des migrations Flyway successives : une migration oubliée bloque les écritures.
- Pas de partition `DEFAULT` : un mois manquant ferait échouer chaque posting au lieu de déclencher une alerte.

## Conséquences

- Créer une partition prend un verrou sur `ledger_entries` ; l'opération est rare (une fois par mois) et instantanée sur une table vide.
- `ledger_app` ne peut pas lire une partition directement, seulement via `ledger_entries`.
