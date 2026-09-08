import type { Locale } from "./dictionaries";
import { config } from "@/lib/config";

// Company / publisher info — single source of truth.
export const company = {
  name: "DEVFI",
  form: "SASU (société par actions simplifiée unipersonnelle)",
  address: "60 rue François Ier, 75008 Paris, France",
  director: "Maxime Ramos",
  siren: "987 710 027",
  siret: "987 710 027 00010",
  rcs: "987 710 027 R.C.S. Paris",
  vat: "FR89987710027",
  capital: "100,00 €",
  created: "15/03/2024",
  host: "Hetzner Online GmbH, Allemagne (Union européenne)",
  privacyEmail: "privacy@who-called.com",
  contactEmail: "contact@who-called.com",
};

type Section = { title: string; body: string };
type LegalDoc = { title: string; updated: string; intro?: string; sections: Section[] };

const privacyFr: LegalDoc = {
  title: "Politique de confidentialité",
  updated: "5 septembre 2026",
  intro:
    "Cette politique couvre le site who-called.com et les applications mobiles Who Called " +
    "pour Android (com.devfi.whocalled) et iOS (bientôt disponible), éditées par " +
    company.name +
    ". Nous appliquons une logique de minimisation stricte : nous ne collectons que le strict nécessaire au filtrage des appels indésirables. Pas de compte, pas de nom, pas d'email, pas de publicité, pas de traceur tiers. Nous ne vendons ni ne partageons aucune donnée.",
  sections: [
    {
      title: "1. Responsable du traitement",
      body: `${company.name} (${company.form}), ${company.address}. Contact vie privée : ${company.privacyEmail}.`,
    },
    {
      title: "2. Ce que nous ne collectons jamais",
      body: "Aucun compte n'est requis et aucune donnée d'identité n'est collectée : ni nom, ni adresse, ni email, ni numéro de téléphone personnel, ni contacts, ni position, ni contenu d'appels ou de messages. Les applications ne contiennent aucun SDK publicitaire ni outil de mesure d'audience tiers. Le code source est public et vérifiable.",
    },
    {
      title: "3. Données stockées sur votre appareil (jamais envoyées)",
      body: "Vos règles personnelles (numéros bloqués/autorisés), vos réglages, la copie locale de la liste de blocage, le journal local des appels vus par le filtre et des SMS masqués, votre progression dans les mini-jeux (séries, records locaux) et un identifiant technique anonyme (UUID aléatoire, généré sur l'appareil). Ces données restent sur l'appareil et disparaissent à la désinstallation.",
    },
    {
      title: "4. Données envoyées à nos serveurs",
      body: "Signalement d'un numéro (action volontaire) : le numéro signalé au format international, votre vote (indésirable/légitime), une catégorie facultative, l'identifiant anonyme de l'appareil, la date et la langue. Vérification d'un numéro : le numéro recherché est transmis pour interroger la base, accompagné de l'identifiant anonyme de l'appareil uniquement pour vous réafficher votre propre vote ; cette requête n'est pas conservée. Synchronisation de la liste : la requête contient uniquement le pays choisi et la date de dernière mise à jour — aucun identifiant. Mini-jeux : voir la section 7.",
    },
    {
      title: "5. Application Android — autorisations",
      body: "Filtrage d'appels (rôle système « Applications de filtrage d'appels ») : le numéro de chaque appel entrant est comparé localement à la liste stockée sur l'appareil ; il n'est jamais envoyé à nos serveurs. Journal local : chaque appel entrant vu par le filtre (bloqué, alerté ou autorisé) est consigné sur l'appareil pendant 90 jours, pour retrouver qui a appelé et signaler un numéro en un geste. L'application ne lit pas le journal d'appels du téléphone et ne demande aucune autorisation SMS ni journal d'appels ; seul un numéro que vous choisissez de signaler est transmis. Notifications (POST_NOTIFICATIONS, facultatif) : alertes d'appels suspects, avis d'appels/SMS bloqués et rappel quotidien des mini-jeux. Accès aux notifications (bouclier SMS, opt-in) : lit uniquement les notifications de votre application SMS pour masquer celles provenant de numéros indésirables ; le contenu n'est ni stocké ni transmis. Internet : synchronisation de la liste et envoi de vos signalements.",
    },
    {
      title: "6. Application iOS — autorisations et extensions",
      body: "Extension de blocage d'appels (CallKit Call Directory) : la liste de blocage est fournie au système et évaluée par iOS hors ligne ; l'application ne voit pas vos appels. Extension de filtrage SMS (Message Filter) : la classification se fait entièrement sur l'appareil, sans requête réseau ; iOS empêche par conception l'application d'accéder à vos messages. Extension de partage : ne traite que le texte que vous partagez volontairement pour signaler un numéro. Notifications locales (facultatives) : rappels générés sur l'appareil, sans serveur.",
    },
    {
      title: "7. Mini-jeux et classement anonyme",
      body: "Les applications incluent deux mini-jeux (DEFENSE et TRACE) avec un classement quotidien anonyme. Si vous jouez, sont envoyés : le score, le nombre de vagues/grilles, le jour du défi et l'identifiant anonyme de l'appareil. Aucun pseudonyme ni donnée personnelle — le classement affiche des positions, pas des identités. Les puzzles du jour sont téléchargés depuis nos serveurs sans transmettre de données personnelles.",
    },
    {
      title: "8. Notifications et rappels",
      body: "Toutes les notifications sont générées localement sur l'appareil (aucune notification « push » via un service tiers). Le rappel quotidien des mini-jeux est plafonné à un par jour, ne part jamais si vous avez déjà joué, se désactive automatiquement si vous l'ignorez plusieurs jours de suite, et peut être coupé en un geste depuis la notification ou les réglages.",
    },
    {
      title: "9. Aucun service tiers",
      body: "Aucun SDK publicitaire, aucun réseau social, aucun outil d'analyse tiers, aucun service de push tiers. Les seules communications réseau des applications vont vers notre API (api.who-called.com), hébergée dans l'Union européenne. Aucune donnée n'est transférée hors de l'UE.",
    },
    {
      title: "10. Finalités et bases légales",
      body: "Filtrage anti-spam et amélioration de la liste communautaire : intérêt légitime (article 6(1)(f) RGPD). Fonctions facultatives reposant sur une autorisation système (journal d'appels, notifications, bouclier SMS) : consentement, révocable à tout moment dans les réglages du système. Classements de jeux : intérêt légitime, données pseudonymisées.",
    },
    {
      title: "11. Conservation",
      body: "Les signalements sont conservés au maximum 365 jours puis supprimés automatiquement. Un numéro qui n'a plus aucun signalement est retiré de la base communautaire. Les scores de jeux sont liés au seul identifiant anonyme et supprimés avec vos données. Les données locales restent sur votre appareil jusqu'à désinstallation.",
    },
    {
      title: "12. Hébergement et sécurité",
      body: `Données hébergées dans l'Union européenne chez ${company.host}. Tous les échanges sont chiffrés (HTTPS/TLS). L'identifiant d'appareil est un UUID aléatoire qui ne permet pas de vous identifier.`,
    },
    {
      title: "13. Vos droits (RGPD) et suppression des données",
      body: `Suppression immédiate et sans justification depuis l'application : Réglages → « Supprimer mes données » — cette action efface de nos serveurs tous vos signalements, vos scores de jeux et l'enregistrement de votre appareil. Vous pouvez aussi demander le retrait d'un numéro vous appartenant ou exercer vos droits (accès, rectification, effacement, opposition) par email : ${company.privacyEmail}. Voir aussi la page « Suppression des données » du site. Réclamation possible auprès de la CNIL (cnil.fr).`,
    },
    {
      title: "14. Enfants",
      body: "L'application n'est pas destinée aux mineurs de moins de 15 ans et ne collecte sciemment aucune donnée les concernant.",
    },
    {
      title: "15. Modifications",
      body: "Toute évolution de cette politique sera publiée sur cette page avec sa date de mise à jour. En cas de changement substantiel, l'application l'indiquera.",
    },
  ],
};

