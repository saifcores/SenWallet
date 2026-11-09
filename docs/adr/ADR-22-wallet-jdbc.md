# ADR-22 — Persistance du wallet en SQL explicite

- Statut : accepté
- Date : 5 novembre 2026
- Jalon : M4

## Contexte

ADR-13 fixe le ledger sur du SQL explicite, sans JPA. Le dossier laisse aux autres contextes Spring Data JDBC ou JPA. Le ledger a déjà les deux rôles (`owner` pour Flyway, `app` à l'exécution, ADR-20).

## Décision

Le wallet reprend le même choix :

- Flyway, connecté en `wallet_owner`, versionne le schéma (`contexts/wallet/wallet-infrastructure/src/main/resources/db/wallet`).
- Le service se connecte en `wallet_app`. Les droits sont accordés table par table dans `V1__wallet_schema.sql`.
- Les dépôts sont des classes `JdbcClient`. Pas de JPA, pas d'Hibernate.
- Plafonds et frais sont des lignes datées (`valid_from`), pas des constantes. La commission P2P est `montant * taux`, arrondie au franc XOF supérieur à partir de 0,5 (`Money`, half-up).

## Alternatives écartées

- JPA, laissé ouvert par le dossier pour les contextes hors ledger : une seconde pile de persistance pour six tables, sans gain sur les verrous et les droits.
- Des plafonds codés en dur : la prochaine instruction BCEAO ne pourrait pas entrer par une ligne `valid_from`.

## Conséquences

- Le script `deploy/postgres/init/02-wallet.sh` crée la base et les deux rôles. Un volume Docker créé avant M4 doit être recréé une fois.
- ArchUnit vérifie que le domaine wallet ne dépend ni de Spring ni de l'infrastructure.
