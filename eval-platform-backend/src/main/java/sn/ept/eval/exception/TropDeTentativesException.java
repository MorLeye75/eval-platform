package sn.ept.eval.exception;

public class TropDeTentativesException extends RuntimeException {

    private final long secondesAvantNouvelEssai;

    public TropDeTentativesException(String message, long secondesAvantNouvelEssai) {
        super(message);
        this.secondesAvantNouvelEssai = secondesAvantNouvelEssai;
    }

    public long getSecondesAvantNouvelEssai() {
        return secondesAvantNouvelEssai;
    }
}
