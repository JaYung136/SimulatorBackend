package org.sim.controller;

import org.jfree.chart.*;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudsimsdn.Log;
import org.sim.cloudsimsdn.sdn.workload.Workload;
import org.sim.service.Constants;

import javax.swing.*;
import java.awt.*;
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
//        FileOutputStream out = null;
//        File outFile = new File(outputPath);
//        if (!outFile.getParentFile().exists()) {
//            outFile.getParentFile().mkdirs();
//        }
//        out = new FileOutputStream(outputPath);
//        if (out != null) {
//            try {
//                // 保存为PNG
//                ChartUtils.writeChartAsPNG(out, chart, weight, height);
//                // 保存为JPEG
//                // ChartUtils.writeChartAsJPEG(out, chart, weight, height);
//                out.flush();
//                out.close();
//            } catch (IOException e) {
////                e.printStackTrace();
//            }
//
//        }
    }

    public void paint(XYSeries[] xys, String pngName, boolean save) throws Exception {
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        for(XYSeries xy: xys) {
            xySeriesCollection.addSeries(xy);
        }
        JFreeChart chart = ChartFactory.createXYLineChart(pngName, "发送时刻(微秒)", "延迟(微秒)", xySeriesCollection);
        ChartPanel chartPanel = new ChartPanel(chart);
        //chartPanel.setPreferredSize(new Dimension(100 ,100));
        setContentPane(chartPanel);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));//定义时区，可以避免虚拟机时间与系统时间不一致的问题
        SimpleDateFormat matter = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss");
        matter.format(new Date()).toString();
        setVisualUI(chart);
        if(save)
//        if(false)
            saveAsFile(chart, System.getProperty("user.dir")+"\\OutputFiles\\Graphs\\"+matter.format(new Date()).toString()+pngName+".png", 1200, 800);
    }

    public void paintLink(XYSeries[] xys, String pngName, String ylabel, boolean save, double maxvalue) throws Exception {
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        for(XYSeries xy: xys) {
            xySeriesCollection.addSeries(xy);
        }
        JFreeChart chart = ChartFactory.createXYLineChart(pngName, "记录时刻(微秒)", ylabel, xySeriesCollection);
        ChartPanel chartPanel = new ChartPanel(chart);
        //chartPanel.setPreferredSize(new Dimension(100 ,100));
        setContentPane(chartPanel);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));//定义时区，可以避免虚拟机时间与系统时间不一致的问题
        SimpleDateFormat matter = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss");
        matter.format(new Date()).toString();
        setVisualUIForLink(chart, maxvalue);
        if(save)
