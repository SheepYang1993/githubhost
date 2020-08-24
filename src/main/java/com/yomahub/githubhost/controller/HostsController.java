package com.yomahub.githubhost.controller;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yomahub.githubhost.enums.OSTypeEnum;
import com.yomahub.githubhost.utils.URLUtil;
import com.yomahub.githubhost.vo.HostVO;
import com.yomahub.githubhost.vo.IpPingVO;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.util.*;

@Controller
public class HostsController {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${ip.address.domain}")
    private String ipAddressUrl;

    @Value("${github.hosts}")
    private List<String> githubHosts;

    private Map<String,Integer> taskProgressMap = new HashMap<>();

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index(ModelMap modelMap){
        String taskId = IdUtil.fastSimpleUUID();
        taskProgressMap.put(taskId,0);
        modelMap.put("taskId",taskId);
        return "index";
    }

    @RequestMapping(value = "/getProgress", method = RequestMethod.GET)
    @ResponseBody
    public Integer getProgress(String taskId){
        if(taskProgressMap.containsKey(taskId)){
            BigDecimal progress = new BigDecimal(taskProgressMap.get(taskId)).divide(new BigDecimal(githubHosts.size()))
                    .multiply(new BigDecimal(100)).setScale(2, RoundingMode.HALF_UP);
            if(progress.compareTo(new BigDecimal(100)) == 0){
                taskProgressMap.remove(taskId);
            }
            return progress.intValue();
        }else{
            return 0;
        }
    }

    @RequestMapping(value = "/genHosts", method = RequestMethod.GET)
    @ResponseBody
    public String genHosts(String taskId){
        try{
            int completedCount = 0;
            List<HostVO> hostVOList = new ArrayList<>();
            HostVO hostVO;
            for(String githubHost : githubHosts){
                hostVO = getBestCdnIpForHost(githubHost);
                if(hostVO != null){
                    hostVOList.add(hostVO);
                }
                completedCount++;
                taskProgressMap.put(taskId,completedCount);
            }

            StringBuilder result = new StringBuilder();
            //封装结果
            OSTypeEnum osType = getOSType();
            result.append(StrUtil.format("<font color=green>你的系统为:{}</font>\n", osType));

            String flushMethod;
            if(osType.equals(OSTypeEnum.Windows)){
                flushMethod = "然后运行\"ipconfig /flushdns\"生效hosts文件";
            }else{
                flushMethod = "然后运行\"sudo killall -HUP mDNSResponder\"生效hosts文件";
            }
            result.append(StrUtil.format("<font color=green>请复制以下内容，追加到你机器的hosts文件里，{}</font>\n\n",flushMethod));

            String itemStr;
            for(HostVO item : hostVOList){
                itemStr = StrUtil.format("{}   {}\n",item.getIp(),item.getDomain());
                result.append(itemStr);
            }

            return result.toString();
        }catch (Throwable t){
            t.printStackTrace();
            return "error";
        }
    }

    private HostVO getBestCdnIpForHost(String host) throws Exception{
        log.info("正在为[{}]选取最快的IP...",host);
        String domainName = URLUtil.getDomainName(host);
        String requestUrl;
        if(domainName.equals(host)){
            requestUrl = StrUtil.format("http://{}.{}",domainName,ipAddressUrl);
        }else{
            requestUrl = StrUtil.format("http://{}.{}/{}",domainName,ipAddressUrl,host);
        }

        String content;
        try {
            content = Jsoup.connect(requestUrl).execute().body();
        } catch (Exception e) {
            return null;
        }

        if (StringUtils.isEmpty(content)) {
            return null;
        }

        Elements elements;
        try{
            elements = Jsoup.parse(content).select("table[class=panel-item table table-stripes table-v]").get(0)
                    .select("tr").last().select("td ul li");
        }catch (Exception e){
            return null;
        }

        //定义返回hostVO
        HostVO hostVO = null;

        //如果只有一个，就直接封装对象，如果有多个，去ping其中耗时最短的ip，再封装
        if(elements.size() == 1){
            hostVO = new HostVO(elements.get(0).text(),host);
        }else{
            List<IpPingVO> ipPingVOList = new ArrayList<>();
            for (Element element : elements) {
                String ip = element.text();
                //ping 10次所用的平均时间
                long ping10TimesAvg = URLUtil.ping10TimesAvg(ip);
                ipPingVOList.add(new IpPingVO(ip,ping10TimesAvg));
            }

            //按ping的耗时正序排
            ListUtil.sort(ipPingVOList, Comparator.comparing(IpPingVO::getPingTime));

            //取第一个，也就是最小的耗时放入resultList
            String bestIp = ipPingVOList.get(0).getIp();
            hostVO = new HostVO(bestIp, host);
        }
        log.info("完成[{}]IP的选取,ip为:{}",hostVO.getDomain(),hostVO.getIp());
        return hostVO;
    }

    private OSTypeEnum getOSType(){
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Mac OS")) {
            return OSTypeEnum.Mac;
        } else if (osName.startsWith("Windows")) {
            return OSTypeEnum.Windows;
        } else {
            return OSTypeEnum.Linux;
        }
    }
}
