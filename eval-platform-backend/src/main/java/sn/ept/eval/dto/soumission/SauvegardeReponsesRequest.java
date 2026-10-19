package sn.ept.eval.dto.soumission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class SauvegardeReponsesRequest {

    public static final int NB_MAX_REPONSES = 200;

    @NotNull
    @Size(max = NB_MAX_REPONSES, message = "Trop de réponses dans une seule sauvegarde")
    private List<@Valid ReponseItemDto> reponses = new ArrayList<>();

    public SauvegardeReponsesRequest() {}

    public SauvegardeReponsesRequest(List<ReponseItemDto> reponses) {
        if (reponses != null) {
            this.reponses = reponses;
        }
    }

    public List<ReponseItemDto> getReponses() { return reponses; }
    public void setReponses(List<ReponseItemDto> reponses) { this.reponses = reponses; }
}
