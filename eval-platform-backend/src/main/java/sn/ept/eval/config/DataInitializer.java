package sn.ept.eval.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (utilisateurRepository.count() > 0) {
            logger.info("Données initiales déjà présentes.");
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
        eval1.setPromotions(List.of(dic2));

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
        eval2.setPromotions(List.of(dic2));

        QuestionCode qCode = new QuestionCode("Écrire une fonction `estPalindrome(String s)` qui renvoie true si la chaîne est un palindrome.", 1, 5.0);
        qCode.addCasDeTest(new CasDeTest("radar", "true", false));
        qCode.addCasDeTest(new CasDeTest("bonjour", "false", false));
        qCode.addCasDeTest(new CasDeTest("kayak", "true", true)); // Cas masqué
        eval2.addQuestion(qCode);

        evaluationRepository.save(eval2);

        logger.info("Données de test initialisées avec succès !");
        logger.info("Comptes créés : admin@ept.sn / admin123 | prof@ept.sn / prof123 | etudiant@ept.sn / etudiant123");
    }
}