const privacyEn: LegalDoc = {
  title: "Privacy Policy",
  updated: "September 5, 2026",
  intro:
    "This policy covers the who-called.com website and the Who Called mobile apps " +
    "for Android (com.devfi.whocalled) and iOS (coming soon), published by " +
    company.name +
    ". We apply strict data minimization: we only collect what is strictly necessary to filter unwanted calls. No account, no name, no email, no ads, no third-party trackers. We never sell or share your data.",
  sections: [
    {
      title: "1. Data controller",
      body: `${company.name} (${company.form}), ${company.address}. Privacy contact: ${company.privacyEmail}.`,
    },
    {
      title: "2. What we never collect",
      body: "No account is required and no identity data is collected: no name, address, email, personal phone number, contacts, location, or content of calls or messages. The apps contain no advertising SDK and no third-party analytics. The source code is public and auditable.",
    },
    {
      title: "3. Data stored on your device (never sent)",
      body: "Your personal rules (blocked/allowed numbers), your settings, the local copy of the blocklist, the local log of calls seen by the filter and hidden SMS, your mini-game progress (streaks, local records) and an anonymous technical identifier (random UUID generated on the device). This data stays on the device and is removed when you uninstall.",
    },
    {
      title: "4. Data sent to our servers",
      body: "Reporting a number (voluntary action): the reported number in international format, your vote (spam/legitimate), an optional category, the device's anonymous identifier, the date and the language. Checking a number: the number you search is sent to query the database, together with the device's anonymous identifier solely to show you your own vote; this request is not stored. List sync: the request only carries the selected country and the last-update date — no identifier. Mini-games: see section 7.",
    },
    {
      title: "5. Android app — permissions",
      body: "Call screening (system role “Call screening apps”): each incoming number is checked locally against the list stored on the device; it is never sent to our servers. Local journal: every incoming call seen by the filter (blocked, warned or allowed) is recorded on the device for 90 days, so you can see who called and report a number in one tap. The app does not read the phone's call log and requests no SMS or call-log permission; only a number you choose to report is transmitted. Notifications (POST_NOTIFICATIONS, optional): suspicious-call alerts, blocked call/SMS notices and the daily mini-game reminder. Notification access (SMS shield, opt-in): only reads notifications from your SMS app to hide those coming from unwanted numbers; content is neither stored nor transmitted. Internet: list sync and sending your reports.",
    },
    {
      title: "6. iOS app — permissions and extensions",
      body: "Call blocking extension (CallKit Call Directory): the blocklist is handed to the system and evaluated by iOS offline; the app cannot see your calls. SMS filter extension (Message Filter): classification happens entirely on-device, with no network request; iOS prevents the app from accessing your messages by design. Share extension: only processes the text you voluntarily share to report a number. Local notifications (optional): reminders generated on the device, with no server involved.",
    },
    {
      title: "7. Mini-games and anonymous leaderboard",
      body: "The apps include two mini-games (DEFENSE and TRACE) with an anonymous daily leaderboard. If you play, the following is sent: the score, the number of waves/grids, the challenge day and the device's anonymous identifier. No nickname and no personal data — the leaderboard shows positions, not identities. Daily puzzles are downloaded from our servers without transmitting personal data.",
    },
    {
      title: "8. Notifications and reminders",
      body: "All notifications are generated locally on the device (no push notifications through a third-party service). The daily mini-game reminder is capped at one per day, never fires if you already played, mutes itself automatically if you ignore it several days in a row, and can be turned off in one tap from the notification or in the settings.",
    },
    {
      title: "9. No third-party services",
      body: "No advertising SDK, no social network, no third-party analytics, no third-party push service. The apps only communicate with our API (api.who-called.com), hosted in the European Union. No data is transferred outside the EU.",
    },
    {
      title: "10. Purposes and legal bases",
      body: "Spam filtering and improving the community list: legitimate interest (Article 6(1)(f) GDPR). Optional features backed by a system permission (call log, notifications, SMS shield): consent, revocable at any time in the system settings. Game leaderboards: legitimate interest, pseudonymized data.",
    },
    {
      title: "11. Retention",
      body: "Reports are kept for at most 365 days, then automatically deleted. A number left with no reports is removed from the community database. Game scores are tied to the anonymous identifier only and deleted together with your data. Local data stays on your device until uninstallation.",
    },
    {
      title: "12. Hosting and security",
      body: `Data is hosted in the European Union at ${company.host}. All traffic is encrypted (HTTPS/TLS). The device identifier is a random UUID that cannot identify you.`,
    },
    {
      title: "13. Your rights (GDPR) and data deletion",
      body: `Immediate, no-questions-asked deletion from the app: Settings → "Delete my data" — this erases from our servers all your reports, your game scores and your device record. You can also request the removal of a number you own, or exercise your rights (access, rectification, erasure, objection) by email: ${company.privacyEmail}. See also the website's “Data deletion” page. You may lodge a complaint with your data protection authority (in France: CNIL, cnil.fr).`,
    },
    {
      title: "14. Children",
      body: "The app is not intended for children under 15 and does not knowingly collect any data about them.",
    },
    {
      title: "15. Changes",
      body: "Any change to this policy will be published on this page with its update date. In case of a substantial change, the app will say so.",
    },
  ],
};

