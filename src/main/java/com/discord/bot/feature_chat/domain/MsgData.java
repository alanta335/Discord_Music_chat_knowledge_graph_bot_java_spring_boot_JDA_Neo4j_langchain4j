package com.discord.bot.feature_chat.domain;

import java.time.OffsetDateTime;

public record MsgData(String key, OffsetDateTime ts, String author, String content) {
}

