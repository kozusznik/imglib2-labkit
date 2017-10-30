package net.imglib2.atlas.control.brush;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.imglib2.*;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.atlas.ActionsAndBehaviours;
import net.imglib2.atlas.Holder;
import net.imglib2.atlas.color.ColorMapProvider;
import net.imglib2.atlas.control.brush.neighborhood.PaintPixelsGenerator;
import net.imglib2.atlas.labeling.Labeling;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.operators.ValueEquals;
import net.imglib2.util.Pair;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.ScrollBehaviour;

import bdv.viewer.ViewerPanel;
import net.imglib2.atlas.BrushOverlay;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

/**
 * A {@link TransformEventHandler} that changes an {@link AffineTransform3D}
 * through a set of {@link Behaviour}s.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 * @author Philipp Hanslovsky
 */
public class LabelBrushController
{

	final private ViewerPanel viewer;

	private List<RandomAccessibleInterval<BitType>> regions;

	private List<String> labels;

	private final PaintPixelsGenerator< BitType, ? extends Iterator<BitType> > pixelsGenerator;

	final private AffineTransform3D labelTransform;

	final private BrushOverlay brushOverlay;

	private int brushRadius = 5;

	private int currentLabel = 0;

	public BrushOverlay getBrushOverlay()
	{
		return brushOverlay;
	}

	private int getCurrentLabel()
	{
		return currentLabel;
	}

	private void setCurrentLabel(int index) {
		currentLabel = index;
		brushOverlay.setLabel( labels.get(index) );
	}

	public LabelBrushController(
			final ViewerPanel viewer,
			final Holder<Labeling> labels,
			final PaintPixelsGenerator< BitType, ? extends Iterator<BitType>> pixelsGenerator,
			final ActionsAndBehaviours behaviors,
			final ColorMapProvider colorProvider, AffineTransform3D labelTransform)
	{
		this.viewer = viewer;
		this.pixelsGenerator = pixelsGenerator;
		this.labelTransform = labelTransform;
		this.brushOverlay = new BrushOverlay( viewer, "", colorProvider );
		updateLabeling(labels.get());
		labels.notifier().add(this::updateLabeling);

		behaviors.addBehaviour( new PaintBehavior(true), "paint", "SPACE button1" );
		behaviors.addBehaviour( new PaintBehavior(false), "erase", "SPACE button2", "SPACE button3" );
		behaviors.addBehaviour( new FloodFillClick(true), "floodfill", "Y button1" );
		behaviors.addBehaviour( new FloodFillClick(false), "floodclear", "Y button2", "Y button3" );
		behaviors.addBehaviour( new ChangeBrushRadius(), "change brush radius", "SPACE scroll" );
		behaviors.addAction( new ChangeLabel(), "N" );
		behaviors.addBehaviour( new MoveBrush(), "move brush", "SPACE" );
	}

	void updateLabeling(Labeling labeling) {
		List<Map.Entry<String, RandomAccessibleInterval<BitType>>> entries =
				new ArrayList<>(labeling.regions().entrySet());
		this.labels = entries.stream().map(Map.Entry::getKey).collect(Collectors.toList());
		this.regions = entries.stream().map(Map.Entry::getValue).collect(Collectors.toList());
		setCurrentLabel(Math.min(currentLabel, regions.size()-1));
	}

