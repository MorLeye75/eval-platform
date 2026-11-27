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
    private List<@NotNull(message = "Réponse vide dans la sauvegarde") @Valid ReponseItemDto> reponses = new ArrayList<>();

    // Version des réponses sur laquelle le client a saisi ; null = pas de contrôle (client sans versionnement)
    private Integer versionAttendue;

    public SauvegardeReponsesRequest() {}

    public SauvegardeReponsesRequest(List<ReponseItemDto> reponses) {
        if (reponses != null) {
            this.reponses = reponses;
        }
    }

    public SauvegardeReponsesRequest(List<ReponseItemDto> reponses, Integer versionAttendue) {
        this(reponses);
        this.versionAttendue = versionAttendue;
    }

    public List<ReponseItemDto> getReponses() { return reponses; }
    public void setReponses(List<ReponseItemDto> reponses) { this.reponses = reponses; }

    public Integer getVersionAttendue() { return versionAttendue; }
    public void setVersionAttendue(Integer versionAttendue) { this.versionAttendue = versionAttendue; }
}
