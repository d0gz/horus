package samples.relationships;

import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

@Entity
public class Owner {
	@Id
	Long id;
	@OneToMany(mappedBy = "owner")
	List<Pet> pets;
}
