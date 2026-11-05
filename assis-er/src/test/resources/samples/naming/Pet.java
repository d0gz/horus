package samples.naming;

import jakarta.persistence.*;

@Entity
@Table(schema = "clinic")
public class Pet {
	@Id
	@Column(name = "pet_id")
	Long id;
	@ManyToOne
	Owner owner;
}
