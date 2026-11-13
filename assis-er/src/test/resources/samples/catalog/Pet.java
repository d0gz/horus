package samples.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public class Pet extends AbstractEntity<PetId> {
	@ManyToOne
	Owner owner;
}
