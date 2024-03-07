package org.sim.controller;


import org.jfree.chart.*;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.sim.cloudsimsdn.sdn.workload.Workload;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class MyPainter extends JFrame {
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

        //设置标题字体
        theme.setExtraLargeFont(new Font("黑体", Font.BOLD, 20));
        //设置轴向字体
//        theme.setLargeFont(new Font("宋体", Font.CENTER_BASELINE, 15));
        //设置图例字体
//        theme.setRegularFont(new Font("宋体", Font.CENTER_BASELINE, 15));
        return theme;
    }
    public  MyPainter(String title) throws Exception {
        super(title);
        StandardChartTheme theme = createChartTheme("");
        ChartFactory.setChartTheme(theme);
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
        JFreeChart chart = ChartFactory.createXYLineChart(pngName, "发送时刻", "延迟", xySeriesCollection);
        ChartPanel chartPanel = new ChartPanel(chart);
        //chartPanel.setPreferredSize(new Dimension(100 ,100));
        setContentPane(chartPanel);

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));//定义时区，可以避免虚拟机时间与系统时间不一致的问题
        SimpleDateFormat matter = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss");
        matter.format(new Date()).toString();
        saveAsFile(chart, System.getProperty("user.dir")+"\\OutputFiles\\Graphs\\"+pngName+matter.format(new Date()).toString()+".png", 1200, 800);
    }

    public static void paintMultiGraph(List<Workload> wls) throws Exception {
        MyPainter p = new MyPainter("网络延迟图像");
        p.setSize(500, 500);
        Map<String, XYSeries> xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = "消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time, wl.networkfinishtime-wl.time);
            xySerieMap.put(key, line);
        }
        p.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "网络延迟图像");

        p = new MyPainter("端到端延迟图像");
        p.setSize(500, 500);
        xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = "消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time, wl.end2endfinishtime-wl.time);
            xySerieMap.put(key, line);
        }
        p.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "端到端延迟图像");

        p = new MyPainter("DAG延迟图像");
        p.setSize(500, 500);
        xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = "消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time, wl.end2endfinishtime-wl.networkfinishtime);
            xySerieMap.put(key, line);
        }
        p.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "DAG延迟图像");
    }

    public static void main(String[] args) throws Exception {
        MyPainter p = new MyPainter("as");
        p.setSize(500, 500);
        XYSeries[] xySeries = new XYSeries[3];
        for(int i = 0; i < 3; i++) {
            xySeries[i] = new XYSeries("asa" + i);
            xySeries[i].add(i, i + 10);
            xySeries[i].add(i + 10, i + 20);
        }
        p.paint(xySeries, "as");
    }


}
