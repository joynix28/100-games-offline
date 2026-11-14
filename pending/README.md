# Jeux en attente

Ces 70 jeux ont été générés mais **ne compilent pas encore** (imports manquants,
quelques erreurs de composition). Ils sont volontairement sortis de `src/` pour
que l'application reste compilable et publiable.

Pour les intégrer : corriger un lot, le déplacer dans
`shared/src/commonMain/kotlin/com/botillon/games/games/`, puis ajouter son
registre (`registry/BatchX.kt`) dans `Catalog.entries`.

Un lot = 10 jeux = une compilation. Ça évite de saturer la RAM du serveur.
