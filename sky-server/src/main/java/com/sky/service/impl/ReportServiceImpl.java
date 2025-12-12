package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    //统计指定时间区间内的营业额数据
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin,LocalDate end){
        //当前集合用于存放从begin到end范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)) {
            //日期计算，计算指定日期的后一天对应的日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        for(LocalDate date : dateList){
            //查询date日期对应的营业额数据，营业额是指：状态为“已完成”的订单金额合计。
            //LocalDate只有年月日
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN); //LocalTime.MIN相当于获得0点0分
            LocalDateTime endTime = LocalDateTime.of(date,LocalTime.MAX);//无限接近于下一个日期的0点0分0秒
            //select sum(amount) from orders where order_time > ? and order_time < ? and status = 5
            //status==5代表订单已完成
            Map map = new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map); //算出当天的营业额
            //考虑当天营业额为0的情况，会返回空
            turnover = turnover == null ? 0.0:turnover;
            turnoverList.add(turnover);
        }
        //封装返回结果
        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
    }

    //统计指定时间区间内的营业额数据
    @Autowired
    private UserMapper userMapper;

    //统计指定时间区间内的用户数据
    public UserReportVO getUserStatistics(LocalDate begin,LocalDate end){
        //存放从begin到end之间的每天对应的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天的新增用户数量 select count(id) from user where create_time < ? and create_time> ?
        List<Integer> newUserList = new ArrayList<>();
        //存放每天的总用户数量 select count(id) from user where create_time < ?
        List<Integer> totalUserList = new ArrayList<>();
        for(LocalDate date : dateList){
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("end",endTime);//只加一个end（1个参数）自动匹配统计总计的SQL语句
            //总用户数量
            Integer totalUser = userMapper.countByMap(map);
            map.put("begin",beginTime);//再加一个参数匹配统计每日新增的SQL语句
            //新增用户数量
            Integer newUser = userMapper.countByMap(map);
            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }
        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .build();
    }

    //统计指定时间区间内的订单数据
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天的订单总数
        List<Integer> orderCountList = new ArrayList<>();
        //存放每天的有效订单数
        List<Integer> validOrderCountList = new ArrayList<>();
        //便利dateList集合，查询每天的有效订单数和订单总数
        for(LocalDate date : dateList){
            //查询每天的订单总数 select count(id) from orders where order_time > ? and order_time < ?
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Integer orderCount = getOrderCount(beginTime, endTime, null);
            //查询每天的有效订单数select count(id) from orders where order_time > ? and order_time < ? and status = 5
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }
        //计算时间区间内的订单总数量
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        //计算时间区间内的有效订单数量
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        //计算订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0){
            //计算订单完成率
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }
    //根据条件统计订单数量
    private Integer getOrderCount(LocalDateTime begin,LocalDateTime end,Integer status){
        Map map = new HashMap();
        map.put("begin",begin);
        map.put("end",end);
        map.put("status",status);
        return orderMapper.countByMap(map);
    }


    @Autowired
    private WorkspaceService workspaceService;
    //统计指定时间区间内的销量排名前10
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end,LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");
        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");
        return SalesTop10ReportVO.builder().nameList(nameList).numberList(numberList).build();
    }

    //导出运营数据报表
    public void exportBusinessData(HttpServletResponse response){
        //1.查询数据库，获取营业数据--查询最近30天的运营数据
        LocalDate dateBegin = LocalDate.now().minusDays(30); //减30天的时间
        LocalDate dateEnd = LocalDate.now().minusDays(1);
        BusinessDataVO businessDatavo = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));
        //2.通过POI将数据写入到Excel文件中
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");//在类路径下读取资源返回输入流对象

        try {
            //基于模板文件创建一个新的Excel文件
            XSSFWorkbook excel = new XSSFWorkbook(in);
            //获取表格文件的Sheet文件
            XSSFSheet sheet = excel.getSheet("Sheet1");
            //填充数据--时间
            sheet.getRow(1).getCell(1).setCellValue("时间："+dateBegin+"至"+dateEnd);
            //获得第4行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessDatavo.getTurnover()); //第3个单元格
            row.getCell(4).setCellValue(businessDatavo.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDatavo.getNewUsers());
            //获得第5行
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessDatavo.getValidOrderCount());
            row.getCell(4).setCellValue(businessDatavo.getUnitPrice());
            //填充明细数据
            for(int i=0;i<30;i++){
                LocalDate date = dateBegin.plusDays(i);
                //查询某一天的营业数据
                workspaceService.getBusinessData(LocalDateTime.of(date,LocalTime.MIN),LocalDateTime.of(date,LocalTime.MAX));
                //获得某一行
                row = sheet.getRow(7+i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessDatavo.getTurnover());
                row.getCell(3).setCellValue(businessDatavo.getValidOrderCount());
                row.getCell(4).setCellValue(businessDatavo.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessDatavo.getUnitPrice());
                row.getCell(6).setCellValue(businessDatavo.getNewUsers());
            }
            //3.通过输出流将Excel文件下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);
            //关闭资源
            out.close();
            excel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}