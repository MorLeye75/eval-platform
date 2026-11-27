package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.evaluation.PromotionResponse;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.PromotionRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Liste des promotions de l'assistant de création (DS-03). */
@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionRepository promotionRepository;
    @Mock private EtudiantRepository etudiantRepository;
    @InjectMocks private PromotionService service;

    @Test
    @DisplayName("Année la plus récente d'abord, puis ordre alphabétique, avec l'effectif de chaque promotion")
    void lister_TrieEtCompte() {
        Promotion dic1 = promotion(1L, "DIC1 Informatique", "2025/2026");
        Promotion dic2 = promotion(2L, "DIC2 Informatique", "2025/2026");
        Promotion ancienne = promotion(3L, "DIC3 Informatique", "2024/2025");
        when(promotionRepository.findAll()).thenReturn(List.of(ancienne, dic2, dic1));
        when(etudiantRepository.countByPromotionId(1L)).thenReturn(45L);
        when(etudiantRepository.countByPromotionId(2L)).thenReturn(38L);
        when(etudiantRepository.countByPromotionId(3L)).thenReturn(0L);

        List<PromotionResponse> promotions = service.lister();

        assertThat(promotions).extracting(PromotionResponse::nom)
                .containsExactly("DIC1 Informatique", "DIC2 Informatique", "DIC3 Informatique");
        assertThat(promotions).extracting(PromotionResponse::nbEtudiants).containsExactly(45L, 38L, 0L);
    }

    @Test
    @DisplayName("Aucune promotion : liste vide")
    void lister_Vide() {
        when(promotionRepository.findAll()).thenReturn(List.of());

        assertThat(service.lister()).isEmpty();
    }

    private Promotion promotion(Long id, String nom, String annee) {
        Promotion p = new Promotion(nom, annee);
        p.setId(id);
        return p;
    }
}
