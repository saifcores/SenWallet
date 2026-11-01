# ADR-17 — `Amount` du ledger construit sur `Money`

- Statut : accepté
- Date : 1er novembre 2026
- Jalon : M1

## Contexte

Le dossier d'architecture (section 11.4) esquisse `record Amount(long value)` pour le montant d'une écriture. ADR-16 a fixé `Money` (`BigDecimal` + `Currency`) comme type monétaire du code Java. Il fallait choisir l'un des deux pour le domaine du ledger.

## Décision

- `Amount` enveloppe un `Money` du noyau partagé et refuse tout montant nul ou négatif ; le sens est porté par `Direction`. `Amount.xof(long)` reste la fabrique courante.
- Les quatre seaux de `Balances` (`posted_dr`, `posted_cr`, `pending_dr`, `pending_cr`) et les soldes calculés (`posted`, `pending`, `available`) sont des `Money`. Un seau est toujours ≥ 0 ; un solde peut être négatif.
- La conversion vers les colonnes `BIGINT` passe par `Money.toMinorUnits()`, dans l'infrastructure (M2).

## Alternatives écartées

- `long` comme dans l'esquisse du dossier : deux représentations monétaires dans le même code, et chaque addition de seau à protéger par `Math.addExact`.

## Conséquences

- Un seul type monétaire dans tout le code Java ; le contrôle de devise de `Money` s'applique aussi aux écritures, aux seaux et aux conditions.
- Le coût de `BigDecimal` est négligeable au regard d'un aller-retour PostgreSQL ; il sera mesuré au jalon M10.