const policyFr: LegalDoc = {
  title: "Mentions légales & conditions",
  updated: "27 juillet 2026",
  sections: [
    {
      title: "1. Éditeur",
      body: `${company.name}, ${company.form}. Siège : ${company.address}. SIREN : ${company.siren}. SIRET : ${company.siret}. RCS : ${company.rcs}. TVA : ${company.vat}. Capital social : ${company.capital}. Directeur de la publication : ${company.director}. Contact : ${company.contactEmail}.`,
    },
    {
      title: "2. Hébergement",
      body: `Site et API hébergés par ${company.host}.`,
    },
    {
      title: "3. Objet",
      body: `Who Called est une application gratuite de filtrage des appels indésirables, basée sur la liste officielle des préfixes de démarchage publiée par l'ARCEP (Autorité de régulation des communications électroniques, des postes et de la distribution de la presse) et sur des signalements communautaires anonymes. Elle ne passe pas d'appels. Source officielle des préfixes : ${config.links.arcepSource}`,
    },
    {
      title: "4. Indépendance et non-affiliation",
      body: `Who Called est un service indépendant édité par ${company.name}. Il n'est pas une application officielle : il n'est affilié ni à l'ARCEP ni à aucune entité gouvernementale, et ne représente aucun organisme public. Les informations issues de sources officielles sont reproduites à titre informatif, avec un lien vers leur source d'origine.`,
    },
    {
      title: "5. Conditions d'utilisation",
      body: "L'application est fournie gratuitement, « en l'état », sans garantie de résultat. En signalant un numéro, vous vous engagez à le faire de bonne foi. Vous restez responsable de la décision de répondre ou non à un appel.",
    },
    {
      title: "6. Liste communautaire",
      body: `Les scores sont calculés automatiquement et ne constituent pas un jugement. Toute personne responsable d'un numéro peut demander son retrait à ${company.privacyEmail}.`,
    },
    {
      title: "7. Propriété intellectuelle",
      body: `La marque « Who Called » et les contenus appartiennent à ${company.name}.`,
    },
    {
      title: "8. Droit applicable",
      body: "Droit français. Tribunaux compétents français.",
    },
  ],
};

