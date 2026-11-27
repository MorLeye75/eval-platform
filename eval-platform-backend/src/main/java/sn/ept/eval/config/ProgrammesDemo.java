package sn.ept.eval.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Programmes Python des copies de démonstration, avec ce que chacun affiche vraiment pour une entrée donnée. Les
 * verdicts des cas de test sont calculés à partir de ces sorties, comme Judge0 le ferait : la note affichée est
 * toujours justifiée par le code montré (une erreur d'étudiant précise par variante).
 */
enum ProgrammesDemo {

    // ------------------------------------------------------------------ nombre de voyelles (a, e, i, o, u, y), sans tenir compte des majuscules

    VOYELLES_JUSTE("""
            texte = input().lower()
            print(sum(1 for c in texte if c in "aeiouy"))
            """, entree -> compter(premiereLigne(entree).toLowerCase(Locale.ROOT), "aeiouy")),

    /** Oublie le passage en minuscules : « PYTHON » donne 0. */
    VOYELLES_SANS_MINUSCULES("""
            texte = input()
            print(sum(1 for c in texte if c in "aeiouy"))
            """, entree -> compter(premiereLigne(entree), "aeiouy")),

    /** Oublie le y demandé par l'énoncé. */
    VOYELLES_SANS_Y("""
            texte = input().lower()
            compte = 0
            for c in texte:
                if c in "aeiou":
                    compte += 1
            print(compte)
            """, entree -> compter(premiereLigne(entree).toLowerCase(Locale.ROOT), "aeiou")),

    /** Oublie les majuscules et le y. */
    VOYELLES_DEBUTANT("""
            texte = input()
            compte = 0
            for c in texte:
                if c in "aeiou":
                    compte += 1
            print(compte)
            """, entree -> compter(premiereLigne(entree), "aeiou")),

    /** Deux-points oubliés : Python refuse le programme, aucun cas ne passe. */
    VOYELLES_ERREUR_SYNTAXE("""
            texte = input().lower()
            compte = 0
            for c in texte
                if c in "aeiouy":
                    compte += 1
            print(compte)
            """, null),

    // ------------------------------------------------------------------ somme de n entiers lus un par ligne

    SOMME_JUSTE("""
            n = int(input())
            total = 0
            for _ in range(n):
                total += int(input())
            print(total)
            """, entree -> String.valueOf(entiers(entree).stream().mapToLong(Long::longValue).sum())),

    /** isdigit() est faux pour « -1 » : les nombres négatifs sont ignorés. */
    SOMME_SANS_NEGATIFS("""
            n = int(input())
            total = 0
            for _ in range(n):
                ligne = input()
                if ligne.isdigit():
                    total += int(ligne)
            print(total)
            """, entree -> String.valueOf(entiers(entree).stream().filter(v -> v >= 0).mapToLong(Long::longValue).sum())),

    /** Ajoute aussi n, le nombre d'entiers, à la somme. */
    SOMME_AVEC_N("""
            n = int(input())
            total = n
            for _ in range(n):
                total += int(input())
            print(total)
            """, entree -> String.valueOf(Long.parseLong(lignes(entree)[0].trim())
                    + entiers(entree).stream().mapToLong(Long::longValue).sum())),

    /** Parenthèse fermante oubliée : Python refuse le programme. */
    SOMME_ERREUR_SYNTAXE("""
            n = int(input())
            total = 0
            for _ in range(n):
                total += int(input())
            print(total
            """, null);

    /** Message d'erreur affiché pour une variante que Python refuse. */
    static final String ERREUR_SYNTAXE = "SyntaxError: invalid syntax";

    final String code;
    private final Function<String, String> execution;

    ProgrammesDemo(String code, Function<String, String> execution) {
        this.code = code;
        this.execution = execution;
    }

    /** Ce que le programme affiche pour cette entrée ; vide s'il ne s'exécute pas (erreur de syntaxe). */
    Optional<String> executer(String entree) {
        return execution == null ? Optional.empty() : Optional.of(execution.apply(entree));
    }

    boolean erreurDeSyntaxe() {
        return execution == null;
    }

    /** Variante d'après le code d'une copie (vérification de la cohérence des verdicts). */
    static Optional<ProgrammesDemo> de(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }

    /** Programme d'un étudiant pour l'exercice des voyelles, selon son niveau. */
    static ProgrammesDemo voyelles(double niveau) {
        if (niveau >= 0.80) return VOYELLES_JUSTE;
        if (niveau >= 0.60) return VOYELLES_SANS_MINUSCULES;
        if (niveau >= 0.45) return VOYELLES_SANS_Y;
        if (niveau >= 0.30) return VOYELLES_DEBUTANT;
        return VOYELLES_ERREUR_SYNTAXE;
    }

    /** Programme d'un étudiant pour l'exercice de la somme, selon son niveau. */
    static ProgrammesDemo somme(double niveau) {
        if (niveau >= 0.75) return SOMME_JUSTE;
        if (niveau >= 0.50) return SOMME_SANS_NEGATIFS;
        if (niveau >= 0.30) return SOMME_AVEC_N;
        return SOMME_ERREUR_SYNTAXE;
    }

    private static String premiereLigne(String entree) {
        return lignes(entree)[0];
    }

    private static String[] lignes(String entree) {
        return entree.split("\n", -1);
    }

    private static String compter(String texte, String voyelles) {
        return String.valueOf(texte.chars().filter(c -> voyelles.indexOf(c) >= 0).count());
    }

    /** Les n entiers qui suivent la première ligne. */
    private static java.util.List<Long> entiers(String entree) {
        String[] l = lignes(entree);
        int n = Integer.parseInt(l[0].trim());
        return Arrays.stream(l, 1, 1 + n).map(s -> Long.parseLong(s.trim())).toList();
    }
}
