# Who Called — bloqueur d'appels indésirables

> Free, anonymous and open-source call blocker for France (Android · iOS · Web). English speakers: the apps and docs are French-first, the code and comments are in English.

**Who Called** bloque le démarchage téléphonique, les arnaques et le spam **avant que ça sonne**, grâce à la liste officielle des préfixes de démarchage (ARCEP) et aux signalements anonymes de la communauté.

- 🛡️ **Blocage avant la sonnerie** — filtrage local sur l'appareil (rôle « Call screening » sur Android, extension CallKit sur iOS)
- 🔍 **Vérification de numéro** — indice de spam 0–100, catégorie dominante, derniers signalements
- 🚩 **Signalement anonyme en 5 secondes** — sans compte, chaque signalement protège toute la communauté
- ✉️ **Bouclier SMS** — masque les SMS d'arnaque (faux colis, CPF…) sans lire vos messages
- 🎮 **Deux mini-jeux** — DEFENSE (arcade) et TRACE (puzzle de déduction), défi quotidien et classement anonyme
- 🔒 **Vie privée d'abord** — aucun compte, aucune pub, aucun traceur, données hébergées dans l'UE

Site : [who-called.com](https://www.who-called.com) · [Politique de confidentialité](https://www.who-called.com/fr-FR/privacy) · [Support](https://www.who-called.com/fr-FR/support)

## Structure du monorepo

| Dossier | Contenu |
|---|---|
| `android/` | Application Android (Kotlin, Jetpack Compose, CallScreeningService) |
| `ios/` | Application iOS (SwiftUI, extensions CallKit Call Directory + Message Filter) |
| `web/` | Site who-called.com (Next.js, fr-FR / en-US) |
| `backend/` | API communautaire (Node/Express + Prisma/PostgreSQL) : signalements, scoring, listes, jeux |
| `infra/` | Manifests k3s (Hetzner) et workflows de déploiement |
| `store/` | Générateur des visuels App Store / Play Store (SVG → PNG) |
| `scripts/`, `tools/` | Outils de build et de publication de la liste embarquée |

## Démarrage rapide

```bash
# Backend (nécessite PostgreSQL, voir backend/.env.example)
cd backend && pnpm install && pnpm dev

# Web
cd web && pnpm install && pnpm dev

# Android
cd android && ./gradlew :app:assembleDebug

# iOS : ouvrir ios/ dans Xcode
```

Chaque sous-projet a son propre README avec les détails.

## Confidentialité & sécurité

Le principe : **minimisation stricte**. Pas de compte, pas de donnée personnelle ; seuls partent vers l'API les signalements volontaires, les scores de jeux et un UUID anonyme. Le détail est dans la [politique de confidentialité](https://www.who-called.com/fr-FR/privacy). Les secrets (base de données, signature des tokens de liste) ne vivent qu'en variables d'environnement — rien dans ce dépôt.

Vous avez trouvé une faille ? Écrivez à `privacy@who-called.com`.

## Contribuer

Issues et pull requests bienvenues — signalements de bugs, traductions, idées de fonctionnalités. Pour signaler un numéro, pas besoin de GitHub : l'app suffit 😉

## Licence

[GPL-3.0](LICENSE) — vous pouvez auditer, forker et améliorer ce code, mais tout fork distribué doit rester open source sous la même licence.
