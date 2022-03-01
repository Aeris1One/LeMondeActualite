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
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class LiveChecker extends TimerTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveChecker.class);

    private static String lastHour = DBot.persistentData.getOrAdd(DBot.LAST_HOUR);

    @Override
    public void run() {
        try {
            Document lastLiveDoc = getLastLive();

            if (lastLiveDoc == null)
                return;

            Elements allPost = lastLiveDoc.getElementsByClass("post__live-section post-container");
            Element lastPost = allPost.first();

            printPost(lastPost, lastLiveDoc);
        } catch (IOException ignored) {

        }
    }

    public static void printPost(Element post, Document webPage) {
        Element info = post.getElementsByClass("info-content").first();
        Element content_live = post.getElementsByClass("content--live").first();

        if (content_live == null) {
            System.out.println("Content Live = Null");
            return;
        }

        if (post.hasClass("post__live-container--essential")) {
            LOGGER.debug("Detected essential post"); //TODO
        }

        String mainImg = webPage.getElementsByClass("hero__live-content")
                .first()
                .getElementsByClass("hero__live-img")
                .first()
                .absUrl("src");

        String hour = info.getElementsByClass("date").first().text().trim();
        Element label = info.getElementsByClass("flag-live__border__label").first();

        Element titleElement = content_live.getElementsByClass("post__live-container--title post__space-node").first();
        String title = titleElement == null ? "" : titleElement.text().trim();

        if (hour.equalsIgnoreCase(lastHour)) {
            LOGGER.debug("[DBOT]: No new post has been detected !");
            return;
        }

        lastHour = hour;

        LOGGER.debug("Title = " + title);
        LOGGER.debug("Hour = " + hour);

        DBot.persistentData.set(DBot.LAST_HOUR, hour);

        LOGGER.debug("[DBOT]: Detected new post !");

        String liveUrl = DBot.persistentData.getOrAdd(DBot.LAST_CHECKED_URL);

        if (label != null && label.text().equalsIgnoreCase("Vos questions")) {
            System.out.println("Question ?");
            printQuestion(content_live, liveUrl, mainImg, hour);
        } else {
            System.out.println("Pas question ?");
            printNormalMessage(content_live, hour, title, mainImg, liveUrl);
        }
    }

    public static void printNormalMessage(Element content_live, String hour, String title, String mainImg, String liveUrl) {
        List<EmbedBuilder> messages = new ArrayList<>();

        EmbedBuilder first = new EmbedBuilder();
        first.setThumbnail(mainImg);
        first.setColor(Color.LIGHT_GRAY);
        first.setTitle(hour.trim() + " - " + title.trim(), liveUrl);

        messages.add(first);

        EmbedBuilder lastMessage = new EmbedBuilder();
        lastMessage.setColor(Color.LIGHT_GRAY);
        messages.add(lastMessage);

        for (Element element : content_live.getAllElements()) {
            //Simple Text
            if (element.hasClass("post__live-container--answer-text post__space-node")) { //Simple Text
                lastMessage.addField("", element.getElementsByClass("post__live-container--answer-text post__space-node").first().text(), false);
            } else if (element.hasClass("post__live-container--img post__space-node") && !element.hasClass("post__live-container--link")) {//Img
                //Todo image sa mère
                /*EmbedBuilder message = new EmbedBuilder();
                message.setColor(Color.LIGHT_GRAY);
                String url = element.getElementsByClass("post__live-container--img post__space-node").first().absUrl("src");
                message.setImage(url);
                messages.add(message);*/
            } else if (element.hasClass("post__live-container--img post__space-node")) { //Video
                EmbedBuilder message = new EmbedBuilder();
                String url = element.getElementsByClass("article__video-container").first().getAllElements().get(0).absUrl("src");
                message.setImage(url);
                messages.add(message);
            }
        }

        for (EmbedBuilder builder : messages) {
            Listener.channels.forEach(textChannel -> textChannel.sendMessageEmbeds(builder.build()).queue());
        }
    }

    /*
    //TODO to normal message ^^^^^^^^^^^^^^^^^^^^^^^^
    else if (content.hasClass("post__live-container--img post__space-node")) {
                EmbedBuilder message = new EmbedBuilder();
                String url = content.getElementsByClass("post__live-container--img post__space-node").first().absUrl("url");
                message.setImage(url);
                messages.add(message);
            }
     */

    public static void printQuestion(Element content_live, String liveUrl, String mainImg, String hour) {
        List<EmbedBuilder> messages = new ArrayList<>();

        EmbedBuilder first = new EmbedBuilder();
        first.setColor(Color.BLUE);
        first.setTitle(hour + " - Vos Question", liveUrl);
        first.setThumbnail(mainImg);
        messages.add(first);

        EmbedBuilder reponse = new EmbedBuilder();

        reponse.setColor(Color.BLUE);

        for (Element content : content_live.getAllElements()) {
            if (content.hasClass("post__live-container--comment-content")) {
                System.out.println("Test 1");
                String question = content.getElementsByClass("post__live-container--comment-blockquote").first().text();
                String pseudo = content.getElementsByClass("post__live-container--comment-author").first().text();
                EmbedBuilder message = new EmbedBuilder();
                message.setColor(Color.BLUE);
                message.addField(pseudo, question, false);

                messages.add(message);
            } else if (content.hasClass("post__live-container--answer-content")) {
                System.out.println("Test 2");
                String response = content.getElementsByClass("post__live-container--answer-text post__space-node").first().text();
                reponse.addField("", response, false);
            }
        }

        messages.add(reponse);

        for (EmbedBuilder builder : messages) {
            Listener.channels.forEach(textChannel -> textChannel.sendMessageEmbeds(builder.build()).queue());
        }
    }

    public Document getLastLive() throws IOException {
        String url = DBot.persistentData.getOrAdd(DBot.LAST_CHECKED_URL);
        Document document = Jsoup.parse(new URL(url).openStream(), "UTF-8", url);

        System.out.println("Check URL: " + url);

        boolean needToGetNewLink = needToRefreshLink(document);

        LOGGER.debug("[DBOT]: needToRefresh Live URL = " + needToGetNewLink);

        if (needToGetNewLink) {
            Element lastPost = document.getElementsByClass("post__live-section post-container").first();
            if (lastPost != null) {
                Element element = lastPost.getElementsByClass("post__live-container--link").get(0);
                if (element != null) {
                    String newUrl = element.attr("href");
                    LOGGER.debug("[DBOT]: New Live URL = " + newUrl);
                    DBot.persistentData.set(DBot.LAST_CHECKED_URL, newUrl);
                    return getLastLive();
                } else {
                    LOGGER.debug("[DBOT]: Last do not have a link");
                    return null;
                }
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
