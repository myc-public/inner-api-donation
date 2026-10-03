package ma.myc.inner.donation.exception;


public class DonorAlreadyExistsException extends RuntimeException {

    // Message fixe : l'email (donnee personnelle) ne doit apparaitre ni dans la reponse ni dans les logs
    public static final String MESSAGE = "A donor with this email already exists";

    // Profil donateur deja cree pour la personne connectee (POST /donors/me, K4c)
    public static final String PROFILE_MESSAGE = "A donor profile already exists for this person";

    public DonorAlreadyExistsException() {
        super(MESSAGE);
    }

    public DonorAlreadyExistsException(String message) {
        super(message);
    }
}
