package samples.relationships;

import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

@Entity
public class Profile {
	@Id
	Long id;
	@OneToOne(mappedBy = "profile")
	User user;
}
