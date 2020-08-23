package com.yomahub.githubhost.vo;

public class IpPingVO {

    private String ip;

    private Long pingTime;

    public IpPingVO(String ip, Long pingTime) {
        this.ip = ip;
        this.pingTime = pingTime;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Long getPingTime() {
        return pingTime;
    }

    public void setPingTime(Long pingTime) {
        this.pingTime = pingTime;
    }
}
