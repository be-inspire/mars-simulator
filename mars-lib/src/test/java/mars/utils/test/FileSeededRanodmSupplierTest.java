package mars.utils.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import mars.utils.FileSeededRanodmSupplier;

class FileSeededRanodmSupplierTest {

	@Test
	void test() throws JsonSyntaxException, JsonIOException, IOException {
		final File file = new File("test1.json");
		final FileSeededRanodmSupplier g1 = new FileSeededRanodmSupplier(file);
		final FileSeededRanodmSupplier g2 = new FileSeededRanodmSupplier(file);
		assertEquals(g1.get().nextLong(), g2.get().nextLong(), "Different: " + g1 + ", " + g2);
	}

}
