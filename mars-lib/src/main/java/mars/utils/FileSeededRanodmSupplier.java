package mars.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

/**
 * A random number supplier that savesand loads the seed form a fil.
 * 
 * This is useful to reproduce the same random behaviopur through different
 * runs.
 * 
 * @author mperrando
 *
 */
public class FileSeededRanodmSupplier implements Supplier<Random> {

	private final Random source;
	private final long seed;

	public FileSeededRanodmSupplier(File file) throws JsonSyntaxException, JsonIOException, IOException {
		if (file.exists()) {
			try (var r = new FileReader(file)) {
				seed = new Gson().fromJson(r, Long.class);
			}
		} else {
			seed = new Random().nextLong();
			try (var w = new FileWriter(file)) {
				new Gson().toJson(seed, w);
			}
		}
		source = new Random(seed);
	}

	@Override
	public Random get() {
		return new Random(source.nextLong());
	}

	@Override
	public String toString() {
		return "Random generator with seed: " + seed;
	}
}
