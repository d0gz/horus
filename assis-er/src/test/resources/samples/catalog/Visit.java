package samples.catalog;

import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Visit {
	@Id
	Long id;
	PetId petId;
	Set<PetId> pastPets;
}
