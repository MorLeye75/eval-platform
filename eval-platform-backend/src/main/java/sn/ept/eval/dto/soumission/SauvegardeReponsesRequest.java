package sn.ept.eval.dto.soumission;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class SauvegardeReponsesRequest {

    @NotNull
    private List<ReponseItemDto> reponses = new ArrayList<>();

    public SauvegardeReponsesRequest() {}

    public SauvegardeReponsesRequest(List<ReponseItemDto> reponses) {
        if (reponses != null) {
            this.reponses = reponses;
        }
    }

    public List<ReponseItemDto> getReponses() { return reponses; }
    public void setReponses(List<ReponseItemDto> reponses) { this.reponses = reponses; }
}
