package fr.bretzel.dbot;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.util.TimerTask;

public class LiveChecker extends TimerTask {
    private final Logger LOGGER = LoggerFactory.getLogger(LiveChecker.class);

    private String lastTitle = DBot.persistentData.getOrAdd(DBot.LAST_TITLE);
    private String lastHour = "";

    @Override
    public void run() {
        try {
            Document lastLiveDoc = getLastLive();

            if (lastLiveDoc == null)
                return;

            String liveUrl = DBot.persistentData.getOrAdd(DBot.LAST_CHECKED_URL);

            Elements allPost = lastLiveDoc.getElementsByClass("post__live-section post-container");
            Element section = allPost.first();
            Element info = section.getElementsByClass("info-content").first();
            Element content_live = section.getElementsByClass("content--live").first();

            if (content_live == null) {
                System.out.println("Content Live = Null");
                return;
            }

            Color color = Color.LIGHT_GRAY;

            if (content_live.hasClass("post__live-container--essential")) {
                LOGGER.debug("Detected essential post"); //TODO
                color = Color.RED;
            }

            String mainImg = lastLiveDoc.getElementsByClass("hero__live-content")
                    .first()
                    .getElementsByClass("hero__live-img")
                    .first()
                    .absUrl("src");

            String hour = info.getElementsByClass("date").first().text();
            Element label = info.getElementsByClass("flag-live__border__label").first();

            String title = null;

            if (content_live.hasClass("post__live-container--title post__space-node"))
                title = content_live.getElementsByClass("post__live-container--title post__space-node").first().text();

            if (label != null && label.text().equalsIgnoreCase("Vos questions"))
                return;


            if ((title == null || title.equalsIgnoreCase(lastTitle)) && hour.equalsIgnoreCase(lastHour)) {
                LOGGER.debug("[DBOT]: No new post has been detected !");
                return;
            }

            lastTitle = title;
            lastHour = hour;

            LOGGER.debug("[DBOT]: Detected new post !");

            Elements elementsMessage = content_live.getElementsByClass("post__live-container--answer-text post__space-node");

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setThumbnail(mainImg);

            embedBuilder.setTitle(hour.trim() + " - " + title.trim(), liveUrl);

            for (Element element : elementsMessage) {
                if (element.hasClass("post__live-container--link")) {
                    Element images = element.getElementsByClass("post__live-container--img post__space-node loaded").first();
                    Element titleImg = element.getElementsByClass("post__live-container--title-img post__space-node").first();
                    String urlPage = element.getElementsByClass("post__live-container--link").first().attr("href");
                    System.out.println("test 3");

                    if (images != null) {
                        embedBuilder.setImage(images.absUrl("src"));
                    }

                    System.out.println("test 4");

                    embedBuilder.setTitle(hour + " - " + title, urlPage);

                    System.out.println("test 5");

                    if (titleImg != null) {
                        embedBuilder.addField("\n Lire aussi :", titleImg.text().trim() + " \n", false);
                    }

                    System.out.println("test 6");

                    color = Color.YELLOW;
                } else {
                    embedBuilder.addField("", element.text().trim() + " \n", false);
                }
            }

            embedBuilder.setColor(color);

            embedBuilder.setFooter("\n \n Merci au site LeMonde.fr pour les actualités !");

            LOGGER.debug("Title = " + title);
            LOGGER.debug("Hour = " + hour);

            Listener.channels.forEach(textChannel -> textChannel.sendMessageEmbeds(embedBuilder.build()).queue());

            DBot.persistentData.set(DBot.LAST_TITLE, title);
            DBot.persistentData.set(DBot.LAST_HOUR, hour);
        } catch (IOException ignored) {

        }
    }

    public Document getLastLive() throws IOException {
        String url = DBot.persistentData.getOrAdd(DBot.LAST_CHECKED_URL);
        LOGGER.debug("[DBOT]: last live = " + url);
        Document document = Jsoup.parse(new URL(url).openStream(), "UTF-8", url);

        boolean needToGetNewLink = needToRefreshLink(document);

        LOGGER.debug("[DBOT]: needToRefresh Live URL = " + needToGetNewLink);

        if (needToGetNewLink) {
            Elements elements = document.getElementsByClass("post__live-container--link");
            if (elements.size() > 0) {
                Element element = elements.get(0);
                String newUrl = element.attr("href");
                LOGGER.debug("[DBOT]: New Live URL = " + newUrl);
                DBot.persistentData.set(DBot.LAST_CHECKED_URL, newUrl);
                return getLastLive();
            } else {
                LOGGER.debug("[DBOT]: Need to a new Live Url, waiting...");
                return null;
            }
        }

        return document;
    }

    public boolean needToRefreshLink(Document document) {
        Elements elements = document.getElementsByClass("label__live  label__live--off");
        return elements.size() > 0;
    }
}