const policyEn: LegalDoc = {
  title: "Legal notice & terms",
  updated: "July 27, 2026",
  sections: [
    {
      title: "1. Publisher",
      body: `${company.name}, ${company.form}. Registered office: ${company.address}. SIREN: ${company.siren}. SIRET: ${company.siret}. RCS: ${company.rcs}. VAT: ${company.vat}. Share capital: ${company.capital}. Publication director: ${company.director}. Contact: ${company.contactEmail}.`,
    },
    {
      title: "2. Hosting",
      body: `Website and API hosted by ${company.host}.`,
    },
    {
      title: "3. Purpose",
      body: `Who Called is a free app that filters unwanted calls, based on the official telemarketer prefix list published by ARCEP (the French regulator of electronic communications) and anonymous community reports. It does not place calls. Official source of the prefixes: ${config.links.arcepSource}`,
    },
    {
      title: "4. Independence and non-affiliation",
      body: `Who Called is an independent service published by ${company.name}. It is not an official app: it is not affiliated with ARCEP or any government entity, and does not represent any public body. Information coming from official sources is reproduced for information purposes, with a link to its original source.`,
    },
    {
      title: "5. Terms of use",
      body: 'The app is provided free of charge, "as is", with no guarantee of results. When reporting a number, you agree to do so in good faith. You remain responsible for the decision to answer a call or not.',
    },
    {
      title: "6. Community list",
      body: `Scores are computed automatically and do not constitute a judgment. Anyone responsible for a number can request its removal at ${company.privacyEmail}.`,
    },
    {
      title: "7. Intellectual property",
      body: `The "Who Called" brand and contents belong to ${company.name}.`,
    },
    {
      title: "8. Governing law",
      body: "French law. French courts have jurisdiction.",
    },
  ],
};

