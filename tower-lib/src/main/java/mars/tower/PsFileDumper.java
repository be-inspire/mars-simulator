package mars.tower;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PsFileDumper extends PsListenerAdapter {

	private final static Logger LOGGER = LoggerFactory.getLogger(PsFileDumper.class);

	private static final String PREFIX = "PS-";

	private final Path dir;

	public PsFileDumper(Path path) throws IOException {
		dir = Files.createDirectories(path);
		Files.walk(dir).filter(Files::isRegularFile).filter(f -> f.getFileName().toString().startsWith(PREFIX))
				.forEach(PsFileDumper::delete);
	}

	private static void delete(Path path) {
		try {
			Files.delete(path);
		} catch (final IOException ignore) {
			ignore.printStackTrace();
		}
	}

	@Override
	public void logUpdated(PitStop ps, PsLog log) {
		final Path psPath = dir.resolve(String.format(PREFIX + "%09d", ps.id));
		try (var pw = new PrintWriter(Files.newOutputStream(psPath, CREATE, APPEND))) {
			pw.println(log);
		} catch (final IOException e) {
			LOGGER.error("Cannot update PS file", e);
		}
	}
}
