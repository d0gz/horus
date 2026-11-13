package samples.primitivenull;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
public class Item {
	@Id
	long id;
	int plain;
	@Column(nullable = true)
	int explicitNull;
	@Basic(optional = true)
	double explicitOptional;
	@Column(nullable = false)
	Integer boxedNotNull;
	Integer boxed;
	@NotNull
	String validated;
	@NotNull
	@Column(nullable = true)
	String validatedWins;
}
