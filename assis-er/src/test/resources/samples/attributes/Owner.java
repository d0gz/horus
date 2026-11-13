package samples.attributes;

import java.util.List;
import java.util.Map;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

@Entity
public class Owner {
	@Id
	Long id;
	String name;
	int a, b;
	List<String> tags;
	Map<String, List<Integer>> scores;
	java.time.LocalDate birth;
	byte[] photo;
	static int counter;
	transient String cache;
	@Transient
	String temp;
	@ManyToOne
	Owner parent;
}
