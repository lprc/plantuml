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
 * Contribution :  Hisashi Miyashita
 * Contribution :  Serge Wenger
 * 
 *
 */
package net.sourceforge.plantuml.svek;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.atmp.InnerStrategy;
import net.sourceforge.plantuml.StringUtils;
import net.sourceforge.plantuml.abel.Entity;
import net.sourceforge.plantuml.abel.GroupType;
import net.sourceforge.plantuml.abel.LeafType;
import net.sourceforge.plantuml.abel.Link;
import net.sourceforge.plantuml.core.UmlSource;
import net.sourceforge.plantuml.decoration.symbol.USymbolHexagon;
import net.sourceforge.plantuml.dot.DotData;
import net.sourceforge.plantuml.dot.ExeState;
import net.sourceforge.plantuml.dot.GraphvizUtils;
import net.sourceforge.plantuml.dot.UnparsableGraphvizException;
import net.sourceforge.plantuml.klimt.color.HColor;
import net.sourceforge.plantuml.klimt.drawing.UGraphic;
import net.sourceforge.plantuml.klimt.font.FontConfiguration;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.MagneticBorder;
import net.sourceforge.plantuml.klimt.geom.MagneticBorderNone;
import net.sourceforge.plantuml.klimt.geom.MinMax;
import net.sourceforge.plantuml.klimt.geom.XDimension2D;
import net.sourceforge.plantuml.klimt.geom.XRectangle2D;
import net.sourceforge.plantuml.klimt.shape.GraphicStrings;
import net.sourceforge.plantuml.log.Logme;
import net.sourceforge.plantuml.security.SecurityProfile;
import net.sourceforge.plantuml.security.SecurityUtils;
import net.sourceforge.plantuml.skin.Pragma;
import net.sourceforge.plantuml.skin.SkinParam;
import net.sourceforge.plantuml.skin.UmlDiagramType;
import net.sourceforge.plantuml.stereo.Stereotype;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.style.PName;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;
import net.sourceforge.plantuml.style.StyleSignature;
import net.sourceforge.plantuml.style.StyleSignatureBasic;
import net.sourceforge.plantuml.svek.image.EntityImageClass;
import net.sourceforge.plantuml.svek.image.EntityImageNote;
import net.sourceforge.plantuml.text.BackSlash;
import net.sourceforge.plantuml.utils.Log;

public final class GraphvizImageBuilder {

	private final DotData dotData;
	private final DotMode dotMode;

	private final UmlSource source;
	private final Pragma pragma;
	private Map<String, Double> maxX;

	private final SName styleName;
	private final DotStringFactory dotStringFactory;

	public GraphvizImageBuilder(DotData dotData, UmlSource source, Pragma pragma, SName styleName, DotMode dotMode,
			DotStringFactory dotStringFactory) {
		this.dotData = dotData;
		this.dotMode = dotMode;
		this.styleName = styleName;
		this.source = source;
		this.pragma = pragma;
		this.dotStringFactory = dotStringFactory;

	}

	final public StyleSignature getDefaultStyleDefinitionArrow(Stereotype stereotype) {
		StyleSignature result = StyleSignatureBasic.of(SName.root, SName.element, styleName, SName.arrow);
		if (stereotype != null)
			result = result.withTOBECHANGED(stereotype);

		return result;
	}

	private boolean isOpalisable(Entity entity) {
		if (dotData.getSkinParam().strictUmlStyle())
			return false;

		if (entity.isGroup())
			return false;

		if (entity.getLeafType() != LeafType.NOTE)
			return false;

		final Link single = onlyOneLink(entity);
		if (single == null)
			return false;

		return single.getOther(entity).getLeafType() != LeafType.NOTE;
	}

	static class EntityImageSimpleEmpty implements IEntityImage {

		private final HColor backColor;

		EntityImageSimpleEmpty(HColor backColor) {
			this.backColor = backColor;
		}

		public boolean isHidden() {
			return false;
		}

		public HColor getBackcolor() {
			return backColor;
		}

		public XDimension2D calculateDimension(StringBounder stringBounder) {
			return new XDimension2D(10, 10);
		}

		public MinMax getMinMax(StringBounder stringBounder) {
			return MinMax.fromDim(calculateDimension(stringBounder));
		}

		public XRectangle2D getInnerPosition(String member, StringBounder stringBounder, InnerStrategy strategy) {
			return null;
		}

		public void drawU(UGraphic ug) {
		}

		public ShapeType getShapeType() {
			return ShapeType.RECTANGLE;
		}

		public Margins getShield(StringBounder stringBounder) {
			return Margins.NONE;
		}

		public double getOverscanX(StringBounder stringBounder) {
			return 0;
		}

		@Override
		public MagneticBorder getMagneticBorder() {
			return new MagneticBorderNone();
		}

	}

