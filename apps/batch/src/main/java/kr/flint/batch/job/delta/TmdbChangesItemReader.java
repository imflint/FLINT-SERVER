package kr.flint.batch.job.delta;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import kr.flint.batch.job.TmdbIdLine;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbChangesRes;

public class TmdbChangesItemReader implements ItemStreamReader<TmdbIdLine> {

    private static final int MAX_CHANGE_WINDOW_DAYS = 14;

    private final TmdbClient tmdbClient;
    private final MediaType mediaType;
    private final String startDate;
    private final String endDate;
    private final String contextPrefix;

    private int page = 1;
    private int itemIndex;
    private int totalPages = 1;
    private List<TmdbIdLine> pageItems;

    public TmdbChangesItemReader(
        TmdbClient tmdbClient,
        MediaType mediaType,
        String startDate,
        String endDate
    ) {
        this.tmdbClient = tmdbClient;
        this.mediaType = mediaType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.contextPrefix = "tmdbChanges." + mediaType.name().toLowerCase() + "." + startDate + "." + endDate;
        validateDateRange();
    }

    @Override
    public TmdbIdLine read() {
        while (page <= totalPages) {
            if (pageItems == null) {
                loadPage();
            }
            if (itemIndex < pageItems.size()) {
                return pageItems.get(itemIndex++);
            }
            page++;
            itemIndex = 0;
            pageItems = null;
        }
        return null;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        page = executionContext.getInt(contextPrefix + ".page", 1);
        itemIndex = executionContext.getInt(contextPrefix + ".itemIndex", 0);
        totalPages = executionContext.getInt(contextPrefix + ".totalPages", 1);
        pageItems = null;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(contextPrefix + ".page", page);
        executionContext.putInt(contextPrefix + ".itemIndex", itemIndex);
        executionContext.putInt(contextPrefix + ".totalPages", totalPages);
    }

    @Override
    public void close() throws ItemStreamException {
        pageItems = null;
    }

    private void loadPage() {
        TmdbChangesRes response = mediaType == MediaType.TV
            ? tmdbClient.getTvChanges(startDate, endDate, page)
            : tmdbClient.getMovieChanges(startDate, endDate, page);
        totalPages = Math.max(page, response.totalPages());
        pageItems = response.results() == null ? List.of() : response.results().stream()
            .filter(result -> result.id() != null)
            .map(result -> new TmdbIdLine(result.id(), null, null, result.adult()))
            .toList();
        if (itemIndex > pageItems.size()) {
            throw new ItemStreamException("TMDB changes checkpoint exceeds current page size");
        }
    }

    private void validateDateRange() {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        long days = ChronoUnit.DAYS.between(start, end);
        if (days < 0 || days > MAX_CHANGE_WINDOW_DAYS) {
            throw new IllegalArgumentException("TMDB changes range must be between 0 and 14 days");
        }
    }
}
