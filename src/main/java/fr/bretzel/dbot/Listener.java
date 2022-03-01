package fr.bretzel.dbot;

import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Listener implements EventListener {

    public static List<TextChannel> channels = new ArrayList<>();

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof ReadyEvent readyEvent) {
            ready(readyEvent);
        }
    }

    public void ready(ReadyEvent event) {
        event.getJDA().getGuilds().forEach(guild -> {
            if (!DBot.SILENT) {
                for (long channel_id : DBot.channels) {
                    TextChannel channel = guild.getTextChannelById(channel_id);
                    if (channel != null)
                        channels.add(channel);
                }
            } else {
                if (guild.getName().equalsIgnoreCase("A.L.S.")) {
                    for (long channel_id : DBot.channels) {
                        TextChannel channel = guild.getTextChannelById(channel_id);
                        if (channel != null) {
                            channels.add(channel);
                            LoggerFactory.getLogger(Listener.class).debug("Added " + channel.getName() + " to the dev print");
                        }
                    }
                }
            }
        });
    }
}
