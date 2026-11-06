package io.github.masmangan.assis.er.extract;

import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import io.github.masmangan.assis.er.types.Cardinality;

/**
 * JPA annotation names and readers. Annotations are matched by simple name, so
 * jakarta and javax, imported or fully qualified, are treated alike.
 */
final class Jpa {

	static final String ENTITY = "Entity";
	static final String TABLE = "Table";
	static final String COLUMN = "Column";
	static final String JOIN_COLUMN = "JoinColumn";
	static final String BASIC = "Basic";
	static final String ID = "Id";
	static final String TRANSIENT = "Transient";
	static final Set<String> ID_ANNOTATIONS = Set.of(ID, "EmbeddedId");
	static final Map<String, Cardinality> RELATIONSHIPS = Map.of(
			"ManyToOne", Cardinality.MANY_TO_ONE,
			"OneToMany", Cardinality.ONE_TO_MANY,
			"OneToOne", Cardinality.ONE_TO_ONE,
			"ManyToMany", Cardinality.MANY_TO_MANY);

	private Jpa() {
	}

	static boolean isEntity(TypeDeclaration<?> td) {
		return has(td, ENTITY);
	}

	static boolean has(NodeWithAnnotations<?> n, String simpleName) {
		return hasAny(n, Set.of(simpleName));
	}

	static boolean hasAny(NodeWithAnnotations<?> n, Set<String> simpleNames) {
		return n.getAnnotations().stream().anyMatch(a -> simpleNames.contains(a.getName().getIdentifier()));
	}

	static boolean isIdField(FieldDeclaration fd) {
		return hasAny(fd, ID_ANNOTATIONS);
	}

	static boolean isRelationshipField(FieldDeclaration fd) {
		return hasAny(fd, RELATIONSHIPS.keySet());
	}

	/** Not static, not transient and not a relationship. */
	static boolean isColumnField(FieldDeclaration fd) {
		return !fd.isStatic() && !fd.isTransient() && !has(fd, TRANSIENT) && !isRelationshipField(fd);
	}

	static boolean hasMappedBy(AnnotationExpr a) {
		return a instanceof NormalAnnotationExpr n
				&& n.getPairs().stream().anyMatch(p -> "mappedBy".equals(p.getNameAsString()));
	}

	static boolean isFalse(AnnotationExpr a, String member) {
		return a instanceof NormalAnnotationExpr n && n.getPairs().stream()
				.anyMatch(p -> member.equals(p.getNameAsString()) && p.getValue() instanceof BooleanLiteralExpr b
						&& !b.getValue());
	}

	static boolean hasFalseMember(FieldDeclaration fd, String annotation, String member) {
		return fd.getAnnotations().stream()
				.anyMatch(a -> annotation.equals(a.getName().getIdentifier()) && isFalse(a, member));
	}

	/**
	 * Literal name member of the annotation, or null when absent, blank or equal
	 * to the Java name.
	 */
	static String nameIfDifferent(NodeWithAnnotations<?> n, String annotation, String javaName) {
		for (AnnotationExpr a : n.getAnnotations()) {
			if (annotation.equals(a.getName().getIdentifier()) && a instanceof NormalAnnotationExpr na) {
				for (var p : na.getPairs()) {
					if ("name".equals(p.getNameAsString()) && p.getValue() instanceof StringLiteralExpr str
							&& !str.asString().isBlank() && !str.asString().equals(javaName)) {
						return str.asString();
					}
				}
			}
		}
		return null;
	}
}
