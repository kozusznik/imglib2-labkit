
package net.imglib2.labkit.segmentation;

import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.labkit.bdv.BdvLayer;
import net.imglib2.labkit.bdv.BdvShowable;
import net.imglib2.labkit.models.Holder;
import net.imglib2.labkit.models.SegmentationItem;
import net.imglib2.labkit.models.SegmentationResultsModel;
import net.imglib2.labkit.utils.Notifier;
import net.imglib2.labkit.utils.RandomAccessibleContainer;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileShortType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.view.Views;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class PredictionLayer implements BdvLayer {

	public static final String SIZE_OF_QUEUE = "predictionLayer.sizeOfQueue";
	
	private final Holder<? extends SegmentationItem> model;
	private final RandomAccessibleContainer<VolatileARGBType> segmentationContainer;
	private final SharedQueue queue = new SharedQueue(getSizeOfQueue());
	private final Holder<Boolean> visibility;
	private Notifier listeners = new Notifier();
	private RandomAccessibleInterval<? extends NumericType<?>> view;
	private AffineTransform3D transformation;
	private Set<SegmentationItem> alreadyRegistered = Collections.newSetFromMap(
		new WeakHashMap<>());

	public PredictionLayer(Holder<? extends SegmentationItem> model,
		Holder<Boolean> visibility)
	{
		this.model = model;
		SegmentationResultsModel selected = model.get().results();
		this.segmentationContainer = new RandomAccessibleContainer<>(
			getEmptyPrediction(selected));
		this.transformation = selected.transformation();
		this.view = Views.interval(segmentationContainer, selected.interval());
		this.visibility = visibility;
		model.notifier().add(() -> classifierChanged());
		registerListener(model.get());
	}

	private int getSizeOfQueue() {
		String val = System.getProperty(SIZE_OF_QUEUE);
		if (val != null) {
			try {
				return Integer.parseInt(val);
			}
			catch (NumberFormatException e) {
				// ignore
			}
		}
		return Runtime.getRuntime().availableProcessors();
	}

	private void registerListener(SegmentationItem segmenter) {
		if (alreadyRegistered.contains(segmenter)) return;
		alreadyRegistered.add(segmenter);
		segmenter.results().segmentationChangedListeners().add(
			() -> onTrainingCompleted(segmenter));
	}

	private void onTrainingCompleted(Segmenter segmenter) {
		if (model.get() == segmenter) {
			classifierChanged();
			visibility.set(true);
		}
	}

	private RandomAccessible<VolatileARGBType> getEmptyPrediction(
		SegmentationResultsModel selected)
	{
		return ConstantUtils.constantRandomAccessible(new VolatileARGBType(0),
			selected.interval().numDimensions());
	}

	private void classifierChanged() {
		SegmentationItem segmentationItem = model.get();
		registerListener(segmentationItem);
		SegmentationResultsModel selected = segmentationItem.results();
		RandomAccessible<VolatileARGBType> source = selected.hasResults() ? Views
			.extendValue(coloredVolatileView(selected), new VolatileARGBType(0))
			: getEmptyPrediction(selected);
		segmentationContainer.setSource(source);
		listeners.notifyListeners();
	}

	private RandomAccessibleInterval<VolatileARGBType> coloredVolatileView(
		SegmentationResultsModel selected)
	{
		ARGBType[] colors = selected.colors().toArray(new ARGBType[0]);
		return mapColors(colors, VolatileViews.wrapAsVolatile(selected
			.segmentation(), queue));
	}

	private RandomAccessibleInterval<VolatileARGBType> mapColors(
		ARGBType[] colors, RandomAccessibleInterval<VolatileShortType> source)
	{
		final Converter<VolatileShortType, VolatileARGBType> conv = (input,
			output) -> {
			final boolean isValid = input.isValid();
			output.setValid(isValid);
			if (isValid) output.set(colors[input.get().get()].get());
		};

		return Converters.convert(source, conv, new VolatileARGBType());
	}

	@Override
	public BdvShowable image() {
		return BdvShowable.wrap(view, transformation);
	}

	@Override
	public Notifier listeners() {
		return listeners;
	}

	@Override
	public Holder<Boolean> visibility() {
		return visibility;
	}

	@Override
	public String title() {
		return "Segmentation";
	}
}