	// Duplicate SvekResult / GeneralImageBuilder
	private HColor getBackcolor() {
		final Style style = StyleSignatureBasic.of(SName.root, SName.document)
				.getMergedStyle(dotData.getSkinParam().getCurrentStyleBuilder());
		return style.value(PName.BackGroundColor).asColor(dotData.getSkinParam().getIHtmlColorSet());
	}

	public IEntityImage buildImage(BaseFile basefile, String dotStrings[], boolean fileFormatOptionIsDebugSvek) {
		// ::comment when __CORE__
		if (dotData.isDegeneratedWithFewEntities(0))
			return new EntityImageSimpleEmpty(dotData.getSkinParam().getBackgroundColor());

		if (dotData.isDegeneratedWithFewEntities(1) && dotData.getUmlDiagramType() != UmlDiagramType.STATE) {
			final Entity single = dotData.getLeafs().iterator().next();
			final Entity group = single.getParentContainer();
			if (group.isRoot() && single.getUSymbol() instanceof USymbolHexagon == false) {
				final IEntityImage tmp = GeneralImageBuilder.createEntityImageBlock(single, dotData.getSkinParam(),
						dotData.isHideEmptyDescriptionForState(), dotData, null, null, dotData.getUmlDiagramType(),
						dotData.getLinks());
				return new EntityImageDegenerated(tmp, getBackcolor());
			}
		}
		dotData.removeIrrelevantSametail();

		printGroups(dotStringFactory, dotData.getRootGroup());
		printEntities(dotStringFactory, getUnpackagedEntities());

		for (Link link : dotData.getLinks()) {
			if (link.isRemoved())
				continue;

			try {
				final ISkinParam skinParam = dotData.getSkinParam();
				final FontConfiguration labelFont = getFontForLink(link, skinParam);

				final SvekEdge line = new SvekEdge(link, dotStringFactory.getColorSequence(), skinParam,
						dotStringFactory.getStringBounder(), labelFont, dotStringFactory.getBibliotekon(), pragma,
						dotStringFactory.getGraphvizVersion());

				dotStringFactory.getBibliotekon().addLine(line);

				if (isOpalisable(link.getEntity1())) {
					final SvekNode node = dotStringFactory.getBibliotekon().getNode(link.getEntity1());
					final SvekNode other = dotStringFactory.getBibliotekon().getNode(link.getEntity2());
					if (other != null) {
						((EntityImageNote) node.getImage()).setOpaleLine(line, node, other);
						line.setOpale(true);
					}
				} else if (isOpalisable(link.getEntity2())) {
					final SvekNode node = dotStringFactory.getBibliotekon().getNode(link.getEntity2());
					final SvekNode other = dotStringFactory.getBibliotekon().getNode(link.getEntity1());
					if (other != null) {
						((EntityImageNote) node.getImage()).setOpaleLine(line, node, other);
						line.setOpale(true);
					}
				}
			} catch (IllegalStateException e) {
				Logme.error(e);
			}
		}

		if (dotStringFactory.illegalDotExe())
			return error(dotStringFactory.getDotExe());

		if (basefile == null && (fileFormatOptionIsDebugSvek || isSvekTrace())
				&& (SecurityUtils.getSecurityProfile() == SecurityProfile.UNSECURE
						|| SecurityUtils.getSecurityProfile() == SecurityProfile.LEGACY
						|| SecurityUtils.getSecurityProfile() == SecurityProfile.SANDBOX))
			basefile = new BaseFile(null);

		final String svg;
		try {
			svg = dotStringFactory.getSvg(dotMode, basefile, dotStrings);
		} catch (IOException e) {
			return new GraphvizCrash(source.getPlainString(BackSlash.lineSeparator()),
					GraphvizUtils.graphviz244onWindows(), e);
		}
		if (svg.length() == 0)
			return new GraphvizCrash(source.getPlainString(BackSlash.lineSeparator()),
					GraphvizUtils.graphviz244onWindows(), new EmptySvgException());

		final String graphvizVersion = extractGraphvizVersion(svg);
		try {
			dotStringFactory.solve(dotData.getEntityFactory(), svg);
			final SvekResult result = new SvekResult(dotData, dotStringFactory);
			this.maxX = dotStringFactory.getBibliotekon().getMaxX();
			return result;
		} catch (Exception e) {
			Log.error("Exception " + e);
			throw new UnparsableGraphvizException(e, graphvizVersion, svg,
					source.getPlainString(BackSlash.lineSeparator()));
		}
		// ::done
		// ::uncomment when __CORE__
		// return null;
		// ::done

	}

	private FontConfiguration getFontForLink(Link link, final ISkinParam skinParam) {
		final Style style = getDefaultStyleDefinitionArrow(link.getStereotype()).getMergedStyle(link.getStyleBuilder());
		return style.getFontConfiguration(skinParam.getIHtmlColorSet());
	}

	private boolean isSvekTrace() {
		final String value = pragma.getValue("svek_trace");
		return "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
	}

