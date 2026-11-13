package samples.inferred;

import jakarta.persistence.*;

@Entity
public class Pet extends AbstractEntity<PetId> {
	OwnerId ownerId;
}
