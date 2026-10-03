package ma.myc.inner.donation.exception;

/**
 * Le donateur connecte n'a pas encore de profil (DC4, K4c) : 409, le profil est cree par l'onboarding (POST /donors/me).
 */
public class DonorProfileRequiredException extends RuntimeException {

    public static final String MESSAGE = "A donor profile is required before creating a donation";

    public DonorProfileRequiredException() {
        super(MESSAGE);
    }
}
