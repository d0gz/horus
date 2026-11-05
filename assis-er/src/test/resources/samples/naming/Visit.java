package samples.naming;

import jakarta.persistence.*;

@Entity
@Table(name = "Visit")
public class Visit {
	@Id
	Long id;
}
