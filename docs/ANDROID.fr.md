# Installer D2R Mobile sur Android

Cette branche ajoute les textes français du jeu et du port, les messages Android et
le choix **Français** dans **Options → Personnalisation**. Appliquez le changement,
puis fermez et relancez l'application. Au premier lancement, le français est choisi
automatiquement si la langue de l'appareil ou de l'application est le français.
Les voix françaises sont choisies avec les textes français si leurs archives sont
présentes. Le générateur lit le manifeste de votre jeu et affiche les archives
audio à conserver dans `data`. Si une archive manque, le jeu utilise les voix
anglaises. Les notes d'objets restent réservées au chinois et à l'espagnol.

## Installation sur Odin 2 / Odin 2 Portal

1. Téléchargez `D2RMobile-android.apk` depuis les
   [releases de cette branche](https://github.com/tib-kpl/d2r-mobile/releases).
2. Téléchargez aussi l'archive des sources du **même tag**. Depuis ces sources,
   préparez votre pack d'import avec vos propres fichiers Switch **1.0.34.0**
   (avec Reign of the Warlock). La version **1.0.33.0 n'est pas compatible**.
3. Suivez [le guide Android](ANDROID.md) pour les dépendances et la commande
   `py dm/tools/d2rm_android.py <Update> <sortie>` sous Windows.
4. Sur le stockage interne ou une carte SD, créez un dossier du jeu contenant
   `android` (le pack généré) et `data` (vos ressources `.data` et `.build.config`).
   Conservez aussi les archives audio françaises indiquées par le générateur.
5. Installez l'APK, autorisez l'accès à tous les fichiers, puis touchez **Choisir le
   dossier du jeu** et sélectionnez ce dossier dans le sélecteur Android. Vous
   pouvez aussi sélectionner directement son sous-dossier `data`.

L'application mémorise le chemin et lit les fichiers **sur place** : ne supprimez
pas le dossier `android`. Elle n'extrait pas un NSP/XCI au lancement ; la préparation
sur ordinateur reste nécessaire. Les sauvegardes, journaux et caches restent dans
le stockage de l'application. Les dossiers cloud ou virtuels sont refusés car le
moteur utilise les fichiers locaux directement.

En cas de dossier absent ou déplacé, le bouton de choix reste disponible. La touche
Menu Android ouvre aussi le choix du dossier. Après un changement alors que le
moteur est chargé, fermez complètement l'application et relancez-la.
Le sélecteur privilégie la carte SD au premier choix lorsqu'elle est disponible.
**Oublier ce dossier** efface seulement le chemin mémorisé, sans supprimer les fichiers.

Le changement de langue nécessite de **recréer le pack avec les nouvelles sources**.
Les anciennes ressources tactiles seront rejetées si leur empreinte ne correspond
pas à celle de l'application. Le jeu est hors ligne, sans multijoueur.

## Builds GitHub automatiques

Le workflow `.github/workflows/android.yml` compile un APK ARM64 et exécute les
contrôles à chaque push de branche. Chaque push réussi publie une **release normale**
`android-<SHA complet du commit>` avec l'APK signé et `SHA256SUMS`. Un push de plusieurs
commits construit le dernier commit de ce push. Les tags de release ne relancent
pas le workflow. Une relance du même commit met à jour ses fichiers de release.
Les pull requests compilent et vérifient un APK de débogage, sans publication et
sans accès aux clés de signature. Le workflow peut aussi être lancé manuellement.

Les builds utilisent des tables vides chargées depuis le pack d'import à
l'exécution : aucun fichier du jeu ni clé Switch n'est nécessaire sur GitHub.
Les métadonnées publiques des atlas servent à générer les interfaces de l'APK.

Pour configurer ce workflow dans un autre dépôt, ajoutez ces secrets Actions :

- `ANDROID_KEYSTORE_BASE64` : keystore PKCS12 encodé en base64.
- `ANDROID_KEY_ALIAS` : alias de la clé.
- `ANDROID_KEY_PASSWORD` : mot de passe du keystore et de la clé.

Conservez la même clé pour les mises à jour. L'installation d'un APK signé avec
une autre clé peut nécessiter une désinstallation, qui efface les sauvegardes.
Le numéro Android augmente avec le numéro d'exécution GitHub. Une release automatique
signifie que le build et les contrôles ont réussi ; elle ne remplace pas une
validation en partie sur chaque modèle de console.

## Version 1.0.33.0

La ROM fournie a été extraite et analysée localement. Les voix françaises sont
bien présentes. En revanche, le programme principal diffère et les instructions
attendues aux adresses des correctifs ne correspondent pas. Cette version n'est
**pas encore prise en charge** : il faut un profil d'adresses et des correctifs
spécifiques, puis une validation en jeu. Voir [le diagnostic](VERSION_1_0_33.md).