	private RealPoint displayToImageCoordinates( final int x, final int y )
	{
		final RealPoint labelLocation = new RealPoint(3);
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );
		viewer.displayToGlobalCoordinates( labelLocation );
		labelTransform.applyInverse( labelLocation, labelLocation );
		return labelLocation;
	}

	private class PaintBehavior implements DragBehaviour
	{
		private boolean value;

		private RealPoint before;

		public PaintBehavior(boolean value) {
			this.value = value;
		}

		private void paint( final RealLocalizable coords)
		{
			synchronized ( viewer )
			{
				final int v = getValue();
				RandomAccessibleInterval<BitType> label = regions.get(v);
				final RandomAccessible<BitType> extended = Views.extendValue(label, new BitType(false));
				final Iterator< BitType > it = pixelsGenerator.getPaintPixels( extended, coords, viewer.getState().getCurrentTimepoint(), brushRadius );
				while ( it.hasNext() )
				{
					final BitType val = it.next();
					if ( Intervals.contains( label, ( Localizable ) it ) )
						val.set( value );
				}
			}

		}

		private void paint(RealPoint a, RealPoint b) {
			long distance = (long) distance(a, b);
			for ( long i = 0; i <= distance; ++i )
				paint( interpolate((double) i / (double) distance, a, b) );
		}

		RealLocalizable interpolate(double ratio, RealLocalizable a, RealLocalizable b) {
			RealPoint result = new RealPoint(a.numDimensions());
			for (int d = 0; d < result.numDimensions(); d++)
				result.setPosition(ratio * a.getDoublePosition(d) + (1 - ratio) * b.getDoublePosition(d), d);
			return result;
		}

		double distance(RealLocalizable a, RealLocalizable b) {
			return LinAlgHelpers.distance(asArray(a), asArray(b));
		}

		private double[] asArray(RealLocalizable a) {
			double[] result = new double[a.numDimensions()];
			a.localize(result);
			return result;
		}

		private int getValue()
		{
			return getCurrentLabel();
		}

		@Override
		public void init( final int x, final int y )
		{
			RealPoint coords = displayToImageCoordinates(x, y);
			this.before = coords;
			paint(coords);

			viewer.requestRepaint();
		}

		@Override
		public void drag( final int x, final int y )
		{
			RealPoint coords = displayToImageCoordinates(x, y);
			paint(before, coords );
			this.before = coords;
			brushOverlay.setPosition( x, y );
			viewer.requestRepaint();
		}

		@Override
		public void end( final int x, final int y )
		{
		}
	}

	private class ChangeBrushRadius implements ScrollBehaviour
	{
		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			if ( !isHorizontal )
			{
				if ( wheelRotation < 0 )
					brushRadius += 1;
				else if ( wheelRotation > 0 )
					brushRadius = Math.max( 0, brushRadius - 1 );

				brushOverlay.setRadius( brushRadius );
				// TODO request only overlays to repaint
				viewer.getDisplay().repaint();
			}
		}
	}

	private class ChangeLabel extends AbstractNamedAction {

		public ChangeLabel() {
			super("Next Label");
		}

		@Override
		public void actionPerformed(ActionEvent actionEvent) {
			setCurrentLabel(currentLabel >= (regions.size() - 1) ? 0 : currentLabel + 1);
			// TODO request only overlays to repaint
			viewer.getDisplay().repaint();
		}
	}

	private class MoveBrush implements DragBehaviour
	{

		@Override
		public void init( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );
			brushOverlay.setVisible( true );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ) );
			viewer.getDisplay().repaint();
		}

		@Override
		public void drag( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );
		}

		@Override
		public void end( final int x, final int y )
		{
			brushOverlay.setVisible( false );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			viewer.getDisplay().repaint();

		}
	}

	private class FloodFillClick implements ClickBehaviour
	{
		private final boolean value;

		FloodFillClick(boolean value) {
			this.value = value;
		}

		protected void floodFill( final RealLocalizable coords)
		{
			synchronized ( viewer )
			{
				RandomAccessibleInterval<BitType> region = regions.get(getCurrentLabel());
				LabelBrushController.floodFill(region, round(coords), new BitType(value));
			}
		}

		private Localizable round(final RealLocalizable realLocalizable) {
			Point point = new Point(2);
			for (int i = 0; i < point.numDimensions(); i++)
				point.setPosition((long) realLocalizable.getDoublePosition(i), i);
			return point;
		}

		@Override
		public void click(int x, int y) {
			floodFill( displayToImageCoordinates(x, y) );
			viewer.requestRepaint();
		}
	}

	public static <T extends Type<T> & ValueEquals<T>> void floodFill(RandomAccessibleInterval<T> image, Localizable seed, T value) {
		Filter<Pair<T, T>, Pair<T, T>> filter = (f, s) -> ! value.valueEquals(f.getB());
		ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> target = Views.extendValue(image, value);
		net.imglib2.algorithm.fill.FloodFill.fill(target, target, seed, value, new DiamondShape(1), filter);
	}
}
