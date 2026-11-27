package sn.ept.eval.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Mots de passe provisoires (DS-16) : SecureRandom, sans caractères ambigus (0/O, 1/l/I). Jamais journalisés. */
@Component
public class GenerateurMotDePasse {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    static final int LONGUEUR = 12;

    private final SecureRandom hasard = new SecureRandom();

    public String generer() {
        StringBuilder mdp = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) {
            mdp.append(ALPHABET.charAt(hasard.nextInt(ALPHABET.length())));
        }
        return mdp.toString();
    }
}
