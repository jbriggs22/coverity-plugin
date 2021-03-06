/*******************************************************************************
 * Copyright (c) 2017 Synopsys, Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Synopsys, Inc - initial implementation and documentation
 *******************************************************************************/
package jenkins.plugins.coverity;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.util.ChartUtil;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import jenkins.model.Jenkins;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;

import java.awt.*;
import java.util.List;


/**
 * Project-level action for Coverity. This is used to to display the history graph.
 */
public class CoverityProjectAction implements Action {

    private final AbstractProject<?, ?> project;

    public CoverityProjectAction(AbstractProject<?, ?> project) {
        this.project = project;
    }

    public AbstractProject<?, ?> getProject() {
        return project;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "coverity";
    }

    public Graph getGraph() {
        return new GraphImpl();
    }

    private class GraphImpl extends Graph {
        protected GraphImpl() {
            super(-1, 600, 300); // no caching, because it doesn't deal with deleted builds
        }

        protected DataSetBuilder<String, ChartLabel> createDataSet() {
            DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<String, ChartLabel>();
            AbstractBuild<?, ?> build = project.getLastCompletedBuild();
            while(build != null) {
                final List<CoverityBuildAction> actions = build.getActions(CoverityBuildAction.class);

                for(CoverityBuildAction action : actions) {
                    if(action != null && action.getDefects() != null && action.getGraphDisplayName() != null) {
                        data.add(action.getDefects().size(), action.getGraphDisplayName(), new ChartLabel(build));
                    }
                }
                build = build.getPreviousBuild();
            }
            return data;

        }

        protected JFreeChart createGraph() {
            final CategoryDataset dataset = createDataSet().build();

            List rows = dataset.getColumnKeys();
            for(int i = 0; i < rows.size(); i ++){
                Object row = rows.get(i);
                if(row == null){
                    throw new NullPointerException();
                }
            }


            final JFreeChart chart = ChartFactory.createStackedAreaChart(null, // chart
                    // title
                    null, // unused
                    "Defect Count", // range axis label
                    dataset, // data
                    PlotOrientation.VERTICAL, // orientation
                    true, // include legend
                    true, // tooltips
                    false // urls
            );

            chart.setBackgroundPaint(Color.white); // Originally white

            final CategoryPlot plot = chart.getCategoryPlot();

            // plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
            plot.setBackgroundPaint(Color.white); // Originally white
            plot.setOutlinePaint(null);
            plot.setForegroundAlpha(0.8f);
            plot.setDomainGridlinesVisible(true);
            plot.setDomainGridlinePaint(Color.white);
            plot.setRangeGridlinesVisible(true);
            plot.setRangeGridlinePaint(Color.black); // Originally black

            CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
            plot.setDomainAxis(domainAxis);
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
            domainAxis.setLowerMargin(0.0);
            domainAxis.setUpperMargin(0.0);
            domainAxis.setCategoryMargin(0.0);

            final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
            rangeAxis.setAutoRange(true);

            plot.setRenderer(new ChartItemRenderer(dataset));

            // crop extra space around the graph
            plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

            return chart;
        }
    }

    private static class ChartItemRenderer extends StackedAreaRenderer2 {
        private final CategoryDataset ds;

        public ChartItemRenderer(CategoryDataset ds) {
            this.ds = ds;
        }

        @Override
        public Paint getItemPaint(int row, int column) {
            ChartLabel key = (ChartLabel) ds.getColumnKey(column);
            if(key.getColor() != null) return key.getColor();
            return super.getItemPaint(row, column);
        }

        @Override
        public String generateURL(CategoryDataset dataset, int row,
                                  int column) {
            ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
            return label.getUrl() + CoverityBuildAction.BUILD_ACTION_IDENTIFIER;
        }

        @Override
        public String generateToolTip(CategoryDataset dataset, int row,
                                      int column) {
            ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
            int defects = 0;
            for(CoverityBuildAction a : label.build.getActions(CoverityBuildAction.class)) {
                defects += a.getDefects().size();
            }
            return label.build.getDisplayName() + " has " + defects + " total defects";
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == this) {
                return true;
            } else if(!(obj instanceof ChartItemRenderer)) {
                return false;
            } else {
                ChartItemRenderer that = (ChartItemRenderer)obj;
                return this.ds != that.ds ? false : super.equals(obj);
            }
        }

        @Override
        public int hashCode() {
            int result = ds.hashCode();
            result = 31 * result + super.hashCode();
            return result;
        }
    }

    private static class ChartLabel implements Comparable<ChartLabel> {
        private AbstractBuild build;

        public ChartLabel(AbstractBuild build) {
            this.build = build;
        }

        public String getUrl() {
            return Jenkins.getInstance().getRootUrl() + build.getUrl();
        }

        public int compareTo(ChartLabel that) {
            return build.number - that.build.number;
        }

        @Override
        public boolean equals(Object o) {
            if(!(o instanceof ChartLabel)) {
                return false;
            }
            ChartLabel that = (ChartLabel) o;
            return this.build == that.build;
        }

        public Color getColor() {
            return null;
        }

        @Override
        public int hashCode() {
            return build.getDisplayName().hashCode();
        }

        @Override
        public String toString() {
            String l = build.getDisplayName();
            
            String s = build.getBuiltOnStr();
            if(s != null)
                l += ' ' + s;
            return l;
        }

    }
}
