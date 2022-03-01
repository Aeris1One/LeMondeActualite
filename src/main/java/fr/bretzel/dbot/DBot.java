package fr.bretzel.dbot;

import com.google.gson.JsonArray;
import fr.bretzel.config.Config;
import fr.bretzel.config.entry.JsonElementEntry;
import fr.bretzel.config.entry.StringEntry;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import java.util.Date;
import java.util.Timer;

public class DBot {

    public static final StringEntry DISCORD_TOKEN = new StringEntry("token", "");
    public static final JsonElementEntry DISCORD_CHANNEL_ID = new JsonElementEntry("channel_ids", new JsonArray());

    public static final StringEntry LAST_CHECKED_URL = new StringEntry("last checked url", "https://www.lemonde.fr/international/live/2022/02/27/guerre-en-ukraine-en-direct-l-union-europeenne-va-armer-kiev-les-occidentaux-appellent-leurs-ressortissants-a-quitter-la-russie_6115418_3210.html");
    public static final StringEntry LAST_TITLE = new StringEntry("last title", "");
    public static final StringEntry LAST_HOUR = new StringEntry("last title", "");

    public static long[] channels = new long[0];

    public static final Config mainConfig = new Config("config", config -> {
        config.enableFileWatch(2000);

        if (config.isFirstLoad()) {
            config.write();
            config.getOrAdd(DISCORD_TOKEN);
            config.getOrAdd(DISCORD_CHANNEL_ID);
        }
    });

    public static final Config persistentData = new Config("persistent", config -> config.setWriteOnUpdate(true));

    public static JDA JDA;

    public static void main(String[] args) throws Exception {
        final var token = mainConfig.getOrAdd(DISCORD_TOKEN);
        final var channelIds = mainConfig.getOrAdd(DISCORD_CHANNEL_ID);

        assert token.length() > 0;

        if (channelIds instanceof JsonArray array) {
            channels = new long[array.size()];

            for (int i = 0; i < array.size(); i++) {
                channels[i] = array.get(i).getAsLong();
            }

        } else {
            throw new Exception("Channel ids config is not a JsonArray");
        }


        JDA = JDABuilder.createDefault(token).addEventListeners(new Listener()).build();
        JDA.awaitReady();

        System.out.println("[DBOT]: Starting timer for checking site LeMonde.fr");

        LiveChecker checker = new LiveChecker();
        new Timer().schedule(checker, new Date(), (1000 * 60));

    }
}
