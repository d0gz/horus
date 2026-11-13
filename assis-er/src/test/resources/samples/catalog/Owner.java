package samples.catalog;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
public class Owner {
	@Id
	Long id;
	@OneToMany(mappedBy = "owner")
	List<Pet> pets;
}
