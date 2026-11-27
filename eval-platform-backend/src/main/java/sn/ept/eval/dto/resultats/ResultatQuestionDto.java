package sn.ept.eval.dto.resultats;

import sn.ept.eval.model.enums.TypeQuestion;

import java.util.List;

/**
 * Résultat d'une question : réponse de l'étudiant, points obtenus et commentaire validé par l'enseignant.
 * La proposition de l'IA n'est jamais renvoyée.
 *
 * @param noteObtenue null si l'étudiant n'a pas répondu
 * @param choix       questions QCM et quiz, sinon vide
 * @param casTests    questions de code, sinon vide
 */
public record ResultatQuestionDto(Long questionId, Integer ordre, TypeQuestion type, String enonce, Double points,
                                  Double noteObtenue, String commentaire, List<ChoixResultatDto> choix,
                                  String texteSoumis, String codeSoumis, String langage,
                                  List<CasTestResultatDto> casTests) {}
