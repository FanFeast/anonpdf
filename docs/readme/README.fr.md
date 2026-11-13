# AnonPDF

[English](../../README.md) · [Deutsch](README.de.md) · [Español](README.es.md) · **Français** · [हिन्दी](README.hi.md) · [Português](README.pt-BR.md) · [Русский](README.ru.md) · [中文](README.zh-CN.md)

Un lecteur et éditeur de PDF qui travaille sur votre appareil et nulle part ailleurs.

**Sans pub. Sans compte. Sans pistage. Sans version payante. Sans permission réseau.**

AnonPDF existe parce que toutes les autres apps PDF semblent vouloir un abonnement,
un compte dans le cloud et un œil sur vos documents. Celle-ci ne veut rien de tout
cela. Elle ne peut pas envoyer vos fichiers, car elle n'a aucune permission d'accès
au réseau : ce n'est pas une promesse dans une politique de confidentialité, c'est
une règle imposée par le système d'exploitation.

> Cette traduction a été rédigée avec une aide automatique. La version de référence
> est le [README en anglais](../../README.md) ; les corrections sont bienvenues.

## Télécharger

- **Android :** l'APK de la [dernière version](https://github.com/FanFeast/anonpdf/releases/latest). Une fiche Google Play arrive bientôt.
- **Linux :** `sudo snap install anonpdf` depuis le [Snap Store](https://snapcraft.io/anonpdf), ou le fichier portable
  `AnonPDF-…-linux-x86_64.tar.gz` de la [dernière version](https://github.com/FanFeast/anonpdf/releases/latest) :
  décompressez-le et double-cliquez sur `AnonPDF.AppImage`. Rien n'est installé.
- **Windows :** le `AnonPDF-…-windows-x64.zip` portable de la [dernière version](https://github.com/FanFeast/anonpdf/releases/latest) :
  décompressez-le et lancez `AnonPDF.exe`.

L'app de bureau est le même moteur et le même éditeur dans une fenêtre native, avec
la même règle : aucun accès au réseau.

| Bibliothèque | Lecture | Vos fichiers |
|---|---|---|
| ![Accueil](../screenshots/home.png) | ![Visionneuse](../screenshots/viewer.png) | ![Parcourir](../screenshots/browse.png) |

## L'éditeur

C'est ici que tout se passe. Choisissez des pages, empilez autant de modifications
que vous voulez, regardez le vrai résultat se mettre à jour, et écrivez le fichier
une seule fois à la fin. Rien n'est écrit avant l'enregistrement, et l'original
n'est jamais modifié.

| Modifier | Glisser pour réordonner | Replier les outils |
|---|---|---|
| ![Éditeur](../screenshots/editor.png) | ![Réordonner](../screenshots/editor-reorder.png) | ![Replié](../screenshots/editor-collapsed.png) |

| Couvrir et réécrire | Surligner et dessiner | Fusionner puis continuer |
|---|---|---|
| ![Texte](../screenshots/editor-text.png) | ![Dessin](../screenshots/editor-draw.png) | ![Fusion](../screenshots/editor-merge.png) |

- **Tous les outils sont ici**, sur quatre pages : *Pages*, *Contenu*,
  *Fichier entier* et *Convertir et protéger*. Les outils de fichier entier
  reçoivent le document avec vos modifications en attente déjà appliquées.
- **Remplissez les formulaires PDF** : champs de texte, cases à cocher, boutons
  radio et listes, avec aperçu pendant la saisie et l'option de verrouiller les
  réponses dans la page à l'enregistrement.
- **La fusion insère là où vous le dites.** Les pages d'un autre PDF arrivent
  comme des pages ordinaires, à faire pivoter, réordonner, recadrer ou supprimer.
- **Modifiez le contenu, pas seulement les pages.** Couvrez quelque chose de
  correcteur et écrivez votre texte par-dessus, surlignez un passage ou dessinez
  à main levée. Le correcteur supprime vraiment ce qu'il couvre : la page devient
  une image sous le cadre, le texte masqué ne peut plus être copié ni recherché.
- **Réordonnez en glissant** et **sélectionnez plusieurs pages d'un toucher**.
- **Annulez et rétablissez** tout l'historique.

L'aperçu n'est pas une approximation : il est produit par le même code que le
fichier final, et un test vérifie qu'ils correspondent au pixel près.

## La garantie, et comment la vérifier

AnonPDF déclare exactement **une** permission :

```
android.permission.MANAGE_EXTERNAL_STORAGE
```

Elle permet au navigateur intégré de lister les PDF de votre appareil. Elle est
**facultative** : refusez-la et tout fonctionne avec le sélecteur du système, qui
ne demande aucune permission. Android la classe comme « accès spécial des
applications » : elle n'apparaît donc pas sur la page Autorisations de l'app.

Ce sur quoi vous pouvez compter :

- **Pas de permission `INTERNET`.** Le système refuse toute connexion. Pas de
  serveur, pas de télémétrie, pas de rapports de plantage, pas de régie
  publicitaire. Les tests vérifient qu'ouvrir une connexion et résoudre un nom
  échouent réellement.
- **Aucune dépendance d'analyse ou de publicité.** Seulement AndroidX, Kotlin et
  PdfBox-Android.
- **`allowBackup=false`** : les documents ne partent pas non plus via les
  sauvegardes cloud.
- **Un test fait échouer la compilation** si une autre permission apparaît.

Vérifiez vous-même :

```bash
apkanalyzer manifest permissions app-release.apk
```

### Ce qui est stocké

| Quoi | Où | Comment l'effacer |
|---|---|---|
| Noms de 20 fichiers récents au plus | Stockage privé de l'app, hors sauvegarde | Paramètres → Effacer les fichiers récents |
| Copies de travail du fichier modifié | Cache de l'app | Paramètres → Supprimer les fichiers temporaires ; aussi à chaque lancement |
| Trois préférences d'affichage | Stockage privé de l'app | Désinstaller |

C'est la liste complète. Aucun identifiant d'aucune sorte.

## Fonctions

**Lecture :** défilement continu, zoom à deux doigts, aller à une page, recherche
de texte, mode nuit, PDF protégés par mot de passe.

**Trouver ses fichiers :** navigateur intégré avec tous les PDF de l'appareil, par
dossiers, ou le sélecteur du système.

**Modifier** (tout en une passe, avec aperçu) : pivoter, recadrer, redimensionner
(A3, A4, A5, Lettre US, Légal US, Tabloïd), supprimer, réordonner, fusionner,
remplir des formulaires, texte, correcteur, surlignage, dessin, filigrane et
numéros de page.

**Fichiers entiers :** fusionner, diviser, extraire des pages, compresser, PDF en
JPG/PNG, images en PDF, extraire le texte, protéger en AES-256 ou retirer un mot de
passe, et signer.

**À l'enregistrement :** retirer, si vous le souhaitez, les informations cachées du
PDF (auteur, app de création, dates, métadonnées XMP, fichiers joints, scripts et
noms dans les commentaires).

**Langues :** anglais, allemand, espagnol, français, hindi, portugais du Brésil,
russe et chinois simplifié, selon la langue de l'appareil.

## Volontairement absent

- **Conversion vers Word, Excel ou PowerPoint.** La faire correctement demande un
  moteur bureautique complet.
- **OCR.** Lire le texte de pages numérisées demande un gros modèle ou un serveur.
- **Synchronisation, comptes, liens de partage.** Le principe est que rien ne sort.
- **Formulaires XFA**, un format que seules les apps d'Adobe savent remplir.
- **Signatures numériques cryptographiques.** « Signer un PDF » pose une image de
  votre signature : de l'encre sur du papier, pas un certificat.

## Configuration requise

- Android 12 ou plus récent ; environ 15 Mo une fois installée
- Linux x86-64 ou Windows 64 bits pour la version de bureau

## Compilation, tests et architecture

Voir le [README en anglais](../../README.md#building).

## Licence

MIT. Voir [LICENSE](../../LICENSE).
