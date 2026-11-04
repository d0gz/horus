package samples.inferred;

import jakarta.persistence.*;

@Entity
public class Visit extends AbstractEntity<VisitId> {
	PetId petId;
	VetId vetId;
	VisitId previousVisitId;
	OtherId otherId;
	SharedId sharedId;
	ClinicId clinicId;
	Long ownerNumber;
	@ManyToOne
	Pet pet;
}
