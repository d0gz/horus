package samples.inferred;

import java.util.Set;

import jakarta.persistence.*;

@Entity
public class Owner extends AbstractEntity<OwnerId> {
	String name;
	Set<PetId> petIds;
}