//        if(false)
            saveAsFile(chart, System.getProperty("user.dir")+"\\OutputFiles\\Graphs\\"+matter.format(new Date()).toString()+pngName+".png", 1200, 800);
    }


    public void paintCPU() throws Exception {
        XYSeries[] xySeries = new XYSeries[Constants.hosts.size()];
        for(int i = 0; i < Constants.hosts.size(); i++) {
            xySeries[i] = new XYSeries(Constants.hosts.get(i).getName());
        }
        Log.printLine("当前LOG数：" + Constants.logs.size());
        for(int i = 0; i < Constants.logs.size(); i++) {
            if(i % Constants.hosts.size() == 0) {
                if(Double.parseDouble(Constants.logs.get(i).time) >= Constants.finishTime) {
                    break;
                }
            }
            xySeries[i % Constants.hosts.size()].add(Double.parseDouble(Constants.logs.get(i).time), Double.parseDouble(Constants.logs.get(i).cpuUtilization));
        }
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        for(XYSeries xy: xySeries) {
            xySeriesCollection.addSeries(xy);
        }
        JFreeChart chart = ChartFactory.createXYLineChart("CPU利用率图像", "时刻(微秒)", "利用率(%)", xySeriesCollection);
        ChartPanel chartPanel = new ChartPanel(chart);
        //chartPanel.setPreferredSize(new Dimension(100 ,100));
        setContentPane(chartPanel);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));//定义时区，可以避免虚拟机时间与系统时间不一致的问题
        SimpleDateFormat matter = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss");
        matter.format(new Date()).toString();
        setVisualUIForHost(chart);
        saveAsFile(chart, System.getProperty("user.dir")+"\\OutputFiles\\Graphs\\"+matter.format(new Date()).toString()+"cpu利用率图像.png", 1200, 800);
    }

    public void paintHost(Host host) throws Exception{
        XYSeries[] xySeries = new XYSeries[1];
        xySeries[0] = new XYSeries(host.getName());
        for(int i = 0; i < Constants.logs.size(); i++) {
            if(i % Constants.hosts.size() == 0) {
                if(Double.parseDouble(Constants.logs.get(i).time) >= Constants.finishTime) {
                    break;
                }
            }
            if(i % Constants.hosts.size() == host.getId())
                xySeries[0].add(Double.parseDouble(Constants.logs.get(i).time), Double.parseDouble(Constants.logs.get(i).cpuUtilization));
        }
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        for(XYSeries xy: xySeries) {
            xySeriesCollection.addSeries(xy);
        }
        JFreeChart chart = ChartFactory.createXYLineChart("CPU利用率图像", "时刻(微秒)", "利用率(%)", xySeriesCollection);
        ChartPanel chartPanel = new ChartPanel(chart);
        //chartPanel.setPreferredSize(new Dimension(100 ,100));
        setContentPane(chartPanel);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));//定义时区，可以避免虚拟机时间与系统时间不一致的问题
        SimpleDateFormat matter = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss");
        matter.format(new Date()).toString();
        setVisualUIForHost(chart);
