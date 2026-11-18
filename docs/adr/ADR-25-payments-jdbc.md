# ADR-25 — Persistance du contexte paiements en SQL explicite

- Statut : accepté
- Date : 16 novembre 2026
- Jalon : M7

## Contexte

Le dossier décrit les ordres de paiement comme une machine à états persistée,
avec une inbox de callbacks et des workers qui prennent les lignes en
`FOR UPDATE SKIP LOCKED`. ADR-13 fixe le ledger en SQL explicite. ADR-22 a
repris ce choix pour le wallet.

## Décision

Le contexte paiements reprend le même choix :

- Flyway, connecté en `payments_owner`, versionne le schéma
  (`contexts/payments/payments-infrastructure/src/main/resources/db/payments`).
- Le service se connecte en `payments_app`. Les droits sont accordés table par
  table.
- Les dépôts sont des classes `JdbcClient`. Pas de JPA.
- Le plafond mensuel de rechargement (15 000 000 XOF) et le taux de frais du
  cash-out sont des lignes datées. Le taux de frais est semé à 0 pour que le
  scénario SendCash bloque les 50 000 XOF entiers.
- Les délais d'enquête (30 s, 1, 2, 5, 10 min) sont de la configuration, pour
  que les tests puissent les raccourcir sans changer la machine à états.

## Alternatives écartées

- JPA, laissé ouvert par le dossier hors ledger : les verrous `SKIP LOCKED` et
  l'inbox doivent rester visibles dans le SQL.
- Un moteur de saga externe : huit états et deux workers tiennent dans le
  service.

## Conséquences

- `deploy/postgres/init/03-payments.sh` crée la base et les deux rôles. Un
  volume Docker créé avant M7 doit être recréé une fois.
- ArchUnit vérifie que le domaine paiements ne dépend ni de Spring ni de
  l'infrastructure.
