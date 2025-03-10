/* ========================================================================
 * PlantUML : a free UML diagram generator
 * ========================================================================
 *
 * (C) Copyright 2009-2024, Arnaud Roques
 *
 * Project Info:  https://plantuml.com
 * 
 * If you like this project or if you find it useful, you can support us at:
 * 
 * https://plantuml.com/patreon (only 1$ per month!)
 * https://plantuml.com/paypal
 * 
 * This file is part of PlantUML.
 *
 * PlantUML is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PlantUML distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 *
 * Original Author:  Arnaud Roques
 *
 * 
 */
package net.sourceforge.plantuml.svek;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.plantuml.StringUtils;
import net.sourceforge.plantuml.abel.Entity;
import net.sourceforge.plantuml.abel.EntityFactory;
import net.sourceforge.plantuml.cucadiagram.ICucaDiagram;
import net.sourceforge.plantuml.dot.DotData;
import net.sourceforge.plantuml.dot.DotSplines;
import net.sourceforge.plantuml.dot.Graphviz;
import net.sourceforge.plantuml.dot.GraphvizUtils;
import net.sourceforge.plantuml.dot.GraphvizVersion;
import net.sourceforge.plantuml.dot.GraphvizVersions;
import net.sourceforge.plantuml.dot.ProcessState;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.Moveable;
import net.sourceforge.plantuml.klimt.geom.Rankdir;
import net.sourceforge.plantuml.klimt.geom.XPoint2D;
import net.sourceforge.plantuml.security.SFile;
import net.sourceforge.plantuml.skin.UmlDiagramType;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.utils.Position;
import net.sourceforge.plantuml.vizjs.GraphvizJs;
import net.sourceforge.plantuml.vizjs.GraphvizJsRuntimeException;

public final class DotStringFactory implements Moveable {

	private final Bibliotekon bibliotekon;

	private final ColorSequence colorSequence;
	private final Cluster root;

	private Cluster current;
	private final UmlDiagramType umlDiagramType;
	private final ISkinParam skinParam;

	private DotSplines dotSplines;

	private final StringBounder stringBounder;

	public DotStringFactory(StringBounder stringBounder, DotData dotData) {
		this.skinParam = dotData.getSkinParam();
		this.umlDiagramType = dotData.getUmlDiagramType();

		this.colorSequence = new ColorSequence();
		this.stringBounder = stringBounder;
		this.root = new Cluster(dotData.getEntityFactory().getDiagram(), colorSequence, skinParam,
				dotData.getRootGroup());
		this.current = root;
		this.bibliotekon = new Bibliotekon(dotData.getLinks());
	}

	public DotStringFactory(StringBounder stringBounder, ICucaDiagram diagram) {
		this.skinParam = diagram.getSkinParam();
		this.umlDiagramType = diagram.getUmlDiagramType();

		this.colorSequence = new ColorSequence();
		this.stringBounder = stringBounder;
		this.root = new Cluster(diagram, colorSequence, skinParam, diagram.getEntityFactory().getRootGroup());
		this.current = root;
		this.bibliotekon = new Bibliotekon(diagram.getLinks());
	}

	public void addNode(SvekNode node) {
		current.addNode(node);
	}

	private double getHorizontalDzeta() {
		double max = 0;
		for (SvekEdge l : bibliotekon.allLines()) {
			final double c = l.getHorizontalDzeta(stringBounder);
			if (c > max)
				max = c;

		}
		return max / 10;
	}

	private double getVerticalDzeta() {
		double max = 0;
		for (SvekEdge l : bibliotekon.allLines()) {
			final double c = l.getVerticalDzeta(stringBounder);
			if (c > max)
				max = c;

		}
		if (root.diagram.getPragma().useKermor())
			return max / 100;
		return max / 10;
	}