//        saveAsFile(chart, System.getProperty("user.dir")+"\\OutputFiles\\Graphs\\"+matter.format(new Date()).toString()+ host.getName() +"cpu利用率图像.png", 1200, 800);
    }
    public void setVisualUI(JFreeChart chart){
        ChartFrame frame = new ChartFrame("图像", chart, true);
        XYPlot xyplot = (XYPlot) chart.getPlot();
        xyplot.setBackgroundPaint(Color.white);//设置背景面板颜色
        ValueAxis vaaxis = xyplot.getDomainAxis();
        vaaxis.setAxisLineStroke(new BasicStroke(1.5f));//设置坐标轴粗细
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public void setVisualUIForLink(JFreeChart chart, double maxvalue){
        ChartFrame frame = new ChartFrame("图像", chart, true);
        XYPlot xyplot = (XYPlot) chart.getPlot();
        xyplot.setBackgroundPaint(Color.white);//设置背景面板颜色
        ValueAxis vaaxis = xyplot.getDomainAxis();
        vaaxis.setAxisLineStroke(new BasicStroke(1.5f));//设置坐标轴粗细

        ValueAxis axis = xyplot.getRangeAxis();
        axis.setRange(0,maxvalue*1.5);
        xyplot.setRangeAxis(axis);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public void setVisualUIForHost(JFreeChart chart){
        ChartFrame frame = new ChartFrame("图像", chart, true);
        XYPlot xyplot = (XYPlot) chart.getPlot();
        xyplot.setBackgroundPaint(Color.white);//设置背景面板颜色
        ValueAxis vaaxis = xyplot.getDomainAxis();
        vaaxis.setAxisLineStroke(new BasicStroke(1.5f));//设置坐标轴粗细

        ValueAxis axis = xyplot.getRangeAxis();
        axis.setRange(0,1);
        xyplot.setRangeAxis(axis);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public static void paintMultiMsgGraph(List<Workload> wls, String name, List<String> namelist) throws Exception {
        MyPainter p = new MyPainter(name+"网络延迟图像");
        p.setSize(50000, 50000);
        Map<String, XYSeries> xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = wl.msgName;//"消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            if(!namelist.contains(key))
                continue;
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time*1000000, (wl.networktransmissiontime)*1000000);
            xySerieMap.put(key, line);
        }
        p.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), name+"网络延迟图像", false);

        MyPainter q = new MyPainter(name+"端到端延迟图像");
        q.setSize(50000, 50000);
        xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = wl.msgName;//"消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            if(!namelist.contains(key))
                continue;
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time*1000000, (wl.end2endfinishtime-wl.time)*1000000);
            xySerieMap.put(key, line);
        }
        Thread.sleep(1000);
        q.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), name+"端到端延迟图像", false);

        MyPainter m = new MyPainter(name+"调度等待延迟图像");
        m.setSize(50000, 50000);
        xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = wl.msgName;//"消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            if(!namelist.contains(key))
                continue;
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time*1000000, (wl.dagschedulingtime)*1000000);
            xySerieMap.put(key, line);
        }
        Thread.sleep(1000);
        m.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), name+"调度等待延迟图像", false);
    }
    public static void paintMultiLatencyGraph(List<Workload> wls, Boolean save) throws Exception {
        MyPainter p = new MyPainter("网络延迟图像");
        p.setSize(50000, 50000);
        Map<String, XYSeries> xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = wl.msgName;//"消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time*1000000, (wl.networktransmissiontime)*1000000);
            xySerieMap.put(key, line);
        }
        p.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "网络延迟图像", save);

        MyPainter q = new MyPainter("端到端延迟图像");
        q.setSize(50000, 50000);
        xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = wl.msgName;//"消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time*1000000, (wl.end2endfinishtime-wl.time)*1000000);
            xySerieMap.put(key, line);
        }

        q.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "端到端延迟图像", save);
        Thread.sleep(1000);
        MyPainter m = new MyPainter("调度等待延迟图像");
        m.setSize(50000, 50000);
        xySerieMap = new HashMap<>();
        for(int i = 0; i < wls.size(); i++) {
            Workload wl = wls.get(i);
            String key = wl.msgName;//"消息[" + wl.submitVmName + "->" + wl.destVmName + "]";
            XYSeries line = xySerieMap.get(key);
            //如果不存在这条线就新建
            if (line ==null) {
                line = new XYSeries(key);
            }
            line.add(wl.time*1000000, (wl.dagschedulingtime)*1000000);
            xySerieMap.put(key, line);
        }
        Thread.sleep(1000);
        m.paint(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "调度等待延迟图像", save);
    }


    public static void paintMultiLinkGraph(Map<String, LinkUtil> lus, Boolean save) throws Exception {
        MyPainter p = new MyPainter("链路利用率图像");
        p.setSize(50000, 100000);
        Map<String, XYSeries> xySerieMap = new HashMap<>();
        double maxvalue = 0;
        for (LinkUtil lu : lus.values()) {
            if(lu.printable == false)
                continue;
            String linkname = lu.linkname;
            XYSeries forwardline = new XYSeries(lu.lowOrder+"-"+lu.highOrder);
            XYSeries backwardline = new XYSeries(lu.highOrder+"-"+lu.lowOrder);
            for(int i=0; i<lu.recordTimes.size(); ++i) {
                forwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilForward.get(i));
                backwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilBackward.get(i));
                maxvalue = (lu.UnitUtilForward.get(i)  > maxvalue)? lu.UnitUtilForward.get(i)  : maxvalue;
                maxvalue = (lu.UnitUtilBackward.get(i) > maxvalue)? lu.UnitUtilBackward.get(i) : maxvalue;
            }
            xySerieMap.put(lu.lowOrder+"-"+lu.highOrder, forwardline);
            xySerieMap.put(lu.highOrder+"-"+lu.lowOrder, backwardline);
        }
        p.paintLink(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "链路利用率图像", "利用率", save, maxvalue);

