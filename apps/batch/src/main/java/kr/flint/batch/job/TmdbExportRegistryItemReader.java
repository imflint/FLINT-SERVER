package kr.flint.batch.job;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.file.mapping.JsonLineMapper;
import org.springframework.core.io.Resource;

import kr.flint.batch.repository.TmdbCatalogEntryJdbcRepository;
import kr.flint.content.domain.MediaType;

public class TmdbExportRegistryItemReader implements ItemStreamReader<TmdbIdLine> {

	private final Resource resource;
	private final MediaType mediaType;
	private final LocalDate exportDate;
	private final int scanSize;
	private final TmdbCatalogEntryJdbcRepository catalogRepository;
	private final JsonLineMapper lineMapper = new JsonLineMapper();
	private final String contextKey;

	private BufferedReader reader;
	private long currentLine;
	private long batchStartLine;
	private List<TmdbIdLine> candidates = List.of();
	private int candidateIndex;

	public TmdbExportRegistryItemReader(
		Resource resource,
		MediaType mediaType,
		LocalDate exportDate,
		int scanSize,
		TmdbCatalogEntryJdbcRepository catalogRepository
	) {
		this.resource = resource;
		this.mediaType = mediaType;
		this.exportDate = exportDate;
		this.scanSize = scanSize;
		this.catalogRepository = catalogRepository;
		this.contextKey = "tmdbExportRegistry." + mediaType.name().toLowerCase() + ".line";
	}

	@Override
	public TmdbIdLine read() throws Exception {
		while (candidateIndex >= candidates.size()) {
			if (!loadNextBatch()) {
				return null;
			}
		}
		return candidates.get(candidateIndex++);
	}

	@Override
	public void open(ExecutionContext executionContext) throws ItemStreamException {
		try {
			currentLine = executionContext.getLong(contextKey, 0L);
			batchStartLine = currentLine;
			reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
			for (long skipped = 0; skipped < currentLine; skipped++) {
				if (reader.readLine() == null) {
					throw new ItemStreamException("TMDB export checkpoint exceeds file length");
				}
			}
		} catch (IOException exception) {
			throw new ItemStreamException("Failed to open TMDB export", exception);
		}
	}

	@Override
	public void update(ExecutionContext executionContext) throws ItemStreamException {
		long checkpoint = candidateIndex < candidates.size() ? batchStartLine : currentLine;
		executionContext.putLong(contextKey, checkpoint);
	}

	@Override
	public void close() throws ItemStreamException {
		if (reader == null) {
			return;
		}
		try {
			reader.close();
		} catch (IOException exception) {
			throw new ItemStreamException("Failed to close TMDB export", exception);
		}
	}

	private boolean loadNextBatch() throws Exception {
		batchStartLine = currentLine;
		List<TmdbIdLine> lines = new ArrayList<>(scanSize);
		while (lines.size() < scanSize) {
			String raw = reader.readLine();
			if (raw == null) {
				break;
			}
			currentLine++;
			TmdbIdLine line = TmdbIdLine.fromMap(lineMapper.mapLine(raw, Math.toIntExact(currentLine)));
			if (line != null && line.id() != null) {
				lines.add(line);
			}
		}
		if (lines.isEmpty()) {
			return false;
		}
		candidates = catalogRepository.registerExportBatch(mediaType, exportDate, lines);
		candidateIndex = 0;
		return true;
	}
}