	private String extractGraphvizVersion(String svg) {
		final Pattern pGraph = Pattern.compile("(?mi)!-- generated by graphviz(.*)");
		final Matcher mGraph = pGraph.matcher(svg);
		if (mGraph.find())
			return StringUtils.trin(mGraph.group(1));

		return null;
	}

	private Link onlyOneLink(Entity ent) {
		Link single = null;
		for (Link link : dotData.getLinks()) {
			if (link.isInvis())
				continue;
			if (link.contains(ent) == false)
				continue;

			if (single != null)
				return null;
			single = link;
		}
		return single;
	}

	// ::comment when __CORE__
	private IEntityImage error(File dotExe) {
		final List<String> msg = new ArrayList<>();
		msg.add("Dot Executable: " + dotExe);
		final ExeState exeState = ExeState.checkFile(dotExe);
		msg.add(exeState.getTextMessage());
		msg.add("Cannot find Graphviz. You should try");
		msg.add(" ");
		msg.add("@startuml");
		msg.add("testdot");
		msg.add("@enduml");
		msg.add(" ");
		msg.add(" or ");
		msg.add(" ");
		msg.add("java -jar plantuml.jar -testdot");
		msg.add(" ");
		return GraphicStrings.createForError(msg, false);
	}
	// ::done

	private void printEntities(DotStringFactory dotStringFactory, Collection<Entity> entities2) {
		for (Entity ent : entities2) {
			if (ent.isRemoved())
				continue;

			printEntity(dotStringFactory, ent);
		}
	}

	private void printEntity(DotStringFactory dotStringFactory, Entity ent) {
		if (ent.isRemoved())
			throw new IllegalStateException();

		final IEntityImage image = printEntityInternal(dotStringFactory, ent);
		final SvekNode node = dotStringFactory.getBibliotekon().createNode(ent, image,
				dotStringFactory.getColorSequence(), dotStringFactory.getStringBounder());
		dotStringFactory.addNode(node);
	}

	private IEntityImage printEntityInternal(DotStringFactory dotStringFactory, Entity ent) {
		if (ent.isRemoved())
			throw new IllegalStateException();

		if (ent.getSvekImage() == null) {
			ISkinParam skinParam = dotData.getSkinParam();
			if (skinParam.sameClassWidth()) {
				final double width = getMaxWidth();
				((SkinParam) skinParam).setParamSameClassWidth(width);
			}

			return GeneralImageBuilder.createEntityImageBlock(ent, skinParam, dotData.isHideEmptyDescriptionForState(),
					dotData, dotStringFactory.getBibliotekon(), dotStringFactory.getGraphvizVersion(),
					dotData.getUmlDiagramType(), dotData.getLinks());
		}
		return ent.getSvekImage();
	}

	private double getMaxWidth() {
		double result = 0;
		for (Entity ent : dotData.getLeafs()) {
			if (ent.getLeafType().isLikeClass() == false)
				continue;

			final IEntityImage im = new EntityImageClass(ent, dotData.getSkinParam(), dotData);
			final double w = im.calculateDimension(dotStringFactory.getStringBounder()).getWidth();
			if (w > result)
				result = w;

		}
		return result;
	}

	private Collection<Entity> getUnpackagedEntities() {
		final List<Entity> result = new ArrayList<>();
		for (Entity ent : dotData.getLeafs())
			if (dotData.getTopParent() == ent.getParentContainer())
				result.add(ent);

		return result;
	}

	private void printGroups(DotStringFactory dotStringFactory, Entity parent) {
		// System.err.println("PARENT=" + parent);
		final Collection<Entity> groups = dotData.getGroupHierarchy().getChildrenGroups(parent);
		// System.err.println("groups=" + groups);
		for (Entity g : groups) {
			if (g.isRemoved())
				continue;

			if (dotData.isEmpty(g) && g.getGroupType() == GroupType.PACKAGE) {
				g.muteToType(LeafType.EMPTY_PACKAGE);
				printEntity(dotStringFactory, g);
			} else {
				printGroup(dotStringFactory, g);
			}
		}
	}

	private void printGroup(DotStringFactory dotStringFactory, Entity g) {
		if (g.getGroupType() == GroupType.CONCURRENT_STATE)
			return;

		final ClusterHeader clusterHeader = new ClusterHeader(g, dotData.getSkinParam(), dotData,
				dotStringFactory.getStringBounder());
		dotStringFactory.openCluster(g, clusterHeader);
		this.printEntities(dotStringFactory, g.leafs());

		printGroups(dotStringFactory, g);

		dotStringFactory.closeCluster();
	}

	public String getWarningOrError(int warningOrError) {
		if (maxX == null)
			return "";

		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Double> ent : maxX.entrySet())
			if (ent.getValue() > warningOrError) {
				sb.append(ent.getKey() + " is overpassing the width limit.");
				sb.append("\n");
			}

		return sb.length() == 0 ? "" : sb.toString();
	}
}