//        p = new MyPainter("链路带宽速率图像");
//        p.setSize(50000, 100000);
//        xySerieMap = new HashMap<>();
//        double bw_Gbps = ethernetSpeed / 1000000; //10
//        for (LinkUtil lu : lus.values()) {
//            if(lu.printable == false)
//                continue;
//            String linkname = lu.linkname;
//            XYSeries forwardline = new XYSeries(linkname+"[方向"+lu.lowOrder+"->"+lu.highOrder+"]");
//            XYSeries backwardline = new XYSeries(linkname+"[方向"+lu.highOrder+"->"+lu.lowOrder+"]");
//            for(int i=0; i<lu.recordTimes.size(); ++i) {
//                forwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilForward.get(i)*0.01*bw_Gbps);
//                backwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilBackward.get(i)*0.01*bw_Gbps);
//            }
//            xySerieMap.put(linkname+"[方向"+lu.lowOrder+"->"+lu.highOrder+"]", forwardline);
//            xySerieMap.put(linkname+"[方向"+lu.highOrder+"->"+lu.lowOrder+"]", backwardline);
//        }
//        p.paintLink(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), "链路带宽速率图像", "带宽(Gbps)", save);
    }

    public static void paintOptionLinkGraph(Map<String, LinkUtil> lus, String name, List<String> namelist) throws Exception {
        MyPainter p = new MyPainter(name+"利用率图像");
        p.setSize(50000, 100000);
        Map<String, XYSeries> xySerieMap = new HashMap<>();
        double maxvalue = 0;
        for (LinkUtil lu : lus.values()) {
            if(!lu.printable) {
                continue;
            }
            String linkname1 = lu.lowOrder+"-"+lu.highOrder;
            String linkname2 = lu.highOrder+"-"+lu.lowOrder;
            if(namelist.contains(linkname1)) {
                XYSeries forwardline = new XYSeries(linkname1);
                for(int i=0; i<lu.recordTimes.size(); ++i) {
                    forwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilForward.get(i));
                    maxvalue = (lu.UnitUtilForward.get(i)  > maxvalue)? lu.UnitUtilForward.get(i)  : maxvalue;
                }
                xySerieMap.put(linkname1, forwardline);

            }
            if(namelist.contains(linkname2)) {
                XYSeries backwardline = new XYSeries(linkname2);
                for(int i=0; i<lu.recordTimes.size(); ++i) {
                    backwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilBackward.get(i));
                    maxvalue = (lu.UnitUtilBackward.get(i) > maxvalue)? lu.UnitUtilBackward.get(i) : maxvalue;
                }
                xySerieMap.put(linkname2, backwardline);
            }
        }
        p.paintLink(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), name+"利用率图像", "利用率",false, maxvalue);

//        p = new MyPainter(name+"带宽速率图像");
//        p.setSize(50000, 100000);
//        xySerieMap = new HashMap<>();
//        double bw_Gbps = ethernetSpeed / 1000000; //10
//        for (LinkUtil lu : lus.values()) {
//            if(lu.printable == false || !lu.linkname.equals(name))
//                continue;
//            XYSeries forwardline = new XYSeries(name+"[方向"+lu.lowOrder+"->"+lu.highOrder+"]");
//            XYSeries backwardline = new XYSeries(name+"[方向"+lu.highOrder+"->"+lu.lowOrder+"]");
//            for(int i=0; i<lu.recordTimes.size(); ++i) {
//                forwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilForward.get(i)*0.01*bw_Gbps);
//                backwardline.add(lu.recordTimes.get(i)*1000000, lu.UnitUtilBackward.get(i)*0.01*bw_Gbps);
//            }
//            xySerieMap.put(name+"[方向"+lu.lowOrder+"->"+lu.highOrder+"]", forwardline);
//            xySerieMap.put(name+"[方向"+lu.highOrder+"->"+lu.lowOrder+"]", backwardline);
//        }
//        p.paintLink(xySerieMap.values().toArray(new XYSeries[xySerieMap.size()]), name+"带宽速率图像", "带宽(Gbps)",false);
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
        p.paint(xySeries, "as", true);
    }


}
