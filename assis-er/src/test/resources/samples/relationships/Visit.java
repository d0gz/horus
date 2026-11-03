package samples.relationships;

import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

@Entity
public class Visit {
	@Id
	Long id;
	@OneToMany
	List<Pet> pets;
	@ManyToOne
	Address address;
}
