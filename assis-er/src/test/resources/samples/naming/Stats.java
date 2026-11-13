package samples.naming;

import jakarta.persistence.*;

@Entity
@Table(name = "pet%stats")
public class Stats {
	@Id
	Long id;
}
