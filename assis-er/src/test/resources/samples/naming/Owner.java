package samples.naming;

import jakarta.persistence.*;

@Entity
@Table(name = "owners")
public class Owner {
	@Id
	Long id;
	@Column(name = "full_name")
	String fullName;
	@Column(name = "e_mail", nullable = false)
	String email;
	@Column(nullable = false)
	String phone;
	@Column(name = "city")
	String city;
	@Column(name = "home address")
	String address;
	@Column(name = OwnerColumns.NOTE)
	String note;
}
