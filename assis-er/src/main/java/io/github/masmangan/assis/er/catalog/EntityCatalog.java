package io.github.masmangan.assis.er.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.masmangan.assis.er.EntityView;

/**
 * User catalog that overrules the extracted entity view. Read on every run,
 * never written back; problems become warnings, never errors.
 */
public final class EntityCatalog {

	private final List<CatalogEntry> entries;
	private final List<String> warnings;

	private EntityCatalog(List<CatalogEntry> entries, List<String> warnings) {
		this.entries = List.copyOf(entries);
		this.warnings = new ArrayList<>(warnings);
	}

	public static EntityCatalog empty() {
		return new EntityCatalog(List.of(), List.of());
	}

	/** Missing or unreadable file gives an empty catalog with one warning. */
	public static EntityCatalog from(Path file) {
		try {
			return fromText(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			return new EntityCatalog(List.of(),
					List.of("Catalog could not be read: " + file + " (" + e.getMessage() + ")"));
		}
	}

	static EntityCatalog of(CatalogEntry... entries) {
		return new EntityCatalog(List.of(entries), List.of());
	}

	public static EntityCatalog fromText(String text) {
		CatalogParser.Result result = CatalogParser.parse(text);
		return new EntityCatalog(result.entries(), result.warnings());
	}

	/** Applies the entries by phase, then in file order. */
	public EntityView apply(EntityView view) {
		List<CatalogEntry> ordered = new ArrayList<>(entries);
		ordered.sort(Comparator.comparing(CatalogEntry::phase));
		for (CatalogEntry entry : ordered) {
			view = entry.applyTo(view, warnings);
		}
		return view;
	}

	public List<String> warnings() {
		return List.copyOf(warnings);
	}

	public int size() {
		return entries.size();
	}
}
