
package net.imglib2.labkit.inputimage;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imglib2.img.display.imagej.ImgPlusViews;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// TODO: make this avaiblable in imglib2
public class ImgPlusViewsOld {

	public static <T> ImgPlus<T> sortAxes(ImgPlus<T> in,
		final List<AxisType> order)
	{
		if (in.numDimensions() == 0) return in;
		boolean changend = true;
		while (changend) {
			changend = false;
			for (int i = 0; i < in.numDimensions() - 1; i++) {
				if (order.indexOf(in.axis(i).type()) > order.indexOf(in.axis(i + 1)
					.type()))
				{
					in = ImgPlusViews.permute((ImgPlus) in, i, i + 1);
					changend = true;
				}
			}
		}
		return in;
	}

	/**
	 * Change the axis types of an image, such that each axis is uniquely typed as
	 * X, Y, Z, channel or time. Existing unique axis of type: X, Y, Z, channel or
	 * time are preserved.
	 */
	public static <T> ImgPlus<T> fixAxes(final ImgPlus<T> in,
		final List<AxisType> allowed)
	{
		final List<AxisType> newAxisTypes = fixAxes(getAxes(in), allowed);
		final CalibratedAxis[] newAxes = IntStream.range(0, in.numDimensions())
			.mapToObj(i -> {
				final CalibratedAxis newAxis = in.axis(i).copy();
				newAxis.setType(newAxisTypes.get(i));
				return newAxis;
			}).toArray(CalibratedAxis[]::new);
		return new ImgPlus<>(in.getImg(), in.getName(), newAxes);
	}

	// -- Helper methods --

	private static List<AxisType> fixAxes(final List<AxisType> in,
		final List<AxisType> allowed)
	{
		final List<AxisType> unusedAxis = new ArrayList<>(allowed);
		unusedAxis.removeAll(in);
		final Predicate<AxisType> isDuplicate = createIsDuplicatePredicate();
		final Predicate<AxisType> replaceIf = axis -> isDuplicate.test(axis) ||
			!allowed.contains(axis);
		final Iterator<AxisType> iterator = unusedAxis.iterator();
		final Supplier<AxisType> replacements = () -> iterator.hasNext() ? iterator
			.next() : Axes.unknown();
		return replaceMatches(in, replaceIf, replacements);
	}

	// NB: Package-private to allow tests.
	static List<AxisType> getAxes(final ImgPlus<?> in) {
		return IntStream.range(0, in.numDimensions()).mapToObj(in::axis).map(
			CalibratedAxis::type).collect(Collectors.toList());
	}

	// NB: Package-private to allow tests.
	private static <T> Predicate<T> createIsDuplicatePredicate() {
		final Set<T> before = new HashSet<>();
		return element -> {
			final boolean isDuplicate = before.contains(element);
			if (!isDuplicate) before.add(element);
			return isDuplicate;
		};
	}

	// NB: Package-private to allow tests.
	private static <T> List<T> replaceMatches(final List<T> in,
		final Predicate<T> predicate, final Supplier<T> replacements)
	{
		return in.stream().map(value -> predicate.test(value) ? replacements.get()
			: value).collect(Collectors.toList());
	}
}
