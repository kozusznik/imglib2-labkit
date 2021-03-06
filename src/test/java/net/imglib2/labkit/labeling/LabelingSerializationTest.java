
package net.imglib2.labkit.labeling;

import com.google.gson.Gson;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.roi.IterableRegion;
import net.imglib2.sparse.SparseIterableRegion;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.Views;
import org.junit.Test;
import org.scijava.Context;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * @author Matthias Arzt
 */
public class LabelingSerializationTest {

	@Test
	public void testJson() throws IOException {
		testSerialization(exampleLabeling(), "json");
		testSerialization(emptyLabeling(), "json");
	}

	@Test
	public void testTif() throws IOException {
		testSerialization(exampleLabeling(), "tif");
		testSerialization(emptyLabeling(), "tif");
	}

	private void testSerialization(Labeling labeling, String extension)
		throws IOException
	{
		final String filename = tempFileWithExtension(extension);
		LabelingSerializer serializer = new LabelingSerializer(new Context());
		serializer.save(labeling, filename);
		Labeling deserialized = serializer.open(filename);
		assertTrue(labelingsEqual(labeling, deserialized));
	}

	private String tempFileWithExtension(String extension) throws IOException {
		File file = File.createTempFile("test-", "." + extension);
		file.deleteOnExit();
		return file.getAbsolutePath();
	}

	private boolean labelingsEqual(Labeling expected, Labeling actual) {
		boolean[] value = { true };
		Views.interval(Views.pair(expected, actual), expected).forEach(p -> {
			value[0] &= setsEqual(toStrings(p.getA()), toStrings(p.getB()));
		});
		return value[0];
	}

	private Set<String> toStrings(Set<Label> input) {
		return input.stream().map(Label::name).collect(Collectors.toSet());
	}

	private <T> boolean setsEqual(Set<T> a, Set<T> b) {
		return a.size() == b.size() && a.containsAll(b);
	}

	private static Labeling exampleLabeling() {
		IterableRegion<BitType> region1 = exampleRegion(10, 10);
		IterableRegion<BitType> region2 = exampleRegion(42, 12);
		Map<String, IterableRegion<BitType>> regions = new TreeMap<>();
		regions.put("A", region1);
		regions.put("B", region2);
		return Labeling.fromMap(regions);
	}

	private static Labeling emptyLabeling() {
		return Labeling.createEmpty(Collections.emptyList(), new FinalInterval(2,
			2));
	}

	private static IterableRegion<BitType> exampleRegion(long... position) {
		SparseIterableRegion roi = new SparseIterableRegion(new FinalInterval(100,
			200));
		RandomAccess<BitType> ra = roi.randomAccess();
		ra.setPosition(position);
		ra.get().set(true);
		return roi;
	}
}
