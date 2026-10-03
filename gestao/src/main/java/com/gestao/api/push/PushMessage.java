package com.gestao.api.push;

import java.util.Map;

public record PushMessage(
        String to,
        String title,
        String body,
        Map<String, Object> data,
        String channelId) {
}