const supportFr: LegalDoc = {
  title: "Aide & support",
  updated: "23 juillet 2026",
  intro:
    "Une question, un problème avec l'application Android ou iOS, ou avec le site ? Cette page rassemble les réponses aux questions les plus fréquentes et les moyens de nous contacter.",
  sections: [
    {
      title: "Nous contacter",
      body: `Email : ${company.contactEmail}. Nous répondons généralement sous 48 h ouvrées, en français ou en anglais. Pour toute question liée aux données personnelles : ${company.privacyEmail}.`,
    },
    {
      title: "Le blocage ne fonctionne pas (Android)",
      body: "Vérifiez que Who Called est bien défini comme application de filtrage d'appels : ouvrez l'application, écran Accueil, et suivez l'invite « Activer la protection ». Vérifiez aussi que la liste est à jour (bouton « Mettre à jour la liste »).",
    },
    {
      title: "Le blocage ne fonctionne pas (iOS)",
      body: "Vérifiez que l'extension est activée : Réglages iOS → Applications → Téléphone → Blocage et identification des appels → activez Who Called. Après une mise à jour de la liste, iOS peut mettre quelques instants à recharger l'extension.",
    },
    {
      title: "Supprimer mes données",
      body: "Dans l'application : Réglages → « Supprimer mes données ». L'action est immédiate et efface vos signalements, vos scores de jeux et l'enregistrement de votre appareil de nos serveurs. Détails sur la page « Suppression des données ».",
    },
    {
      title: "Mon numéro est signalé à tort",
      body: `Si vous êtes responsable d'un numéro listé et contestez son score, écrivez à ${company.privacyEmail} : nous retirons le numéro après vérification. Les préfixes issus de la liste officielle ARCEP ne peuvent pas être retirés.`,
    },
    {
      title: "Signaler un bug ou proposer une idée",
      body: "Le code est open source : vous pouvez ouvrir un ticket sur le dépôt public (lien « Code source » en bas de page) ou nous écrire par email.",
    },
  ],
};

const supportEn: LegalDoc = {
  title: "Help & support",
  updated: "July 23, 2026",
  intro:
    "A question or an issue with the Android or iOS app, or with the website? This page gathers answers to the most frequent questions and the ways to reach us.",
  sections: [
    {
      title: "Contact us",
      body: `Email: ${company.contactEmail}. We usually reply within 48 business hours, in French or English. For anything related to personal data: ${company.privacyEmail}.`,
    },
    {
      title: "Blocking does not work (Android)",
      body: "Check that Who Called is set as the call screening app: open the app, Home screen, and follow the “Enable protection” prompt. Also make sure the list is up to date (“Update the list” button).",
    },
    {
      title: "Blocking does not work (iOS)",
      body: "Check that the extension is enabled: iOS Settings → Apps → Phone → Call Blocking & Identification → enable Who Called. After a list update, iOS may take a moment to reload the extension.",
    },
    {
      title: "Delete my data",
      body: "In the app: Settings → “Delete my data”. The action is immediate and erases your reports, your game scores and your device record from our servers. Details on the “Data deletion” page.",
    },
    {
      title: "My number is wrongly reported",
      body: `If you are responsible for a listed number and dispute its score, write to ${company.privacyEmail}: we remove the number after verification. Prefixes from the official ARCEP list cannot be removed.`,
    },
    {
      title: "Report a bug or suggest an idea",
      body: "The code is open source: you can open an issue on the public repository (“Source code” link in the footer) or email us.",
    },
  ],
};

