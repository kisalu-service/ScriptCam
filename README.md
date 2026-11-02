# ScriptCam — téléprompteur vidéo pour TikTok (Android natif)

Application Android **native** (Kotlin + Jetpack Compose + CameraX) qui transforme ton téléphone en
studio de tournage : tu colles ton texte, l'application le découpe en phrases, affiche chaque
phrase par-dessus l'aperçu caméra, et enregistre ta vidéo avec le son — **100 % hors ligne**, sans
compte, sans serveur, sans filigrane.

> **État du projet : compilé et testé.** L'APK debug et l'APK release (R8 activé, non signé par
> défaut) se construisent avec succès ; les 18 tests unitaires de la logique du téléprompteur et du
> découpage en phrases passent. Voir la section « Ce qui a été vérifié / ce qui doit être testé sur
> un téléphone ».

---

## 1. Fonctionnalités

### Gestion des textes
- Créer, modifier, dupliquer, supprimer un texte, avec titre.
- Coller depuis le presse-papiers en un geste (le titre est déduit automatiquement).
- Réception d'un texte partagé depuis une autre application (« Partager vers ScriptCam »).
- Stockage local (JSON dans l'espace privé de l'app), disponible hors ligne.
- Aperçu du découpage automatique en phrases avant le tournage.
- Estimation de la durée de lecture (mots/minute).

### Découpage en phrases
- Découpe sur `.` `!` `?` `…` `;`, y compris les répétitions (`...`, `?!`).
- Ne coupe pas sur les abréviations courantes (`M.`, `Dr.`, `etc.`, initiales, nombres).
- Regroupe les fragments trop courts pour rester lisible à l'écran.
- Respecte les paragraphes.

### Téléprompteur (fonction centrale)
- **Trois modes** : `Intelligent` (par défaut), `Automatique`, `Manuel`.
  - *Intelligent* : durée calculée phrase par phrase selon le nombre de mots et le rythme choisi
    (mots/minute), avec pause, précédent et suivant immédiats **sans couper l'enregistrement**.
  - *Automatique* : durée fixe réglable par phrase.
  - *Manuel* : la phrase ne change qu'à l'appui sur « suivant ».
- Transition douce (fondu + glissement) entre les phrases.
- Surlignage progressif des mots déjà énoncés (repère de rythme discret).
- Commandes : phrase précédente, phrase suivante, lecture/pause, recommencer, vitesse.
- Indicateur « Phrase 4 sur 15 » + barre de progression globale.
- Affichage de **1 à 3 phrases** simultanément (1 par défaut).

### Personnalisation
Taille du texte, couleur du texte, couleur et transparence du fond, position verticale
(proportionnelle à l'écran, pour ne pas masquer le visage), espacement des lignes, vitesse,
nombre de phrases, orientation (vertical 9:16 / horizontal / auto), texte en miroir, compte à
rebours 0/3/5 s, écran maintenu allumé.

### Caméra et enregistrement
- Aperçu en direct, bascule caméra frontale ↔ arrière.
- **Zoom** : pincement à deux doigts sur l'image, ou glissière dédiée dans le studio
  (du zoom minimal de l'objectif jusqu'à son zoom maximal), avec retour à 1× en un appui.
- **Lumière** : bouton torche dans la barre du haut.
  - Caméra arrière : torche matérielle continue, visible dans l'enregistrement.
  - Caméra frontale : la plupart des téléphones n'ont pas de flash frontal ; l'application
    bascule alors sur une **lampe d'appoint** (voile blanc sur l'écran) qui éclaire le visage.
    Cette lumière d'écran n'apparaît pas dans le fichier vidéo.
- **Orientation cohérente** : la vidéo est enregistrée **en miroir pour la caméra frontale**
  (`MirrorMode.MIRROR_MODE_ON_FRONT_ONLY`), donc identique à ce que tu vois pendant le tournage.

### Faible luminosité (vidéo sombre de nuit)
Trois leviers, du plus efficace au plus risqué :
1. **Mode basse lumière** (bouton soleil dans le studio, ou Paramètres) : bascule l'enregistrement
   en **720p**. Un capteur qui produit moins de pixels « binne » ses photosites et expose plus
   longtemps → image nettement plus lumineuse, résolution réduite. C'est le levier le plus utile
   sur un téléphone de gamme modeste.
2. **Luminosité de l'image** (réglages rapides du studio) : compensation d'exposition CameraX
   (`setExposureCompensationIndex`), de « Auto » jusqu'à +2 EV environ, bornée à la plage réelle
   du capteur. Éclaircit, mais allonge l'exposition → risque de flou de mouvement et de grain.
3. **Photo lumineuse** (icône appareil photo) : prend une image fixe qui peut exposer beaucoup
   plus longtemps que 30 images/seconde. La photo et la vidéo restent deux fichiers distincts ;
   les fusionner demanderait un transcodage complet (Media3 Transformer), volontairement exclu
   pour garder l'application légère.
