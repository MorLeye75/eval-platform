package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.PromotionResponse;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.PromotionRepository;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final EtudiantRepository etudiantRepository;

    public PromotionService(PromotionRepository promotionRepository, EtudiantRepository etudiantRepository) {
        this.promotionRepository = promotionRepository;
        this.etudiantRepository = etudiantRepository;
    }

    /** Année la plus récente d'abord, puis ordre alphabétique : la promotion courante est en tête de liste. */
    public List<PromotionResponse> lister() {
        return promotionRepository.findAll().stream()
                .sorted(Comparator.comparing(Promotion::getAnnee, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Promotion::getNom, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new PromotionResponse(p.getId(), p.getNom(), p.getAnnee(), etudiantRepository.countByPromotionId(p.getId())))
                .toList();
    }
}
