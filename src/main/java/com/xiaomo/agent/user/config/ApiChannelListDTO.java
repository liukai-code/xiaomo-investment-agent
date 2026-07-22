package com.xiaomo.agent.user.config;

import java.util.List;

public class ApiChannelListDTO {

    private List<ApiChannelDTO> channels;
    private Long activeChannelId;

    public ApiChannelListDTO() {
    }

    public ApiChannelListDTO(List<ApiChannelDTO> channels, Long activeChannelId) {
        this.channels = channels;
        this.activeChannelId = activeChannelId;
    }

    public List<ApiChannelDTO> getChannels() {
        return channels;
    }

    public void setChannels(List<ApiChannelDTO> channels) {
        this.channels = channels;
    }

    public Long getActiveChannelId() {
        return activeChannelId;
    }

    public void setActiveChannelId(Long activeChannelId) {
        this.activeChannelId = activeChannelId;
    }
}
