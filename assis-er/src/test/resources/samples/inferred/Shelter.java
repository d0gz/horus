package samples.inferred;

import java.util.Map;
import java.util.Optional;

import jakarta.persistence.*;

@Entity
public class Shelter {
	@Id
	Long id;
	Optional<PetId> mascot;
	Map<String, PetId> pets;
	Wrapper<PetId> featured;
}