- Le **verrouillage du framerate** (30/30 plutôt que 15-30) laisse aussi le capteur allonger son
  temps d'exposition en basse lumière.
- Enregistrement vidéo + audio via CameraX, **1080p (FHD) demandé avec repli automatique** vers la
  meilleure qualité disponible (téléphones de gamme modeste).
- Compte à rebours 3 s avant le départ, chronomètre, pause / reprise, arrêt.
- Vidéo écrite **directement dans la galerie** (`Movies/ScriptCam`) sur Android 10+ ; sur Android
  7-9 le fichier est publié dans la galerie via MediaScanner.
- Gestion des erreurs (stockage plein, encodage, source interrompue…) affichée à l'écran.
- Aucun filigrane.

### Après le tournage
- Écran d'aperçu avec lecteur vidéo, détails (durée, taille, date, emplacement).
- **Partage Android** vers TikTok, Instagram, WhatsApp, Google Photos… (FileProvider + grant).
- Ouverture dans la galerie, suppression, nouveau tournage.
- Bibliothèque locale : textes + vidéos (lecture, partage, suppression).

---

## 2. Écrans

| # | Écran | Rôle |
|---|-------|------|
| 1 | **Accueil** | Reprendre un texte, créer un texte, derniers tournages |
| 2 | **Éditeur** | Coller/écrire/modifier/enregistrer, aperçu des phrases |
| 3 | **Préparation** | Caméra, orientation, mode de défilement, réglages, aperçu du rendu |
| 4 | **Studio** | Aperçu caméra plein écran, téléprompteur, commandes, REC, chrono |
| 5 | **Aperçu vidéo** | Lecture, partage, supprimer, recommencer |
| 6 | **Bibliothèque** | Textes et vidéos enregistrées |
| 7 | **Paramètres** | Préférences du téléprompteur et de l'application |

---

## 3. Architecture technique

```
app/src/main/java/com/scriptcam/app/
├── MainActivity.kt           Activité unique (Compose) + partage entrant
├── ScriptCamApp.kt           Application + conteneur de dépendances minimal
├── domain/
│   ├── Models.kt             ScriptText, Recording, ScrollMode
│   └── SentenceSplitter.kt   Découpage du texte en phrases (pur, testé)
├── prompt/
│   └── PromptEngine.kt       Moteur du téléprompteur (pur, testé, sans Android)
├── camera/
│   └── CameraController.kt   CameraX : preview, bascule, REC, pause, galerie
├── data/
│   ├── LocalStore.kt         Textes + index vidéos (JSON atomique, StateFlow)
│   ├── SettingsRepository.kt Réglages persistants (DataStore Preferences)
│   └── VideoExporter.kt      Partage, suppression, FileProvider, MediaStore
└── ui/
    ├── AppState.kt           AppViewModel racine + session de tournage
    ├── AppNavigation.kt      Navigation Compose (7 destinations)
    ├── components/           Design system, overlay du prompteur, réglages partagés
    ├── screens/              Les 7 écrans
    └── theme/                Palette « studio nuit » (Material 3, sombre)
```

**Choix structurants**

- **Kotlin + Compose + CameraX** : pas de React Native/Expo. L'objectif exigeait une superposition
  texte/caméra fiable, un enregistrement avec pause/reprise et une qualité adaptée ; ce sont
  exactement les points faibles d'Expo (`expo-camera` enregistre sans pause/reprise ni contrôle de
  qualité fin, et la vue caméra ne peut pas être composée librement dans l'arbre Compose). Le natif
  donne aussi un APK de ~2,2 Mo (release / R8) contre ~50 Mo pour une app React Native.
- **Moteur du téléprompteur isolé du framework** (`PromptEngine`) : il ne connaît ni Android ni
  Compose, reçoit le temps écoulé via `tick(deltaMs)`. Le studio le nourrit avec une boucle
  `withFrameMillis` (une itération par image affichée) → défilement fluide, testable en JUnit.
- **Pas de Room / KSP** : deux fichiers JSON écrits de façon atomique. Démarrage plus rapide, zéro
  processeur d'annotations, APK plus léger — important sur un téléphone de gamme modeste.
- **DataStore Preferences** pour les réglages (lecture réactive en `Flow`).
- **Aucune permission de stockage** sur Android 10+ : MediaStore suffit. `WRITE_EXTERNAL_STORAGE`
  n'est déclarée qu'avec `maxSdkVersion="28"`.
- **Aucune permission Internet** dans le manifeste : l'application ne peut techniquement pas
  envoyer de données.

---

## 4. Compiler et installer l'APK

### Option A — GitHub Actions (recommandé, aucune installation locale)

Un workflow est fourni : `.github/workflows/android-build.yml`.

1. Pousser le dépôt sur GitHub.
2. Onglet **Actions → Build APK → Run workflow** (ou simplement pousser sur `main`).
3. À la fin du job, télécharger l'artefact **`scriptcam-apk`** : il contient
   `app-debug.apk` (installable directement) et, si demandé, `app-release.apk`.