	// ::comment when __CORE__
	private String createDotString(DotMode dotMode, String... dotStrings) {
		final StringBuilder sb = new StringBuilder();

		double nodesep = getHorizontalDzeta();
		if (nodesep < getMinNodeSep())
			nodesep = getMinNodeSep();

		if (skinParam.getNodesep() != 0)
			nodesep = skinParam.getNodesep();

		final String nodesepInches = SvekUtils.pixelToInches(nodesep);
		// Log.println("nodesep=" + nodesepInches);
		double ranksep = getVerticalDzeta();
		if (ranksep < getMinRankSep())
			ranksep = getMinRankSep();

		if (skinParam.getRanksep() != 0)
			ranksep = skinParam.getRanksep();

		final String ranksepInches = SvekUtils.pixelToInches(ranksep);
		// Log.println("ranksep=" + ranksepInches);
		sb.append("digraph unix {");
		SvekUtils.println(sb);

		for (String s : dotStrings) {
			if (s.startsWith("ranksep"))
				sb.append("ranksep=" + ranksepInches + ";");
			else if (s.startsWith("nodesep"))
				sb.append("nodesep=" + nodesepInches + ";");
			else
				sb.append(s);

			SvekUtils.println(sb);
		}
		// sb.append("newrank=true;");
		// SvekUtils.println(sb);
		sb.append("remincross=true;");
		SvekUtils.println(sb);
		sb.append("searchsize=500;");
		SvekUtils.println(sb);
		// if (OptionFlags.USE_COMPOUND) {
		// sb.append("compound=true;");
		// SvekUtils.println(sb);
		// }

		dotSplines = skinParam.getDotSplines();
		if (dotSplines == DotSplines.POLYLINE) {
			sb.append("splines=polyline;");
			SvekUtils.println(sb);
		} else if (dotSplines == DotSplines.ORTHO) {
			sb.append("splines=ortho;");
			sb.append("forcelabels=true;");
			SvekUtils.println(sb);
		}

		if (skinParam.getRankdir() == Rankdir.LEFT_TO_RIGHT) {
			sb.append("rankdir=LR;");
			SvekUtils.println(sb);
		}

		manageMinMaxCluster(sb);

		if (root.diagram.getPragma().useKermor()) {
			for (SvekEdge line : bibliotekon.lines0())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines);
			for (SvekEdge line : bibliotekon.lines1())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines);

