package samples.inferred;

import jakarta.persistence.*;

@Entity
public class Vet extends AbstractEntity<OtherId> {
	@EmbeddedId
	VetId id;
}
