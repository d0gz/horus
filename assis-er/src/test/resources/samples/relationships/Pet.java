package samples.relationships;

import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

@Entity
public class Pet {
	@Id
	Long id;
	@ManyToOne
	Owner owner;
	@ManyToOne
	Pet mother;
}
