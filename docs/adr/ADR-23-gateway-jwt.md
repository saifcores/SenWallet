# ADR-23 — Traefik route, JWT aux services

- Statut : accepté
- Date : 9 novembre 2026
- Jalon : M5

## Contexte

Le dossier place TLS, le routage, les quotas et la vérification JWT sur Traefik, et
Spring Security OAuth2 Resource Server sur chaque service. Traefik OSS (v3) n'a
pas de middleware JWT : celui-ci est dans Traefik Hub / Enterprise. Un plugin
ajouterait une pièce mobile pour un jalon de taille S.

Le ledger ne doit pas être joignable par la passerelle. Les appels wallet →
ledger passent par un jeton de service (client credentials).

## Décision

- Traefik (file provider, pas de socket Docker) termine TLS, route
  `PathPrefix(/api)` vers le wallet avec `StripPrefix(/api)`, et applique un
  quota par en-tête `Authorization`. Aucune route vers le ledger.
- Chaque service vérifie le JWT (issuer + audience) avec Spring OAuth2
  Resource Server. Le wallet exige le rôle `customer` sauf `POST /customers`
  et `/actuator/health`.
- Le ledger n'accepte qu'un jeton dont l'audience est `ledger` (client
  `wallet-service`). Les ports 8001 et 8002 restent publiés pour la santé et
  le debug local ; sans Bearer, l'API répond 401.
- Keycloak importe le realm `senwallet` depuis
  `deploy/keycloak/senwallet-realm.json` (Compose et Testcontainers).
- `Idempotency-Key` reste exigée par le wallet (Traefik OSS ne peut pas
  imposer un en-tête).

## Alternatives écartées

- Vérifier le JWT dans Traefik via un plugin ou ForwardAuth : une brique de
  plus, alors que le ledger doit de toute façon vérifier le jeton (il n'est
  pas derrière Traefik).
- Ne plus publier 8002 : les healthchecks Compose et le debug local
  s'appuient dessus ; le 401 et l'absence de route Traefik suffisent.
- mTLS entre services : option du dossier, hors M5.

## Conséquences

- L'exemple P2P passe par `https://localhost/api/transfers` avec un jeton
  utilisateur. `curl -k` ignore le certificat auto-signé de Traefik.
- Dans Compose, Keycloak pinne `--hostname=http://localhost:8080` (même
  `iss` que les jetons obtenus depuis l'hôte). Les services valident cet
  issuer et chargent les JWKS via `http://keycloak:8080/...`. Les tests
  Java ont leur propre Keycloak Testcontainers, issuer sur
  `127.0.0.1:<port>`.
- Les clients `payments-service` et `recon-service` sont dans le realm,
  désactivés jusqu'à M7 / M8.
