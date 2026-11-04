package samples.optionality;

import jakarta.persistence.*;

@Entity
public class Pet {
	@Id
	@Column(nullable = false)
	Long id;
	@Column(nullable = false)
	String name;
	@Basic(optional = false)
	String code;
	@Column(nullable = true)
	String nickname;
	@ManyToOne
	Owner owner;
	@ManyToOne(optional = false)
	Clinic clinic;
	@ManyToOne
	@JoinColumn(nullable = false)
	Breeder breeder;
	@ManyToOne(optional = true)
	@JoinColumn(nullable = false)
	Shelter shelter;
	@OneToOne
	Passport passport;
	@OneToOne(optional = false)
	Chip chip;
}