			root.printCluster3_forKermor(sb, bibliotekon.allLines(), stringBounder, dotMode, getGraphvizVersion(),
					umlDiagramType);

		} else {
			root.printCluster1(sb, bibliotekon.allLines(), stringBounder);

			for (SvekEdge line : bibliotekon.lines0())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines);

			root.printCluster2(sb, bibliotekon.allLines(), stringBounder, dotMode, getGraphvizVersion(),
					umlDiagramType);

			for (SvekEdge line : bibliotekon.lines1())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines);

		}

		SvekUtils.println(sb);
		sb.append("}");

		return sb.toString();
	}
	// ::done

	private void manageMinMaxCluster(final StringBuilder sb) {
		final List<String> minPointCluster = new ArrayList<>();
		final List<String> maxPointCluster = new ArrayList<>();
		for (Cluster cluster : bibliotekon.allCluster()) {
			final String minPoint = cluster.getMinPoint(umlDiagramType);
			if (minPoint != null)
				minPointCluster.add(minPoint);

			final String maxPoint = cluster.getMaxPoint(umlDiagramType);
			if (maxPoint != null)
				maxPointCluster.add(maxPoint);

		}
		if (minPointCluster.size() > 0) {
			sb.append("{rank=min;");
			for (String s : minPointCluster) {
				sb.append(s);
				sb.append(" [shape=point,width=.01,label=\"\"]");
				sb.append(";");
			}
			sb.append("}");
			SvekUtils.println(sb);
		}
		if (maxPointCluster.size() > 0) {
			sb.append("{rank=max;");
			for (String s : maxPointCluster) {
				sb.append(s);
				sb.append(" [shape=point,width=.01,label=\"\"]");
				sb.append(";");
			}
			sb.append("}");
			SvekUtils.println(sb);
		}
	}

	private int getMinRankSep() {
		if (umlDiagramType == UmlDiagramType.ACTIVITY) {
			// return 29;
			return 40;
		}
		if (root.diagram.getPragma().useKermor())
			return 40;
		return 60;
	}

	private int getMinNodeSep() {
		if (umlDiagramType == UmlDiagramType.ACTIVITY) {
			// return 15;
			return 20;
		}
		return 35;
	}

	// ::uncomment when __CORE__
	// public GraphvizVersion getGraphvizVersion() {
	// return null;
	// }
	// ::done
	// ::comment when __CORE__
	private GraphvizVersion graphvizVersion;

	public GraphvizVersion getGraphvizVersion() {
		if (graphvizVersion == null)
			graphvizVersion = getGraphvizVersionInternal();

		return graphvizVersion;
	}

	private GraphvizVersion getGraphvizVersionInternal() {
		final Graphviz graphviz = GraphvizUtils.create(skinParam, "foo;", "svg");
		if (graphviz instanceof GraphvizJs)
			return GraphvizJs.getGraphvizVersion(false);

		final File f = graphviz.getDotExe();
		return GraphvizVersions.getInstance().getVersion(f);
	}

	public String getSvg(DotMode dotMode, BaseFile basefile, String[] dotOptions) throws IOException {
		String dotString = createDotString(dotMode, dotOptions);

		if (basefile != null) {
			final SFile f = basefile.getTraceFile("svek.dot");
			SvekUtils.traceString(f, dotString);
		}

		Graphviz graphviz = GraphvizUtils.create(skinParam, dotString, "svg");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			final ProcessState state = graphviz.createFile3(baos);
			baos.close();
			if (state.differs(ProcessState.TERMINATED_OK()))
				throw new IllegalStateException("Timeout4 " + state, state.getCause());

		} catch (GraphvizJsRuntimeException e) {
			System.err.println("GraphvizJsRuntimeException");
			graphvizVersion = GraphvizJs.getGraphvizVersion(true);
			dotString = createDotString(dotMode, dotOptions);
			graphviz = GraphvizUtils.create(skinParam, dotString, "svg");
			baos = new ByteArrayOutputStream();
			final ProcessState state = graphviz.createFile3(baos);
			baos.close();
			if (state.differs(ProcessState.TERMINATED_OK()))
				throw new IllegalStateException("Timeout4 " + state, state.getCause());

		}
		final byte[] result = baos.toByteArray();
		final String s = new String(result, UTF_8);

		if (basefile != null) {
			final SFile f = basefile.getTraceFile("svek.svg");
			SvekUtils.traceString(f, s);
		}

		return s;
	}

	public boolean illegalDotExe() {
		final Graphviz graphviz = GraphvizUtils.create(skinParam, "svg");
		if (graphviz instanceof GraphvizJs)
			return false;

		final File dotExe = graphviz.getDotExe();
		return dotExe == null || dotExe.isFile() == false || dotExe.canRead() == false;
	}

	public File getDotExe() {
		final Graphviz graphviz = GraphvizUtils.create(skinParam, "svg");
		return graphviz.getDotExe();
	}

	public void solve(EntityFactory entityFactory, final String svg) throws IOException, InterruptedException {
		if (svg.length() == 0)
			throw new EmptySvgException();

		final Pattern pGraph = Pattern.compile("(?m)\\<svg\\s+width=\"(\\d+)pt\"\\s+height=\"(\\d+)pt\"");
		final Matcher mGraph = pGraph.matcher(svg);
		if (mGraph.find() == false)
			throw new IllegalStateException();

		final int fullHeight = Integer.parseInt(mGraph.group(2));

		final Point2DFunction move = new YDelta(fullHeight);
		final SvgResult svgResult = new SvgResult(svg, move);
		for (SvekNode node : bibliotekon.allNodes()) {
			int idx = svg.indexOf("<title>" + node.getUid() + "</title>");
			if (node.getType() == ShapeType.RECTANGLE || node.getType() == ShapeType.RECTANGLE_HTML_FOR_PORTS
					|| node.getType() == ShapeType.RECTANGLE_WITH_CIRCLE_INSIDE || node.getType() == ShapeType.FOLDER
					|| node.getType() == ShapeType.DIAMOND || node.getType() == ShapeType.RECTANGLE_PORT) {
				final List<XPoint2D> points = svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS);
				final XPoint2D min = SvekUtils.getMinXY(points);
				node.moveDelta(min.getX(), min.getY());
			} else if (node.getType() == ShapeType.ROUND_RECTANGLE) {
				final int idx2 = svg.indexOf("d=\"", idx + 1);
				idx = svg.indexOf("points=\"", idx + 1);
				final List<XPoint2D> points;
				if (idx2 != -1 && (idx == -1 || idx2 < idx)) {
					// GraphViz 2.30
					points = svgResult.substring(idx2).extractList(SvgResult.D_EQUALS);
				} else {
					points = svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS);
					for (int i = 0; i < 3; i++) {
						idx = svg.indexOf("points=\"", idx + 1);
						points.addAll(svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS));
					}
				}
				final XPoint2D min = SvekUtils.getMinXY(points);
				node.moveDelta(min.getX(), min.getY());
			} else if (node.getType() == ShapeType.OCTAGON || node.getType() == ShapeType.HEXAGON) {
				idx = svg.indexOf("points=\"", idx + 1);
				final int starting = idx;
				final List<XPoint2D> points = svgResult.substring(starting).extractList(SvgResult.POINTS_EQUALS);
				final XPoint2D min = SvekUtils.getMinXY(points);
				// corner1.manage(minX, minY);
				node.moveDelta(min.getX(), min.getY());
				node.setPolygon(min.getX(), min.getY(), points);
			} else if (node.getType() == ShapeType.CIRCLE || node.getType() == ShapeType.OVAL) {
				final double cx = SvekUtils.getValue(svg, idx, "cx");
				final double cy = SvekUtils.getValue(svg, idx, "cy") + fullHeight;
				final double rx = SvekUtils.getValue(svg, idx, "rx");
				final double ry = SvekUtils.getValue(svg, idx, "ry");
				node.moveDelta(cx - rx, cy - ry);
			} else {
				throw new IllegalStateException(node.getType().toString() + " " + node.getUid());
			}
		}

		for (Cluster cluster : bibliotekon.allCluster()) {
			if (cluster.getGroup().isPacked())
				continue;

			int idx = getClusterIndex(svg, cluster.getColor());
			final int starting = idx;
			final List<XPoint2D> points = svgResult.substring(starting).extractList(SvgResult.POINTS_EQUALS);
			final XPoint2D min = SvekUtils.getMinXY(points);
			final XPoint2D max = SvekUtils.getMaxXY(points);
			cluster.setPosition(min, max);

			if (cluster.getTitleAndAttributeWidth() == 0 || cluster.getTitleAndAttributeHeight() == 0)
				continue;

			idx = getClusterIndex(svg, cluster.getTitleColor());
			final List<XPoint2D> pointsTitle = svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS);
			cluster.setTitlePosition(SvekUtils.getMinXY(pointsTitle));

			if (root.diagram.getPragma().useKermor()) {
				if (cluster.getGroup().getNotes(Position.TOP).size() > 0) {
					final List<XPoint2D> noteUp = svgResult.substring(getClusterIndex(svg, cluster.getColorNoteTop()))
							.extractList(SvgResult.POINTS_EQUALS);
					cluster.setNoteTopPosition(SvekUtils.getMinXY(noteUp));
				}
				if (cluster.getGroup().getNotes(Position.BOTTOM).size() > 0) {
					final List<XPoint2D> noteBottom = svgResult
							.substring(getClusterIndex(svg, cluster.getColorNoteBottom()))
							.extractList(SvgResult.POINTS_EQUALS);
					cluster.setNoteBottomPosition(SvekUtils.getMinXY(noteBottom));
				}
			}
		}

		for (SvekEdge line : bibliotekon.allLines())
			line.solveLine(svgResult);

		for (SvekEdge line : bibliotekon.allLines())
			line.manageCollision(bibliotekon.allNodes());

	}

	private int getClusterIndex(final String svg, int colorInt) {
		final String colorString = StringUtils.goLowerCase(StringUtils.sharp000000(colorInt));
		final String keyTitle1 = "=\"" + colorString + "\"";
		int idx = svg.indexOf(keyTitle1);
		if (idx == -1) {
			final String keyTitle2 = "stroke:" + colorString + ";";
			idx = svg.indexOf(keyTitle2);
		}
		if (idx == -1)
			throw new IllegalStateException("Cannot find color " + colorString);

		return idx;
	}
	// ::done

	public void openCluster(Entity g, ClusterHeader clusterHeader) {
		this.current = current.createChild(clusterHeader, colorSequence, skinParam, g);
		bibliotekon.addCluster(this.current);
	}

	public void closeCluster() {
		if (current.getParentCluster() == null)
			throw new IllegalStateException();

		this.current = current.getParentCluster();
	}

	public void moveDelta(double deltaX, double deltaY) {
		for (SvekNode sh : bibliotekon.allNodes())
			sh.moveDelta(deltaX, deltaY);

		for (SvekEdge line : bibliotekon.allLines())
			line.moveDelta(deltaX, deltaY);

		for (Cluster cl : bibliotekon.allCluster())
			cl.moveDelta(deltaX, deltaY);

	}

	public final Bibliotekon getBibliotekon() {
		return bibliotekon;
	}

	public ColorSequence getColorSequence() {
		return colorSequence;
	}

	public StringBounder getStringBounder() {
		return stringBounder;
	}

}
