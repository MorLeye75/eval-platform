package sn.ept.eval.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Jeu de données de démonstration : uniquement pour le développement local et les tests.
 */
@Component
@Profile({"dev", "h2-dev", "test"})
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    /** Titre du quiz de démonstration (ni « QCM » ni « code » : les tests repèrent les autres évaluations ainsi). */
    static final String TITRE_QUIZ_DEMO = "Bases de données — Quiz chronométré";

    private final UtilisateurRepository utilisateurRepository;
    private final PromotionRepository promotionRepository;
    private final EvaluationRepository evaluationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UtilisateurRepository utilisateurRepository,
                           PromotionRepository promotionRepository,
                           EvaluationRepository evaluationRepository,
                           PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.promotionRepository = promotionRepository;
        this.evaluationRepository = evaluationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (utilisateurRepository.count() > 0) {
            logger.info("Données initiales déjà présentes.");
            ajouterQuizDemo();
            return;
        }

        logger.info("Initialisation des données de démarrage...");

        // 1. Promotions
        Promotion dic2 = new Promotion("DIC2 Informatique", "2025/2026");
        Promotion dic3 = new Promotion("DIC3 Informatique", "2025/2026");
        promotionRepository.saveAll(List.of(dic2, dic3));

        // 2. Utilisateurs
        Administrateur admin = new Administrateur("Directeur", "Admin", "admin@ept.sn", passwordEncoder.encode("admin123"));
        Enseignant prof = new Enseignant("Sow", "Mamadou", "prof@ept.sn", passwordEncoder.encode("prof123"), "ENS-2026-01");
        Etudiant etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", passwordEncoder.encode("etudiant123"), "ETU-2026-42", dic2);

        utilisateurRepository.saveAll(List.of(admin, prof, etudiant));

        // 3. Évaluation exemple (Structures de données - QCM)
        Evaluation eval1 = new Evaluation();
        eval1.setTitre("Structures de données — QCM");
        eval1.setDescription("Évaluation portant sur les piles, files, listes et arbres.");
        eval1.setDureeStandard(45);
        eval1.setDateOuverture(LocalDateTime.now().minusHours(2));
        eval1.setDateFermeture(LocalDateTime.now().plusDays(5));
        eval1.setStatut(StatutEvaluation.EN_COURS);
        eval1.setResultatsPublies(false);
        eval1.setEnseignant(prof);
        eval1.setPromotions(Set.of(dic2));

        QuestionQCM q1 = new QuestionQCM("Quelle structure de données suit le principe LIFO (Last In, First Out) ?", 1, 2.0, false);
        q1.addChoix(new ChoixReponse("File (Queue)", false));
        q1.addChoix(new ChoixReponse("Pile (Stack)", true));
        q1.addChoix(new ChoixReponse("Liste chaînée", false));
        q1.addChoix(new ChoixReponse("Arbre binaire", false));
        eval1.addQuestion(q1);

        QuestionQCM q2 = new QuestionQCM("Parmi les propositions suivantes, lesquelles sont des structures de données linéaires ?", 2, 3.0, true);
        q2.addChoix(new ChoixReponse("Tableau", true));
        q2.addChoix(new ChoixReponse("Arbre binaire de recherche", false));
        q2.addChoix(new ChoixReponse("Liste chaînée", true));
        q2.addChoix(new ChoixReponse("File", true));
        eval1.addQuestion(q2);

        evaluationRepository.save(eval1);

        // 4. Évaluation exemple (Algorithmique - Code)
        Evaluation eval2 = new Evaluation();
        eval2.setTitre("Algorithmique — Exercice de code");
        eval2.setDescription("Implémentation de fonctions de manipulation de chaînes et tableaux.");
        eval2.setDureeStandard(60);
        eval2.setDateOuverture(LocalDateTime.now().minusHours(1));
        eval2.setDateFermeture(LocalDateTime.now().plusDays(2));
        eval2.setStatut(StatutEvaluation.EN_COURS);
        eval2.setResultatsPublies(false);
        eval2.setEnseignant(prof);
        eval2.setPromotions(Set.of(dic2));

        // Judge0 exécute un programme complet : l'entrée du cas de test arrive sur l'entrée standard
        // et la sortie standard est comparée à la sortie attendue
        QuestionCode qCode = new QuestionCode("Écrire un programme qui lit une chaîne sur l'entrée standard et affiche "
                + "true si c'est un palindrome, false sinon.", 1, 5.0);
        qCode.addCasDeTest(new CasDeTest("radar", "true", false));
        qCode.addCasDeTest(new CasDeTest("bonjour", "false", false));
        qCode.addCasDeTest(new CasDeTest("kayak", "true", true)); // Cas masqué
        eval2.addQuestion(qCode);

        evaluationRepository.save(eval2);

        ajouterQuizDemo();

        logger.info("Données de test initialisées avec succès !");
        logger.info("Comptes de démonstration créés : admin@ept.sn, prof@ept.sn, etudiant@ept.sn");
    }

    /**
     * Quiz chronométré de démonstration, pour essayer l'écran du quiz à la main. Ajouté aussi à une base déjà
     * remplie (base de développement existante), une seule fois : il est reconnu à son titre.
     */
    private void ajouterQuizDemo() {
        if (evaluationRepository.existsByTitre(TITRE_QUIZ_DEMO)) return;
        Enseignant prof = utilisateurRepository.findByEmail("prof@ept.sn")
                .filter(Enseignant.class::isInstance).map(Enseignant.class::cast).orElse(null);
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElse(null);
        if (prof == null || dic2 == null) return; // base sans les comptes de démonstration

        Evaluation quiz = new Evaluation();
        quiz.setTitre(TITRE_QUIZ_DEMO);
        quiz.setDescription("Quiz chronométré : une question à la fois, sans retour en arrière.");
        quiz.setDureeStandard(10);
        quiz.setDateOuverture(LocalDateTime.now().minusHours(1));
        quiz.setDateFermeture(LocalDateTime.now().plusDays(3));
        quiz.setStatut(StatutEvaluation.EN_COURS);
        quiz.setResultatsPublies(false);
        quiz.setEnseignant(prof);
        quiz.setPromotions(new HashSet<>(Set.of(dic2)));

        QuestionQuiz q1 = new QuestionQuiz("Quelle commande SQL supprime toutes les lignes d'une table sans la supprimer ?", 1, 1.0, false, 30);
        q1.addChoix(new ChoixReponse("DROP TABLE", false));
        q1.addChoix(new ChoixReponse("TRUNCATE TABLE", true));
        q1.addChoix(new ChoixReponse("ALTER TABLE", false));
        quiz.addQuestion(q1);

        QuestionQuiz q2 = new QuestionQuiz("Quelles propriétés font partie de ACID ?", 2, 2.0, true, 45);
        q2.addChoix(new ChoixReponse("Atomicité", true));
        q2.addChoix(new ChoixReponse("Disponibilité", false));
        q2.addChoix(new ChoixReponse("Isolation", true));
        q2.addChoix(new ChoixReponse("Durabilité", true));
        quiz.addQuestion(q2);

        QuestionQuiz q3 = new QuestionQuiz("Une clé étrangère référence…", 3, 1.0, false, 20);
        q3.addChoix(new ChoixReponse("la clé primaire d'une autre table (ou de la même)", true));
        q3.addChoix(new ChoixReponse("un index quelconque", false));
        q3.addChoix(new ChoixReponse("une vue", false));
        quiz.addQuestion(q3);

        evaluationRepository.save(quiz);
        logger.info("Quiz chronométré de démonstration ajouté : « {} »", TITRE_QUIZ_DEMO);
    }
}
