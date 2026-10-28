# Installer D2R Mobile sur Android

Cette branche ajoute les textes français du jeu et du port, les messages Android et
le choix **Français** dans **Options → Personnalisation** du profil 1.0.34. Appliquez le changement,
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
   préparez votre pack avec vos propres fichiers Switch **1.0.34.0** ou **1.0.33.0**.
   Le générateur reconnaît les programmes et sélectionne le profil adapté.
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
`v<majeur>.<mineur>.<numéro d'exécution>` avec l'APK signé et `SHA256SUMS`.
Par exemple : **Android 2.2.7**, tag **v2.2.7**, puis **2.2.8**. La version de l'APK
utilise le même numéro. Le fichier `VERSION` définit le préfixe majeur/mineur ; le
dernier nombre vient de GitHub Actions et augmente automatiquement. Des numéros
peuvent être sautés lorsqu'un build échoue ou qu'une pull request est vérifiée.
Les anciennes releases nommées avec un commit restent disponibles.
Un push de plusieurs commits construit le dernier commit de ce push. Les tags
de release ne relancent pas le workflow. Relancer la même exécution conserve son
numéro et met à jour ses fichiers. Le workflow refuse de réutiliser un numéro
attribué à un autre commit.
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

Le profil **`original133`** fonctionne sur Android avec une **manette physique** :
boutons, sticks, croix directionnelle et gâchettes. Il conserve l'interface du jeu
Switch. Les commandes tactiles, la caméra et les améliorations du port liées aux
adresses de la 1.0.34 sont désactivées. Les options de langue sont celles du jeu
original ; le français est choisi à la première installation sur un appareil en français.
Vous pouvez vérifier séparément **Langue des voix** et **Langue des textes** dans
**Options → Audio**. Ces deux valeurs sont réglées sur **Français** sur l'Odin testée.

La même commande de préparation fonctionne pour les deux versions :

```sh
python3 dm/tools/d2rm_android.py /chemin/vers/Update /chemin/vers/sortie
```

Sous Windows, les fichiers 1.0.33 fournis ont été préparés avec **WSL / Ubuntu**.
Le dossier `Update` doit contenir `ExeFS` et `RomFS`. Pour une extraction avec
`extract_game.sh`, définissez `DM_GAME_OUT` vers un dossier `Update` distinct afin
de conserver vos autres versions. Le script accepte les empreintes exactes validées
par ce fork ; le générateur vérifie aussi les identifiants des trois modules.

Conservez tout `RomFS/data` si vous avez la place : la copie testée occupe environ
9 Go et contient les voix françaises dans `08-00000001.data` et `08-00000002.data`.
Les cinématiques ne sont pas décodées dans cet APK et peuvent apparaître en noir ;
utilisez un bouton pour les passer. Création, déplacement, dialogue, sauvegarde
et rechargement ont été vérifiés sur Odin 2 Portal avec les fichiers sur carte SD.
Cela ne constitue pas une validation de tous les actes et de tous les personnages.
Voir [les essais et les limites](VERSION_1_0_33.md).
