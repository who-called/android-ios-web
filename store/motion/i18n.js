// Every on-screen string of the promo film. Add a locale by copying the `fr`
// block, translating it, and opening index.html?lang=<code>.
// Phone numbers use ranges reserved for fiction (ARCEP 01 99 00…, US 555-01xx).
// `app` = texts of the real app screens shown in the phone (rendered by store/screens.mjs).
window.WC_I18N = {
  fr: {
    s1: {
      incoming: "Appel entrant…",
      unknown: "Numéro inconnu",
      number: "01 99 00 47 12",
      title: "Encore un démarcheur ?",
    },
    s2: {
      words: ["Démarchage.", "Arnaques.", "Spam."],
      tags: ["Démarchage", "Arnaque CPF", "Faux livreur", "Rénovation", "Assurance", "Panneaux solaires", "Sondage", "Faux conseiller", "Spam"],
      numbers: ["02 61 91 03 88", "03 53 01 72 40", "04 65 71 19 63", "05 36 49 88 21", "01 99 00 31 57", "06 39 98 44 02", "02 61 91 57 16", "03 53 01 26 94"],
    },
    s3: {
      stamp: "BLOQUÉ",
      titleA: "Bloqué",
      titleB: "avant même de sonner.",
      chip: "Liste officielle ARCEP + signalements de la communauté",
    },
    s4: {
      kicker: "Vérifier",
      title: "Qui m'a appelé\u00a0?",
      sub: "Un indice de spam de 0 à 100, en un instant.",
    },
    s5: {
      kicker: "Signaler",
      title: "5 secondes pour protéger tout le monde.",
      plus: "+1",
    },
    s6: {
      kicker: "Bouclier SMS",
      title: "Les SMS d'arnaque, masqués.",
      sub: "Sans jamais lire vos messages.",
    },
    // App screens (store/screens.mjs). French defaults = the store visuals; only the number differs.
    app: {
      lookup: { number: "+33 1 99 00 82 54" },
      report: { number: "+33 1 99 00 82 54" },
    },
    s7: {
      words: ["Gratuit.", "Anonyme.", "Open source."],
      line: "Sans compte. Sans pub. Sans traceur.",
    },
    s8: {
      name: "Who Called",
      tagline: "Le bon appel passe. Le spam, non.",
      url: "who-called.com",
      platforms: ["Android", "iOS"],
    },
  },

  en: {
    s1: {
      incoming: "Incoming call…",
      unknown: "Unknown number",
      number: "(555) 010-4712",
      title: "Another telemarketer?",
    },
    s2: {
      words: ["Telemarketing.", "Scams.", "Spam."],
      tags: ["Telemarketing", "Tax scam", "Fake courier", "Home repair", "Insurance", "Solar panels", "Survey", "Fake bank", "Spam"],
      numbers: ["(555) 010-0388", "(555) 012-7240", "(555) 014-1963", "(555) 016-8821", "(555) 018-3157", "(555) 011-4402", "(555) 013-5716", "(555) 015-2694"],
    },
    s3: {
      stamp: "BLOCKED",
      titleA: "Blocked",
      titleB: "before it even rings.",
      chip: "Official do-not-call lists + community reports",
    },
    s4: {
      kicker: "Check",
      title: "Who called me?",
      sub: "A 0–100 spam score, in an instant.",
    },
    s5: {
      kicker: "Report",
      title: "5 seconds to protect everyone.",
      plus: "+1",
    },
    s6: {
      kicker: "SMS Shield",
      title: "Scam texts, hidden.",
      sub: "Without ever reading your messages.",
    },
    app: {
      nav: ["Home", "Report", "Log", "Settings"],
      lookup: {
        title: "Check a number", sub: "Block list + community reputation", number: "+1 555-019-8254",
        score: 87, scoreLabel: "spam score", src1: "DNC list", src2: "Community",
        cat: "Telemarketing", catSub: "Most reported category", catCount: "182×", recent: "Latest reports",
        rows: [["Scam", "2 h ago"], ["Telemarketing", "yesterday"], ["Silent call", "3 d ago"]],
      },
      report: {
        title: "Report a number", sub: "Anonymous — no account needed", phoneLabel: "Phone number", number: "+1 555-019-8254",
        spam: "Unwanted", spamIconDx: -48, legit: "Legitimate", legitIconDx: -61, catLabel: "Category",
        chips: ["Telemarketing", "Scam", "Robocall", "Silent call", "Debt collector", "Survey", "Other"], selected: 0,
        anonTitle: "100% anonymous", anonSub: "No account, no personal data.",
        send: "Send report", sendIconDx: -67, sent: "Thanks! Anonymous report sent.",
      },
      sms: {
        title: "SMS Shield", sub: "Filter unwanted texts too", onTitle: "SMS Shield on", onSub: "Unwanted texts are hidden",
        today: "Today", masked: "Unwanted text hidden", filtered: "Filtered",
        spam: [["+1 555-01xx · “Your parcel is on hold,", "tap here to pay $1.99 …”"], ["555-018x · “Final notice: your tax", "refund expires today …”"]],
        legitFrom: "Mom", legitText: "“Still on for Sunday lunch?” · Delivered",
      },
    },
    s7: {
      words: ["Free.", "Anonymous.", "Open source."],
      line: "No account. No ads. No trackers.",
    },
    s8: {
      name: "Who Called",
      tagline: "Good calls ring. Spam doesn't.",
      url: "who-called.com",
      platforms: ["Android", "iOS"],
    },
  },

  es: {
    s1: {
      incoming: "Llamada entrante…",
      unknown: "Número desconocido",
      number: "600 000 471",
      title: "¿Otra vez publicidad?",
    },
    s2: {
      words: ["Publicidad.", "Estafas.", "Spam."],
      tags: ["Publicidad", "Estafa", "Falso repartidor", "Reformas", "Seguros", "Placas solares", "Encuesta", "Falso banco", "Spam"],
      numbers: ["600 000 038", "600 000 724", "600 000 196", "600 000 882", "600 000 315", "600 000 440", "600 000 571", "600 000 269"],
    },
    s3: {
      stamp: "BLOQUEADO",
      titleA: "Bloqueado",
      titleB: "antes de que suene.",
      chip: "Listas oficiales + avisos de la comunidad",
    },
    s4: {
      kicker: "Verificar",
      title: "¿Quién me llamó?",
      sub: "Un índice de spam de 0 a 100, al instante.",
    },
    s5: {
      kicker: "Denunciar",
      title: "5 segundos para proteger a todos.",
      plus: "+1",
    },
    s6: {
      kicker: "Escudo SMS",
      title: "SMS de estafa, ocultos.",
      sub: "Sin leer nunca tus mensajes.",
    },
    app: {
      nav: ["Inicio", "Denunciar", "Registro", "Ajustes"],
      lookup: {
        title: "Verificar un número", sub: "Reputación oficial + comunidad", number: "+34 600 000 825",
        score: 87, scoreLabel: "índice de spam", src1: "Oficial", src2: "Comunidad",
        cat: "Publicidad", catSub: "Categoría más denunciada", catCount: "182×", recent: "Últimos avisos",
        rows: [["Estafa", "hace 2 h"], ["Publicidad", "ayer"], ["Llamada muda", "hace 3 d"]],
      },
      report: {
        title: "Denunciar un número", sub: "Anónimo — sin cuenta", phoneLabel: "Número de teléfono", number: "+34 600 000 825",
        spam: "No deseado", spamIconDx: -55, legit: "Legítimo", legitIconDx: -53, catLabel: "Categoría",
        chips: ["Publicidad", "Estafa", "Robollamada", "Llamada muda", "Cobros", "Encuesta", "Otro"], selected: 0,
        anonTitle: "100 % anónimo", anonSub: "Sin cuenta, sin datos personales.",
        send: "Enviar la denuncia", sendIconDx: -100, sent: "¡Gracias! Aviso anónimo enviado.",
      },
      sms: {
        title: "Escudo SMS", sub: "Filtra también los SMS no deseados", onTitle: "Escudo SMS activo", onSub: "Los SMS no deseados se ocultan",
        today: "Hoy", masked: "SMS no deseado oculto", filtered: "Filtrado",
        spam: [["6xx xxx xxx · «Tu paquete está retenido,", "paga 1,99 € aquí …»"], ["Hacienda · «Último aviso: tu devolución", "caduca hoy …»"]],
        legitFrom: "Mamá", legitText: "«¿Comemos el domingo?» · Entregado",
      },
    },
    s7: {
      words: ["Gratis.", "Anónimo.", "Código abierto."],
      line: "Sin cuenta. Sin anuncios. Sin rastreadores.",
    },
    s8: {
      name: "Who Called",
      tagline: "Las buenas llamadas suenan. El spam, no.",
      url: "who-called.com",
      platforms: ["Android", "iOS"],
    },
  },
};
