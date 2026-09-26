package ma.myc.inner.donation.domain.bo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

class DonationBOTest {

	@Test
	@DisplayName("L'identifiant est attribue par l'application : pas de @GeneratedValue (sinon save() -> merge -> 500 sous Hibernate 7)")
	void id_isAssignedByApplication_notGenerated() throws NoSuchFieldException {
		var idField = DonationBO.class.getDeclaredField("id");

		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		assertThat(idField.isAnnotationPresent(GeneratedValue.class)).isFalse();
	}
}
