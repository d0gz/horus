package samples.relationships;

import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

@Entity
public class User {
	@Id
	Long id;
	@OneToOne
	Profile profile;
}
