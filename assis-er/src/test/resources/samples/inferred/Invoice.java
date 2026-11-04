package samples.inferred;

import jakarta.persistence.*;

@Entity
public class Invoice {
	@Id
	SharedId id;
	Order order;
}
