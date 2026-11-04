package samples.inferred;

import jakarta.persistence.*;

@Entity
public class Clinic extends AbstractEntity<ClinicId> {
	@Id
	Long number;
}
