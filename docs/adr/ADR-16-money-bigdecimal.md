# ADR-16 — `Money` en `BigDecimal` dans le code Java

- Statut : accepté
- Date : 30 octobre 2026
- Jalon : M0

## Contexte

Le principe 7 du dossier d'architecture impose des montants entiers (`BIGINT`, XOF, exposant 0) dans la base, l'API et les événements. Le noyau partagé a besoin d'un type `Money` pour le code Java.

## Décision

- `Money` porte un `BigDecimal` et une `Currency`.
- Le constructeur ramène toujours l'échelle à l'exposant de la devise (0 pour XOF) : un montant avec des décimales non nulles est **rejeté**, jamais arrondi. `100.00` et `100` sont donc égaux au sens de `equals`.
- L'arithmétique (`plus`, `minus`, `negate`) est exacte et ne déborde pas.
- `toMinorUnits()` convertit vers le `long` des colonnes `BIGINT` et lève une exception si le montant n'y tient pas.

## Ce qui ne change pas

La base, l'API et les événements restent en entiers XOF (principe 7). La conversion se fait aux frontières, dans l'infrastructure.

## Alternatives écartées

- `long` partout : plus léger, mais le débordement doit être surveillé à chaque opération (`Math.addExact`), et une devise à exposant non nul demanderait de tout reprendre.

## Conséquences

- Les comparaisons passent par `compareTo` / `equals` de `Money`, jamais par `BigDecimal.equals` sur des valeurs non normalisées.
- La conversion `Money` vers `BIGINT` est explicite et peut échouer : elle est testée.
