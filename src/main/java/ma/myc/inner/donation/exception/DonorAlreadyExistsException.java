package ma.myc.inner.donation.exception;


public class DonorAlreadyExistsException extends RuntimeException {

    // Message fixe : l'email (donnee personnelle) ne doit apparaitre ni dans la reponse ni dans les logs
    public static final String MESSAGE = "A donor with this email already exists";

    public DonorAlreadyExistsException() {
        super(MESSAGE);
    }
}