const deletionFr: LegalDoc = {
  title: "Suppression de vos données",
  updated: "23 juillet 2026",
  intro:
    "Who Called ne demande aucun compte : les seules données conservées sur nos serveurs sont vos signalements, vos scores de jeux et un identifiant anonyme d'appareil. Voici comment tout supprimer.",
  sections: [
    {
      title: "1. Depuis l'application (recommandé, immédiat)",
      body: "Ouvrez Who Called → Réglages → « Supprimer mes données ». Cette action efface définitivement de nos serveurs tous les signalements envoyés depuis votre appareil, vos scores de jeux et l'enregistrement de l'appareil. Aucune justification n'est demandée et l'application reste utilisable ensuite.",
    },
    {
      title: "2. Si vous avez déjà désinstallé l'application",
      body: `Écrivez à ${company.privacyEmail}. L'identifiant anonyme ayant été supprimé avec l'application, indiquez-nous si possible le ou les numéros que vous aviez signalés et la période approximative : nous procédons à la vérification puis à l'effacement.`,
    },
    {
      title: "3. Données locales",
      body: "Toutes les données stockées sur l'appareil (règles, journal des appels filtrés, réglages, progression des jeux) sont supprimées par le système lors de la désinstallation de l'application.",
    },
    {
      title: "4. Retrait d'un numéro de la base communautaire",
      body: `Si vous êtes responsable d'un numéro listé, demandez son retrait à ${company.privacyEmail}. Les préfixes de la liste officielle ARCEP ne sont pas concernés.`,
    },
    {
      title: "5. Délais",
      body: "Suppression via l'application : immédiate. Par email : sous 30 jours au plus, conformément au RGPD (en pratique quelques jours ouvrés).",
    },
  ],
};

const deletionEn: LegalDoc = {
  title: "Deleting your data",
  updated: "July 23, 2026",
  intro:
    "Who Called requires no account: the only data kept on our servers are your reports, your game scores and an anonymous device identifier. Here is how to delete everything.",
  sections: [
    {
      title: "1. From the app (recommended, immediate)",
      body: "Open Who Called → Settings → “Delete my data”. This permanently erases from our servers all reports sent from your device, your game scores and the device record. No justification is asked and the app remains usable afterwards.",
    },
    {
      title: "2. If you already uninstalled the app",
      body: `Write to ${company.privacyEmail}. Since the anonymous identifier was deleted with the app, please tell us if possible which number(s) you reported and the approximate period: we verify, then erase.`,
    },
    {
      title: "3. Local data",
      body: "All data stored on the device (rules, filtered-calls log, settings, game progress) is removed by the system when you uninstall the app.",
    },
    {
      title: "4. Removing a number from the community database",
      body: `If you are responsible for a listed number, request its removal at ${company.privacyEmail}. Prefixes from the official ARCEP list are not affected.`,
    },
    {
      title: "5. Timeframe",
      body: "Deletion through the app: immediate. By email: within 30 days at most, as required by the GDPR (in practice a few business days).",
    },
  ],
};

export function getPrivacy(locale: Locale): LegalDoc {
  return locale === "en" ? privacyEn : privacyFr;
}
export function getPolicy(locale: Locale): LegalDoc {
  return locale === "en" ? policyEn : policyFr;
}
export function getSupport(locale: Locale): LegalDoc {
  return locale === "en" ? supportEn : supportFr;
}
export function getDataDeletion(locale: Locale): LegalDoc {
  return locale === "en" ? deletionEn : deletionFr;
}
