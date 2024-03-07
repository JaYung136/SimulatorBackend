package org.sim.controller;

import org.jfree.chart.*;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.DefaultXYDataset;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ScatterGraph {
    public static StandardChartTheme createChartTheme(String fontName) {
        StandardChartTheme theme = new StandardChartTheme("unicode") {
            public void apply(JFreeChart chart) {
                chart.getRenderingHints().put(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                super.apply(chart);
            }
        };
        fontName = (fontName.length()==0) ? "宋体" : fontName;
        theme.setExtraLargeFont(new Font(fontName, Font.PLAIN, 25));
        theme.setLargeFont(new Font(fontName, Font.PLAIN, 24));
        theme.setRegularFont(new Font(fontName, Font.PLAIN, 22));
        theme.setSmallFont(new Font(fontName, Font.PLAIN, 20));
        theme.setLegendBackgroundPaint(Color.white);
        theme.setChartBackgroundPaint(Color.white);
        theme.setPlotBackgroundPaint(Color.white);
        return theme;
    }
    public JFreeChart makescatterchart(List<double[][]> data, String graphname, List<String> serienames) {
        DefaultXYDataset xydataset = new DefaultXYDataset();
        for(int i=1; i<=data.size(); ++i){
            xydataset.addSeries("消息["+serienames.get(i-1)+"]", data.get(i-1));//设置点的图标title一般表示这画的是决策变量还是目标函数值;
        }
        StandardChartTheme theme = createChartTheme("");
        ChartFactory.setChartTheme(theme);
        JFreeChart chart = ChartFactory.createScatterPlot(graphname, "消息发送时间", "消息延迟", xydataset,
                PlotOrientation.VERTICAL, true, true, false);//设置表头，x轴，y轴，name表示问题的类型
        // 保存在Graphs目录下
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));//定义时区，可以避免虚拟机时间与系统时间不一致的问题
            SimpleDateFormat matter = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss");
            String filename = graphname+matter.format(new Date()).toString();
            saveAsFile(chart,"OutputFiles/Graphs/"+filename+".png",1200, 800);
        }catch (Exception e){
            e.printStackTrace();
        }
//        setVisual(chart);
        return chart;
    }
    public void setVisual(JFreeChart chart){
        ChartFrame frame = new ChartFrame("2D scatter plot", chart, true);
        XYPlot xyplot = (XYPlot) chart.getPlot();
        xyplot.setBackgroundPaint(Color.white);//设置背景面板颜色
        ValueAxis vaaxis = xyplot.getDomainAxis();
        vaaxis.setAxisLineStroke(new BasicStroke(1.5f));//设置坐标轴粗细
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
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

    public static void plot(String graphname, List<double[][]> data, List<String> keys) {
        System.out.println("开始画图");
        ScatterGraph scatterGraph_ = new ScatterGraph();
        JFreeChart chart = scatterGraph_.makescatterchart(data, graphname, keys);
    }

    public static void main(String[] args) {
        ScatterGraph scatterGraph_ = new ScatterGraph();
        List<double[][]> data = new ArrayList<>();
        double[][] a = {{1, 2, 3}, {4, 5, 6}};
        double[][] b = {{2, 3, 10}, {7, 8, 10}};
        data.add(a);
        data.add(b);
        JFreeChart chart = scatterGraph_.makescatterchart(data, "延迟图像", List.of(new String[]{"serie1", "serie2"}));
    }
}

