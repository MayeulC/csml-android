# CSML - Partie Android
Ce dépot contient une application Android conçue pour se connecter à un module
bluetooth série (SPP), et afficher les alertes reçues par le dispositif
Android.

L'application se lance au démarage, et créée un service d'arrière plan qui
lance l'activité principale lors de la réception d'une alerte.

Le code est basé sur un projet réalisé par Mayeul Cantan en 2015 pour Synergie.

## Licence
Le code est fourni sous licence GPLv3. Voir le fichier LICENSE pour le texte
complet.

## Compilation
Le code est prévu pour être utilisé avec Android Studio. Des adaptations sont
peut-être nécessaires afin de le faire fonctionner avec une autre chaîne de
compilation.

## Liste des améliorations possibles
* Le code original disposait d'un système de somme de contrôle. Bien que
présent dans le protocole SPP, il serait souhaitable d'en implémenter un.

* La partie STM32 est capable de lire le rapport cyclique actuel à volonté. Un
bouton pourraît être implémenté dans l'activité principale afin de lire 
celui-ci.