Pour un build **release signé**, ajouter ces *secrets* du dépôt
(*Settings → Secrets and variables → Actions*) :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | keystore encodé : `base64 -w0 scriptcam.jks` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias de la clé |
| `KEY_PASSWORD` | mot de passe de la clé |

Sans keystore, l'APK release est produit **non signé** (utilisable seulement après signature
manuelle avec `apksigner`).

### Option B — En local

Prérequis : JDK 17 ou 21, Android SDK (plateforme 35, build-tools 35).

```bash
git clone <ton-depot> ScriptCam && cd ScriptCam
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # chemin de ton SDK
./gradlew assembleDebug          # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests unitaires
./gradlew assembleRelease        # APK release minifié (R8)
# ou installer directement sur un téléphone branché en USB (débogage activé) :
./gradlew installDebug
```

Créer un keystore et signer l'APK release :

```bash
keytool -genkeypair -v -keystore scriptcam.jks -alias scriptcam \
        -keyalg RSA -keysize 2048 -validity 10000

cat > keystore.properties <<EOF
storeFile=../scriptcam.jks
storePassword=****
keyAlias=scriptcam
keyPassword=****
EOF
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

`keystore.properties` et `*.jks` sont ignorés par `.gitignore`.

---

## 5. Versions utilisées

| Composant | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.11.1 (wrapper inclus) |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.11.00 (Material 3) |
| CameraX | 1.4.1 |
| Navigation Compose | 2.8.4 |
| DataStore | 1.1.1 |
| minSdk / targetSdk / compileSdk | 24 / 35 / 35 |
| JDK du build | 17 (CI : Temurin 21) |

---

## 6. Ce qui a été vérifié / ce qui doit être testé sur un téléphone

**Vérifié dans l'environnement de développement (Linux, sans appareil) :**

- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**, APK produit (~19 Mo).
- `./gradlew assembleRelease` → **BUILD SUCCESSFUL** avec R8 + resource shrinking (APK ~2,2 Mo).
- `./gradlew testDebugUnitTest` → **18/18 tests réussis** (découpage en phrases, moteur du
  téléprompteur : modes, pause, précédent/suivant, fin de script, progression, surlignage).
- Manifeste, permissions, FileProvider, règles R8, ressources de l'icône adaptative.

**Non testable sans matériel (à valider sur ton téléphone) :** le rendu réel de la caméra, la
qualité effective de l'encodage (1080p selon l'appareil), le partage vers TikTok, et l'ergonomie du
texte superposé à ton cadrage. Les chemins critiques de ces parties sont écrits défensivement
(repli de qualité, repli sans audio si le micro est refusé, messages d'erreur explicites,
publication galerie spécifique Android 7-9).

**Contraintes connues :**

1. **Pause d'enregistrement** : dépend du support caméscope de l'appareil. Sur un appareil qui ne
   la supporte pas, CameraX lève une erreur explicite affichée à l'écran ; le studio continue de
   fonctionner (la pause du téléprompteur, elle, reste toujours disponible).
2. **Changement de caméra pendant l'enregistrement** : volontairement bloqué (CameraX ne permet pas
   de changer de capteur au milieu d'un fichier). Le message l'indique à l'écran.
3. **Miroir** : l'aperçu frontal est un miroir (comme un miroir réel) ; l'enregistrement est
   désormais aligné sur cet aperçu. Si tu préfères une vidéo non inversée, c'est le comportement
   `MirrorMode.MIRROR_MODE_OFF` qu'il faut changer dans `CameraController.bindInternal`.
4. **Torche frontale** : dépend du matériel. Sur la plupart des téléphones il n'existe aucun
   flash frontal ; la lampe d'appoint écran est alors proposée (elle éclaire mais n'est pas
   enregistrée). Le zoom, lui, est numérique au-delà de l'objectif natif.
5. **Basse lumière** : la luminosité d'une vidéo dépend d'abord du capteur. Sur un téléphone
   d'entrée de gamme, le mode 720p et l'exposition +EV améliorent la situation mais ne
   remplacent pas un éclairage (fenêtre, lampe, ring light). La torche arrière aide réellement ;
   la lampe d'appoint frontale éclaire la scène, pas le capteur.
6. **Format 9:16** : l'orientation verticale est forcée dans le studio ; le fichier produit est
   celui du capteur dans cette orientation (1080 × 1920 sur la plupart des téléphones). Pour un
   recadrage strict 9:16 sur tous les appareils, il faudrait un transcodage (MediaCodec/Media3
   Transformer) — non inclus ici pour garder l'app légère et l'enregistrement instantané.
7. **Android 7-9** : l'enregistrement passe par un fichier privé publié dans la galerie ; le
   partage utilise FileProvider.

---

## 7. Vie privée

Aucune connexion réseau, aucun compte, aucun analytics, aucune publicité, aucun filigrane.
Textes et index des vidéos restent dans le stockage privé de l'application ; les vidéos sont
écrites dans la galerie du téléphone (`Movies/ScriptCam`).

## 8. Licence

MIT — voir `LICENSE`.
