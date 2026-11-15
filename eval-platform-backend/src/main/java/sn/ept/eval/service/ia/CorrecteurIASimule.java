package sn.ept.eval.service.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Correcteur sans réseau (fournisseur « simule », par défaut) : démonstration et tests. Il compare les mots
 * significatifs de l'énoncé à ceux de la réponse ; ce n'est PAS une correction pédagogique. Un connecteur vers un
 * vrai modèle de langage implémentera la même interface ({@code correction.ia.fournisseur = llm}).
 */
@Component
@ConditionalOnProperty(name = "correction.ia.fournisseur", havingValue = "simule", matchIfMissing = true)
public class CorrecteurIASimule implements CorrecteurIA {

    @Override
    public Proposition proposer(String enonce, double bareme, String texteEtudiant) {
        Set<String> motsEnonce = mots(enonce);
        Set<String> motsReponse = mots(texteEtudiant);
        if (motsReponse.isEmpty()) {
            return new Proposition(0.0, "Aucune réponse.");
        }
        long communs = motsEnonce.stream().filter(motsReponse::contains).count();
        double couvertureEnonce = motsEnonce.isEmpty() ? 0.5 : (double) communs / motsEnonce.size();
        double longueur = Math.min(1.0, motsReponse.size() / 30.0); // 30 mots distincts = réponse développée
        double ratio = 0.5 * Math.min(1.0, couvertureEnonce * 2) + 0.5 * longueur;
        double note = BigDecimal.valueOf(CorrecteurIA.borner(ratio * bareme, bareme)).setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new Proposition(note, "Proposition simulée (sans modèle de langage) : " + communs + " mot(s) de l'énoncé repris, "
                + motsReponse.size() + " mot(s) distinct(s). À vérifier par l'enseignant.");
    }

    private static Set<String> mots(String texte) {
        if (texte == null) return Set.of();
        String sansAccents = Normalizer.normalize(texte.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(sansAccents.split("[^a-z0-9]+")).filter(m -> m.length() > 3).collect(Collectors.toSet());
    }
}
