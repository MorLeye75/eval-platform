package sn.ept.eval.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutProposition;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.repository.*;
import sn.ept.eval.service.PublicationResultatsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Jeu de données de démonstration complet, en plus des comptes et évaluations ouvertes de {@link DataInitializer} :
 * deux promotions d'étudiants, un deuxième enseignant, des évaluations terminées et publiées (notes variées,
 * statistiques, historique de révision, alertes), une évaluation terminée à corriger et publier pendant la démo,
 * un brouillon et une évaluation à venir. Activé par {@code app.demo.jeu-complet} (profils dev et h2-dev), jamais en
 * production ni dans les autres tests. Créé une seule fois : reconnu au titre du contrôle continu.
 *
 * <p>Les notes suivent les règles de la correction : QCM (points, 0 sans réponse, moins la pénalité sinon), code au
 * prorata des cas de test réussis, questions ouvertes notées par l'enseignant après proposition de l'IA ; note finale
 * jamais sous 0. Les résultats sont publiés par le service de publication, qui envoie les notifications.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo.jeu-complet", havingValue = "true")
public class DonneesDemoInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DonneesDemoInitializer.class);

    static final String TITRE_RESEAUX = "Réseaux — QCM de mi-semestre";
    static final String TITRE_PARTIEL = "Bases de données — Partiel";
    static final String TITRE_CONTROLE = "Programmation — Contrôle continu";
    static final String TITRE_DIC3 = "Systèmes distribués — Examen";
    static final String TITRE_BROUILLON = "Génie logiciel — Patrons de conception";
    static final String TITRE_A_VENIR = "Systèmes d'exploitation — Ordonnancement";

    static final String MOT_DE_PASSE_ETUDIANTS = "etudiant123";

    /** Étudiant de démonstration et son niveau (0 à 1) : il décide de ses réponses, donc de ses notes. */
    private record Profil(String prenom, String nom, double niveau) {}

    private static final List<Profil> DIC2 = List.of(
            new Profil("Fatou", "Sarr", 0.92), new Profil("Moussa", "Diop", 0.55), new Profil("Awa", "Ndiaye", 0.80),
            new Profil("Cheikh", "Ba", 0.35), new Profil("Mariama", "Fall", 0.66), new Profil("Ibrahima", "Sy", 0.45),
            new Profil("Khady", "Gueye", 0.76), new Profil("Ousmane", "Faye", 0.25), new Profil("Aïssatou", "Diallo", 0.86),
            new Profil("Pape", "Mbaye", 0.50), new Profil("Ndeye", "Sow", 0.60));
    private static final double NIVEAU_AMINOU = 0.70;
    private static final List<Profil> DIC3 = List.of(
            new Profil("Serigne", "Kane", 0.82), new Profil("Astou", "Cissé", 0.64), new Profil("Modou", "Thiam", 0.40),
            new Profil("Bineta", "Seck", 0.90), new Profil("Lamine", "Camara", 0.55));

    private final UtilisateurRepository utilisateurRepository;
    private final PromotionRepository promotionRepository;
    private final EvaluationRepository evaluationRepository;
    private final SoumissionRepository soumissionRepository;
    private final AmenagementTempsRepository amenagementTempsRepository;
    private final RevisionNoteRepository revisionNoteRepository;
    private final PublicationResultatsService publicationResultatsService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public DonneesDemoInitializer(UtilisateurRepository utilisateurRepository, PromotionRepository promotionRepository,
                                  EvaluationRepository evaluationRepository, SoumissionRepository soumissionRepository,
                                  AmenagementTempsRepository amenagementTempsRepository,
                                  RevisionNoteRepository revisionNoteRepository,
                                  PublicationResultatsService publicationResultatsService,
                                  PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
        this.utilisateurRepository = utilisateurRepository;
        this.promotionRepository = promotionRepository;
        this.evaluationRepository = evaluationRepository;
        this.soumissionRepository = soumissionRepository;
        this.amenagementTempsRepository = amenagementTempsRepository;
        this.revisionNoteRepository = revisionNoteRepository;
        this.publicationResultatsService = publicationResultatsService;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(String... args) {
        if (evaluationRepository.existsByTitre(TITRE_CONTROLE)) {
            log.info("Jeu de données de démonstration déjà présent.");
            return;
        }
        Boolean cree = transaction.execute(tx -> creer());
        if (!Boolean.TRUE.equals(cree)) return;
        // Publication par le service : mêmes contrôles et mêmes notifications qu'un clic de l'enseignant
        publier(TITRE_RESEAUX, "prof@ept.sn");
        publier(TITRE_PARTIEL, "prof@ept.sn");
        publier(TITRE_DIC3, "prof2@ept.sn");
        log.info("Jeu de démonstration créé : 16 étudiants (mot de passe {}), prof2@ept.sn, 6 évaluations ; "
                + "« {} » est terminée et prête à corriger puis publier.", MOT_DE_PASSE_ETUDIANTS, TITRE_CONTROLE);
    }

    private void publier(String titre, String email) {
        evaluationRepository.findAll().stream().filter(e -> titre.equals(e.getTitre())).findFirst()
                .ifPresent(e -> publicationResultatsService.publier(e.getId(), email));
    }

    // ------------------------------------------------------------------ création

    private boolean creer() {
        Enseignant prof = utilisateurRepository.findByEmail("prof@ept.sn")
                .filter(Enseignant.class::isInstance).map(Enseignant.class::cast).orElse(null);
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElse(null);
        Etudiant aminou = utilisateurRepository.findByEmail("etudiant@ept.sn")
                .filter(Etudiant.class::isInstance).map(Etudiant.class::cast).orElse(null);
        if (prof == null || dic2 == null || aminou == null) return false; // base sans les comptes de démonstration
        Promotion dic3 = promotionRepository.findByNom("DIC3 Informatique")
                .orElseGet(() -> promotionRepository.save(new Promotion("DIC3 Informatique", "2025/2026")));

        String hache = passwordEncoder.encode(MOT_DE_PASSE_ETUDIANTS); // même mot de passe pour tous : un seul hachage
        Map<Etudiant, Double> niveaux = new LinkedHashMap<>();
        niveaux.put(aminou, NIVEAU_AMINOU);
        DIC2.forEach(p -> niveaux.put(etudiant(p, dic2, hache, "ETU-2026-"), p.niveau()));
        Map<Etudiant, Double> niveauxDic3 = new LinkedHashMap<>();
        DIC3.forEach(p -> niveauxDic3.put(etudiant(p, dic3, hache, "ETU-2025-"), p.niveau()));
        Enseignant prof2 = (Enseignant) utilisateurRepository.save(new Enseignant("Diagne", "Awa", "prof2@ept.sn",
                passwordEncoder.encode("prof123"), "ENS-2026-02"));

        LocalDateTime maintenant = LocalDateTime.now();
        reseaux(prof, dic2, niveaux, maintenant.minusDays(21));
        partiel(prof, dic2, niveaux, maintenant.minusDays(7));
        controle(prof, dic2, niveaux, aminou, maintenant.minusHours(1));
        dic3(prof2, dic3, niveauxDic3, maintenant.minusDays(10));
        brouillon(prof, dic2, maintenant);
        aVenir(prof, dic2, maintenant);
        return true;
    }

    private int numero = 100;

    private Etudiant etudiant(Profil p, Promotion promotion, String hache, String prefixe) {
        String email = sansAccents(p.prenom() + "." + p.nom()).toLowerCase(Locale.ROOT) + "@ept.sn";
        String numero = prefixe + (++this.numero);
        return (Etudiant) utilisateurRepository.save(new Etudiant(p.nom(), p.prenom(), email, hache, numero, promotion));
    }

    private Evaluation evaluation(String titre, String description, int duree, LocalDateTime fermeture,
                                  StatutEvaluation statut, Enseignant prof, Promotion promotion) {
        Evaluation e = new Evaluation();
        e.setTitre(titre);
        e.setDescription(description);
        e.setDureeStandard(duree);
        e.setDateOuverture(fermeture.minusMinutes(duree).minusHours(2));
        e.setDateFermeture(fermeture);
        e.setStatut(statut);
        e.setResultatsPublies(false);
        e.setEnseignant(prof);
        e.setPromotions(new HashSet<>(Set.of(promotion)));
        return e;
    }

    // ------------------------------------------------------------------ Réseaux : QCM publié il y a 3 semaines

    private void reseaux(Enseignant prof, Promotion dic2, Map<Etudiant, Double> niveaux, LocalDateTime fermeture) {
        Evaluation e = evaluation(TITRE_RESEAUX, "QCM sur les modèles en couches, l'adressage IP et les équipements réseau.",
                30, fermeture, StatutEvaluation.TERMINEE, prof, dic2);
        QuestionQCM q1 = qcm(e, "Quel protocole assure une livraison fiable et ordonnée des données ?", 1, 2, false, 0,
                "TCP", true, "UDP", false, "IP", false, "ICMP", false);
        QuestionQCM q2 = qcm(e, "Combien d'adresses d'hôtes utilisables offre un réseau /24 ?", 2, 2, false, 0,
                "254", true, "256", false, "255", false, "252", false);
        QuestionQCM q3 = qcm(e, "Quelles couches font partie du modèle TCP/IP ?", 3, 3, true, 1,
                "Application", true, "Transport", true, "Session", false, "Internet", true);
        QuestionQCM q4 = qcm(e, "Quel équipement travaille principalement à la couche 3 (réseau) ?", 4, 3, false, 1,
                "Routeur", true, "Commutateur", false, "Concentrateur", false, "Répéteur", false);
        e = evaluationRepository.saveAndFlush(e);

        for (Map.Entry<Etudiant, Double> et : niveaux.entrySet()) {
            if (et.getKey().getNom().equals("Mbaye")) continue; // absent
            double n = et.getValue();
            Soumission s = copie(e, et.getKey(), 24);
            repondreQcm(s, q1, n >= 0.20);
            repondreQcm(s, q2, n >= 0.45);
            repondreQcm(s, q3, n >= 0.65);
            repondreQcm(s, q4, n >= 0.40);
            terminer(s);
        }
    }

    // ------------------------------------------------------------------ Bases de données : partiel publié il y a une semaine

    private void partiel(Enseignant prof, Promotion dic2, Map<Etudiant, Double> niveaux, LocalDateTime fermeture) {
        Evaluation e = evaluation(TITRE_PARTIEL, "Normalisation, jointures et un programme de calcul.", 90, fermeture,
                StatutEvaluation.TERMINEE, prof, dic2);
        QuestionQCM q1 = qcm(e, "Quelle forme normale élimine les dépendances transitives ?", 1, 4, false, 0,
                "Troisième forme normale (3FN)", true, "Première forme normale (1FN)", false,
                "Deuxième forme normale (2FN)", false, "Forme normale de Boyce-Codd", false);
        QuestionOuverte q2 = new QuestionOuverte("Expliquez la différence entre une jointure interne (INNER JOIN) et une "
                + "jointure externe gauche (LEFT JOIN), avec un exemple.", 2, 6.0);
        e.addQuestion(q2);
        QuestionCode q3 = code(e, "Écrire un programme qui lit un entier n puis n entiers (un par ligne) et affiche leur somme.",
                3, 10, "3\n1\n2\n3", "6", "1\n42", "42", "4\n-1\n1\n5\n5", "10", "0", "0");
        e = evaluationRepository.saveAndFlush(e);

        RevisionNote[] revision = new RevisionNote[1];
        for (Map.Entry<Etudiant, Double> et : niveaux.entrySet()) {
            double n = et.getValue();
            Soumission s = copie(e, et.getKey(), 80);
            repondreQcm(s, q1, n >= 0.45);
            repondreOuverte(s, q2, texteJointures(n), n, StatutProposition.VALIDEE, true);
            ReponseCode rc = repondreCode(s, q3, ProgrammesDemo.somme(n));
            if (et.getKey().getNom().equals("Faye")) alerte(s, 1, NiveauAlerte.INFO);
            if (et.getKey().getNom().equals("Diop")) {
                // Judge0 a noté 7,5/10 (3 cas sur 4 : isdigit ignore « -1 ») ; l'enseignant a relu le code avant de publier
                double ancienne = rc.getNoteObtenue();
                rc.setNoteObtenue(9.0);
                String motif = "Algorithme juste ; seul le cas avec un nombre négatif échoue (isdigit ignore le signe « - ») : erreur mineure.";
                rc.setCommentaireProf("Note revue par l'enseignant : " + motif);
                revision[0] = new RevisionNote(rc, prof, ancienne, 9.0, motif, fermeture.plusDays(2));
            }
            terminer(s);
        }
        if (revision[0] != null) revisionNoteRepository.save(revision[0]);
    }

    // ------------------------------------------------------------------ Programmation : terminée il y a 1 h, à corriger et publier

    private void controle(Enseignant prof, Promotion dic2, Map<Etudiant, Double> niveaux, Etudiant aminou,
                          LocalDateTime fermeture) {
        Evaluation e = evaluation(TITRE_CONTROLE, "Complexité, un programme sur les chaînes et la récursivité.", 60,
                fermeture, StatutEvaluation.TERMINEE, prof, dic2);
        QuestionQCM q1 = qcm(e, "Quelle est la complexité de la recherche dichotomique dans un tableau trié de n éléments ?",
                1, 4, false, 0, "O(log n)", true, "O(n)", false, "O(n log n)", false, "O(1)", false);
        QuestionCode q2 = code(e, "Écrire un programme qui lit une ligne et affiche le nombre de voyelles (a, e, i, o, u, y), "
                + "sans tenir compte des majuscules.", 2, 10, "Bonjour", "3", "PYTHON", "2", "xyz", "1", "rythme", "2");
        QuestionOuverte q3 = new QuestionOuverte("Expliquez ce qu'est la récursivité et donnez un exemple où elle est "
                + "préférable à une boucle.", 3, 6.0);
        e.addQuestion(q3);
        e = evaluationRepository.saveAndFlush(e);
        amenagementTempsRepository.save(new AmenagementTemps(aminou, e, 80)); // tiers-temps

        for (Map.Entry<Etudiant, Double> et : niveaux.entrySet()) {
            Etudiant etudiant = et.getKey();
            if (etudiant.getNom().equals("Mbaye")) continue; // absent
            double n = et.getValue();
            boolean aminouCopie = etudiant.getId().equals(aminou.getId());
            boolean sarr = etudiant.getNom().equals("Sarr");
            Soumission s = copie(e, etudiant, aminouCopie ? 75 : 50);
            repondreQcm(s, q1, n >= 0.35);
            if (aminouCopie) {
                // Judge0 indisponible : le code attend une note de l'enseignant (repli manuel)
                ReponseCode rc = new ReponseCode();
                rc.setQuestion(q2);
                rc.setLangage("python");
                rc.setCodeSoumis(ProgrammesDemo.VOYELLES_SANS_MINUSCULES.code);
                s.addReponse(rc);
            } else {
                repondreCode(s, q2, ProgrammesDemo.voyelles(n));
            }
            // Aminou et Fatou Sarr : la proposition de l'IA attend la validation de l'enseignant
            repondreOuverte(s, q3, texteRecursivite(n), n, aminouCopie || sarr ? StatutProposition.EN_ATTENTE
                    : StatutProposition.VALIDEE, !(aminouCopie || sarr));
            if (etudiant.getNom().equals("Sy")) alerte(s, 3, NiveauAlerte.MODERE);
            if (aminouCopie || sarr) {
                s.setStatut(StatutSoumission.EN_CORRECTION);
                soumissionRepository.save(s);
            } else {
                terminer(s);
            }
        }
    }

    // ------------------------------------------------------------------ DIC3 : examen d'un autre enseignant, publié

    private void dic3(Enseignant prof2, Promotion dic3, Map<Etudiant, Double> niveaux, LocalDateTime fermeture) {
        Evaluation e = evaluation(TITRE_DIC3, "Cohérence, consensus et tolérance aux pannes.", 120, fermeture,
                StatutEvaluation.TERMINEE, prof2, dic3);
        QuestionQCM q1 = qcm(e, "Selon le théorème CAP, que ne peut-on garantir simultanément en cas de partition ?", 1, 4,
                false, 0, "Cohérence et disponibilité", true, "Disponibilité et durabilité", false,
                "Cohérence et durabilité", false, "Latence et débit", false);
        QuestionQCM q2 = qcm(e, "Quels algorithmes servent au consensus ?", 2, 4, true, 1,
                "Raft", true, "Paxos", true, "Dijkstra", false, "Quicksort", false);
        QuestionOuverte q3 = new QuestionOuverte("Décrivez le rôle du leader dans Raft et ce qui se passe quand il tombe en panne.",
                3, 12.0);
        e.addQuestion(q3);
        e = evaluationRepository.saveAndFlush(e);
        for (Map.Entry<Etudiant, Double> et : niveaux.entrySet()) {
            double n = et.getValue();
            Soumission s = copie(e, et.getKey(), 100);
            repondreQcm(s, q1, n >= 0.5);
            repondreQcm(s, q2, n >= 0.6);
            repondreOuverte(s, q3, "Le leader reçoit les écritures, les réplique sur les suiveurs et valide une entrée quand "
                    + "une majorité l'a reçue. S'il tombe, les suiveurs ne reçoivent plus de battements de cœur et une élection "
                    + "désigne un nouveau leader.", n, StatutProposition.VALIDEE, true);
            terminer(s);
        }
    }

    // ------------------------------------------------------------------ brouillon et évaluation à venir

    private void brouillon(Enseignant prof, Promotion dic2, LocalDateTime maintenant) {
        Evaluation e = evaluation(TITRE_BROUILLON, "Brouillon : à compléter, prévisualiser puis publier pendant la démo.", 45,
                maintenant.plusDays(14).plusHours(2), StatutEvaluation.BROUILLON, prof, dic2);
        qcm(e, "Quel patron garantit qu'une classe n'a qu'une seule instance ?", 1, 2, false, 0,
                "Singleton", true, "Fabrique", false, "Observateur", false, "Adaptateur", false);
        e.addQuestion(new QuestionOuverte("Expliquez l'intérêt du patron Observateur avec un exemple.", 2, 4.0));
        evaluationRepository.save(e);
    }

    private void aVenir(Enseignant prof, Promotion dic2, LocalDateTime maintenant) {
        Evaluation e = evaluation(TITRE_A_VENIR, "Ordonnancement des processus : FIFO, SJF, tourniquet.", 40,
                maintenant.plusDays(2).plusHours(3), StatutEvaluation.PUBLIEE, prof, dic2);
        qcm(e, "Quel algorithme d'ordonnancement peut provoquer la famine des longs processus ?", 1, 2, false, 0,
                "SJF (plus court d'abord)", true, "FIFO", false, "Tourniquet", false, "Aucun", false);
        evaluationRepository.save(e);
    }

    // ------------------------------------------------------------------ questions

    private QuestionQCM qcm(Evaluation e, String enonce, int ordre, double points, boolean multiple, double penalite,
                            Object... choix) {
        QuestionQCM q = new QuestionQCM(enonce, ordre, points, multiple);
        q.setPenalite(penalite);
        for (int i = 0; i < choix.length; i += 2) q.addChoix(new ChoixReponse((String) choix[i], (Boolean) choix[i + 1]));
        e.addQuestion(q);
        return q;
    }

    /** Question de code ; les deux derniers cas sont masqués pour l'étudiant. */
    private QuestionCode code(Evaluation e, String enonce, int ordre, double points, String... entreesSorties) {
        QuestionCode q = new QuestionCode(enonce, ordre, points);
        int nbCas = entreesSorties.length / 2;
        for (int i = 0; i < nbCas; i++) {
            q.addCasDeTest(new CasDeTest(entreesSorties[2 * i], entreesSorties[2 * i + 1], i >= nbCas - 2));
        }
        e.addQuestion(q);
        return q;
    }

    // ------------------------------------------------------------------ copies et réponses

    private Soumission copie(Evaluation e, Etudiant etudiant, int minutes) {
        Soumission s = new Soumission();
        s.setEvaluation(e);
        s.setEtudiant(etudiant);
        LocalDateTime debut = e.getDateOuverture().plusMinutes(10 + Math.abs(etudiant.getEmail().hashCode()) % 60);
        s.setDateDebut(debut);
        s.setDateRemise(debut.plusMinutes(minutes));
        return s;
    }

    private void repondreQcm(Soumission s, QuestionQCM q, boolean juste) {
        ReponseQCM r = new ReponseQCM();
        r.setQuestion(q);
        Set<ChoixReponse> coches = new HashSet<>();
        if (juste) {
            q.getChoix().stream().filter(ChoixReponse::getEstCorrect).forEach(coches::add);
        } else {
            q.getChoix().stream().filter(c -> !c.getEstCorrect()).findFirst().ifPresent(coches::add);
        }
        r.setChoixCoches(coches);
        double penalite = q.getPenalite() == null ? 0 : q.getPenalite();
        r.setNoteObtenue(juste ? q.getPoints() : (penalite == 0 ? 0.0 : -penalite));
        r.setValideeParProf(true);
        r.setCommentaireProf("Correction automatique QCM.");
        s.addReponse(r);
    }

    /**
     * Code exécuté comme le ferait Judge0 : chaque cas compare ce que le programme affiche vraiment à la sortie attendue.
     * Un programme que Python refuse (erreur de syntaxe) échoue à tous les cas.
     */
    private ReponseCode repondreCode(Soumission s, QuestionCode q, ProgrammesDemo programme) {
        ReponseCode r = new ReponseCode();
        r.setQuestion(q);
        r.setLangage("python");
        r.setCodeSoumis(programme.code);
        List<CasDeTest> cas = q.getCasTests();
        int reussis = 0;
        for (int i = 0; i < cas.size(); i++) {
            CasDeTest c = cas.get(i);
            ResultatCasTest rt = new ResultatCasTest();
            rt.setCasDeTest(c);
            rt.setTempsExecution(0.02 + i * 0.01);
            Optional<String> sortie = programme.executer(c.getEntree());
            boolean ok = sortie.isPresent() && sortie.get().equals(c.getSortieAttendue().strip());
            rt.setSortieObtenue(sortie.orElse(""));
            rt.setEstValide(ok);
            if (programme.erreurDeSyntaxe()) {
                rt.setVerdict(VerdictCas.ERREUR_EXECUTION);
                rt.setMessageErreur(ProgrammesDemo.ERREUR_SYNTAXE);
            } else {
                rt.setVerdict(ok ? VerdictCas.REUSSI : VerdictCas.MAUVAISE_REPONSE);
            }
            r.addResultat(rt);
            if (ok) reussis++;
        }
        r.setNoteObtenue(arrondi(q.getPoints() * reussis / cas.size()));
        r.setValideeParProf(true);
        r.setCommentaireProf("Correction automatique : " + reussis + "/" + cas.size() + " cas de test réussis.");
        s.addReponse(r);
        return r;
    }

    private void repondreOuverte(Soumission s, QuestionOuverte q, String texte, double niveau, StatutProposition statut,
                                 boolean notee) {
        ReponseOuverte r = new ReponseOuverte();
        r.setQuestion(q);
        r.setTexteSoumis(texte);
        double note = Math.round(q.getPoints() * Math.min(1, niveau + 0.05) * 2) / 2.0;
        String justification = niveau >= 0.75 ? "Réponse complète et précise, exemple pertinent."
                : niveau >= 0.5 ? "Idée principale juste, exemple incomplet." : "Réponse partielle : notions confondues.";
        r.setPropositionIA(new PropositionIA(note, justification, statut));
        if (notee) {
            r.setNoteObtenue(note);
            r.setValideeParProf(true);
        }
        s.addReponse(r);
    }

    private void alerte(Soumission s, int changements, NiveauAlerte niveau) {
        s.setNbChangementsOnglet(changements);
        s.addAlerte(new AlerteAntiTriche(changements + " changement" + (changements > 1 ? "s" : "") + " d'onglet pendant l'épreuve.",
                niveau, s));
    }

    private void terminer(Soumission s) {
        double somme = s.getReponses().stream().map(ReponseEtudiant::getNoteObtenue).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sum();
        s.setNoteFinale(Math.max(0.0, arrondi(somme)));
        s.setStatut(StatutSoumission.EVALUE);
        soumissionRepository.save(s);
    }

    // ------------------------------------------------------------------ contenus des réponses

    private static String texteJointures(double n) {
        return n >= 0.75 ? "INNER JOIN ne garde que les lignes qui ont une correspondance dans les deux tables. LEFT JOIN garde "
                + "toutes les lignes de la table de gauche, avec NULL quand il n'y a pas de correspondance. Exemple : la liste "
                + "des étudiants avec leurs notes ; en LEFT JOIN, un étudiant sans note apparaît quand même."
                : n >= 0.5 ? "INNER JOIN prend les lignes communes, LEFT JOIN prend aussi celles de gauche. Exemple : clients et commandes."
                : "Les deux jointures relient deux tables, LEFT JOIN est plus rapide.";
    }

    private static String texteRecursivite(double n) {
        return n >= 0.75 ? "Une fonction récursive s'appelle elle-même sur un problème plus petit, jusqu'à un cas de base. "
                + "Elle est préférable pour parcourir un arbre (par exemple calculer sa hauteur), où une boucle demanderait "
                + "de gérer une pile à la main."
                : n >= 0.5 ? "C'est une fonction qui s'appelle elle-même, par exemple factorielle."
                : "C'est une boucle qui se répète.";
    }

    private static double arrondi(double valeur) {
        return BigDecimal.valueOf(valeur).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String sansAccents(String texte) {
        return java.text.Normalizer.normalize(texte, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace(' ', '-');
    }
}
