# ADR-24 — Simulateurs hors DDD, pannes partagées, HMAC

- Statut : accepté
- Date : 14 novembre 2026
- Jalon : M6

## Contexte

Le dossier place trois familles de rails simulés (`pispi`, `mno`, `bank`) hors des
contextes DDD, avec une API de contrôle des pannes commune et des callbacks
signés. Les paiements (M7) consommeront ces APIs ; le rapprochement (M8) lira
les relevés et fichiers de règlement.

## Décision

- Modules Gradle `simulators/{common,pispi,mno,bank}` : Spring Boot simples,
  état en mémoire, pas de PostgreSQL (jamais de base partagée avec la
  plateforme).
- `simulators/common` porte `FaultQueue`, `HmacSigner`, `CallbackDispatcher` et
  `POST /control/faults`. Les apps importent `SimulatorCommonConfiguration`.
- Deux instances MNO (`wave` / `orange`) et deux banques (`bank-a` / `bank-b`)
  via la même image et des variables d'environnement (`SIMULATOR_PROVIDER`,
  `SIMULATOR_BANK_NAME`, `SERVER_PORT`).
- Callbacks : HMAC-SHA256 sur `timestamp + "\n" + nonce + "\n" + body`, en-têtes
  `X-Senwallet-Timestamp`, `X-Senwallet-Nonce`, `X-Senwallet-Signature`.
- Relevés bancaires : CSV poussé via le SDK S3 (path-style) vers un magasin
  d'objets. En local / CI, Compose et les IT utilisent Adobe S3Mock
  (S3-compatible) ; un MinIO réel peut remplacer le service Compose sans
  changer le code.

## Alternatives écartées

- WireMock seul : pas d'état de float ni de relevés durables pour M8.
- Une base Postgres par simulateur : coût inutile pour un jalon M.
- Vérifier le JWT sur les simulateurs : hors passerelle, usage local / tests.

## Conséquences

- Chaque mode de panne a un test d'intégration qui observe la réponse HTTP ou
  le callback.
- M7 branchera `CALLBACK_BASE_URL` vers l'inbox paiements et vérifiera la
  signature (fenêtre 5 min, nonce unique).
