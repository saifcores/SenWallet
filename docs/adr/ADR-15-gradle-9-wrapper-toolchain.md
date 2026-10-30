# ADR-15 — Wrapper Gradle 9 et toolchain Java 25

- Statut : accepté
- Date : 30 octobre 2026
- Jalon : M0

## Contexte

Le dossier d'architecture fixe Java 25 (LTS) et Gradle Kotlin DSL (ADR-11). Le Gradle installé globalement sur le poste de développement est en 8.13, qui ne sait pas s'exécuter sur Java 25. Le JDK par défaut du poste est Java 11.

## Décision

- Le dépôt embarque un wrapper Gradle **9.8.2** ; toute commande passe par `./gradlew`, jamais par un Gradle global.
- La version de Java est déclarée **une seule fois**, dans `gradle/libs.versions.toml` (`java = "25"`), et appliquée par le plugin de convention `senwallet.java-library` via une toolchain (`languageVersion`) et `options.release`.
- Le plugin `org.gradle.toolchains.foojay-resolver-convention` est déclaré dans `settings.gradle.kts` : si aucun JDK 25 n'est présent, Gradle le télécharge.
- La compilation est stricte : `-Xlint:all -Werror`.

## Alternatives écartées

- Mettre à jour le Gradle global : dépend du poste, non reproductible en CI.
- Fixer `sourceCompatibility` sans toolchain : laisse le build dépendre du JDK qui lance Gradle.

## Conséquences

- Lancer Gradle demande un JDK 17+ pour le démon ; en local, `JAVA_HOME` pointe sur un JDK 25 (SDKMAN : `~/.sdkman/candidates/java/25-tem`).
- Monter de version se fait à un seul endroit (catalogue de versions ou `gradle-wrapper.properties`).
