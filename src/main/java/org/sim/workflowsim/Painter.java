package org.sim.workflowsim;


import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Painter extends JFrame {
    public  Painter(String title) throws Exception {
        super(title);
    }

    public void saveAsFile(JFreeChart chart, String outputPath, int weight, int height)throws Exception {
        FileOutputStream out = null;
        File outFile = new File(outputPath);
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }
        out = new FileOutputStream(outputPath);
        // 保存为PNG
        ChartUtils.writeChartAsPNG(out, chart, weight, height);
        // 保存为JPEG
        // ChartUtils.writeChartAsJPEG(out, chart, weight, height);
        out.flush();
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public void paint(XYSeries[] xys, String pngName) throws Exception {
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        for(XYSeries xy: xys) {
            xySeriesCollection.addSeries(xy);
        }
        JFreeChart chart = ChartFactory.createXYLineChart("as", "ss", "aa", xySeriesCollection);
        ChartPanel chartPanel = new ChartPanel(chart);
        //chartPanel.setPreferredSize(new Dimension(100 ,100));
        setContentPane(chartPanel);
        saveAsFile(chart, System.getProperty("user.dir")+"\\InputFiles\\" + pngName + ".png", 1200, 800);
    }

    public static void main(String[] args) throws Exception {
        Painter p = new Painter("as");
       /* p.setSize(500, 500);
        p.setVisible(true);*/
        XYSeries[] xySeries = new XYSeries[1];
        xySeries[0] = new XYSeries("as");
        xySeries[0].add(1, 1);
        p.paint(xySeries, "ass");
    }
}
