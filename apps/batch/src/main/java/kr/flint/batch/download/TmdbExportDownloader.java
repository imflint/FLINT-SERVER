package kr.flint.batch.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPInputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import kr.flint.batch.config.BatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// TMDB daily export(.json.gz)를 다운로드 후 gunzip 하여 라인 단위 JSON Resource를 반환한다.
@Component
@RequiredArgsConstructor
@Slf4j
public class TmdbExportDownloader {

	public enum ExportType {
		MOVIE("movie_ids"),
		TV("tv_series_ids");

		private final String filePrefix;

		ExportType(String filePrefix) {
			this.filePrefix = filePrefix;
		}
	}

	private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("MM_dd_yyyy");

	private final BatchProperties batchProperties;
	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.build();

	public Resource fetchAsLineResource(ExportType type, LocalDate date) throws IOException, InterruptedException {
		String fileName = "%s_%s.json.gz".formatted(type.filePrefix, EXPORT_DATE.format(date));
		String url = batchProperties.tmdb().exportBaseUrl() + "/" + fileName;

		Path outputDir = Path.of(batchProperties.tmdb().downloadDir());
		Files.createDirectories(outputDir);
		Path gzPath = outputDir.resolve(fileName);
		Path jsonPath = outputDir.resolve(fileName.replace(".gz", ""));

		if (!Files.exists(jsonPath)) {
			log.info("downloading TMDB export {} -> {}", url, gzPath);
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofMinutes(5))
				.GET()
				.build();
			HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
			if (resp.statusCode() != 200) {
				throw new IOException("TMDB export download failed: HTTP " + resp.statusCode() + " for " + url);
			}
			try (InputStream body = resp.body()) {
				Files.copy(body, gzPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			log.info("decompressing {} -> {}", gzPath, jsonPath);
			try (GZIPInputStream gz = new GZIPInputStream(Files.newInputStream(gzPath))) {
				Files.copy(gz, jsonPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			Files.deleteIfExists(gzPath);
		} else {
			log.info("reusing cached TMDB export {}", jsonPath);
		}

		return new FileSystemResource(jsonPath);
	}
}
