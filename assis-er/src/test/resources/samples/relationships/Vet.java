package samples.relationships;

import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

@Entity
public class Vet {
	@Id
	Long id;
	@ManyToMany
	Set<Specialty> specialties;
}
