package samples.inferred;

import jakarta.persistence.*;

@Entity
public class Order extends AbstractAggregateRoot<Order> {
	@Id
	Long id;
}
