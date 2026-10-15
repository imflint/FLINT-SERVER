package kr.flint.batch.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
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

	public synchronized Resource fetchAsLineResource(ExportType type, LocalDate date) throws IOException, InterruptedException {
		String fileName = "%s_%s.json.gz".formatted(type.filePrefix, EXPORT_DATE.format(date));
		String url = batchProperties.tmdb().exportBaseUrl() + "/" + fileName;

		Path outputDir = Path.of(batchProperties.tmdb().downloadDir());
		Files.createDirectories(outputDir);
		Path jsonPath = outputDir.resolve(fileName.replace(".gz", ""));
		Path completionPath = outputDir.resolve(jsonPath.getFileName() + ".complete");
		if (verifiedCache(jsonPath, completionPath)) {
			log.info("reusing verified TMDB export {}", jsonPath);
			return new FileSystemResource(jsonPath);
		}
		String attempt = UUID.randomUUID().toString();
		Path gzPart = outputDir.resolve(fileName + "." + attempt + ".part");
		Path jsonPart = outputDir.resolve(jsonPath.getFileName() + "." + attempt + ".part");
		Path completionPart = outputDir.resolve(completionPath.getFileName() + "." + attempt + ".part");
		try {
			log.info("downloading TMDB export type={} date={}", type, date);
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofMinutes(5))
				.GET()
				.build();
			HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = resp.body()) {
				if (resp.statusCode() != 200) {
					throw new IOException("TMDB export download failed: HTTP " + resp.statusCode());
				}
				Files.copy(body, gzPart);
			}
			long expectedSize = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
			if (expectedSize >= 0 && Files.size(gzPart) != expectedSize) {
				throw new IOException("TMDB export download is incomplete");
			}
			try (GZIPInputStream gz = new GZIPInputStream(Files.newInputStream(gzPart))) {
				Files.copy(gz, jsonPart);
			}
			long size = Files.size(jsonPart);
			if (size == 0) throw new IOException("TMDB export is empty");
			Properties completion = new Properties();
			completion.setProperty("size", Long.toString(size));
			completion.setProperty("sha256", checksum(jsonPart));
			try (var output = Files.newOutputStream(completionPart)) {
				completion.store(output, null);
			}
			Files.move(jsonPart, jsonPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			Files.move(completionPart, completionPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(gzPart);
			Files.deleteIfExists(jsonPart);
			Files.deleteIfExists(completionPart);
		}

		return new FileSystemResource(jsonPath);
	}

	private boolean verifiedCache(Path jsonPath, Path completionPath) {
		if (!Files.isRegularFile(jsonPath) || !Files.isRegularFile(completionPath)) return false;
		try (InputStream input = Files.newInputStream(completionPath)) {
			Properties completion = new Properties();
			completion.load(input);
			long size = Long.parseLong(completion.getProperty("size", "0"));
			return size > 0 && size == Files.size(jsonPath)
				&& checksum(jsonPath).equals(completion.getProperty("sha256"));
		} catch (IOException | IllegalArgumentException failure) {
			return false;
		}
	}

	private String checksum(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(path)) {
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable");
		}
	}
}
