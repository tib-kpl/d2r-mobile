# Installer D2R Mobile sur Android

Cette branche ajoute les textes français du jeu et du port, les messages Android et
le choix **Français** dans **Options → Personnalisation**. Appliquez le changement,
puis fermez et relancez l'application. Au premier lancement, le français est choisi
automatiquement si la langue de l'appareil ou de l'application est le français.
Les voix restent en anglais ; les notes d'objets restent réservées au chinois et à
l'espagnol.

## Installation sur Odin 2 / Odin 2 Portal

1. Téléchargez `D2RMobile-android.apk` depuis les
   [releases de cette branche](https://github.com/tib-kpl/d2r-mobile/releases).
2. Téléchargez aussi l'archive des sources du **même tag**. Depuis ces sources,
   préparez votre pack d'import avec vos propres fichiers Switch **1.0.34.0**
   (avec Reign of the Warlock). La version **1.0.33.0 n'est pas compatible**.
3. Suivez [le guide Android](ANDROID.md) pour les dépendances et la commande
   `py dm/tools/d2rm_android.py <Update> <sortie>` sous Windows.
4. Copiez le dossier généré `android` dans `Download/S2X/D2R Mobile/`, et les
   ressources `.data` avec `.build.config` dans `Download/S2X/D2R Mobile/data/`.
5. Installez l'APK, autorisez l'accès à tous les fichiers et laissez l'import finir.

Le changement de langue nécessite de **recréer le pack avec les nouvelles sources**.
Les anciennes ressources tactiles seront rejetées si leur empreinte ne correspond
pas à celle de l'application. Le jeu est hors ligne, sans multijoueur.

## Builds GitHub automatiques

Le workflow `.github/workflows/android.yml` compile un APK ARM64 et exécute les
contrôles à chaque push de branche. Chaque push réussi publie une **préversion**
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
Le numéro Android augmente avec le numéro d'exécution GitHub ; les préversions
restent distinctes des versions stables.
