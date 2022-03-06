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

            if (lastPost != null)
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

        String mainImg = webPage.getElementsByClass("hero__live-content")
                .first()
                .getElementsByClass("hero__live-img")
                .first()
                .absUrl("src");

        String hour = info.getElementsByClass("date").first().text().trim();

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

        if (isQuestion(content_live)) {
            LOGGER.debug("Question is detected !");
            printQuestion(content_live, liveUrl, mainImg, hour);
        } else if (post.hasClass("post__live-container--essential")) {
            LOGGER.debug("Essential Detected !");
            //printEssential(content_live, hour, mainImg, liveUrl);
        } else {
            LOGGER.debug("Normal Message !");
            printNormalMessage(content_live, hour, title, mainImg, liveUrl);
        }
    }

    public static void printEssential(Element content_live, String hour, String mainImg, String liveUrl) {
        EmbedBuilder message = new EmbedBuilder();
        String title = content_live.getElementsByClass("post__live-container--title post__space-node title-big").first().text();

        message.setColor(Color.RED);
        message.setTitle(hour + " - " + title);
        message.setThumbnail(mainImg);

        //TODO
    }

    public static void printNormalMessage(Element content_live, String hour, String title, String mainImg, String liveUrl) {
        EmbedBuilder message = new EmbedBuilder();

        message.setThumbnail(mainImg);
        message.setColor(Color.LIGHT_GRAY);
        message.setTitle(hour.trim() + " - " + title.trim(), liveUrl);

        for (Element element : content_live.getAllElements()) {
            if (element.hasClass("post__live-container--answer-text post__space-node")) { //Simple Text
                String msg = fixString(element.getElementsByClass("post__live-container--answer-text post__space-node").first().text().trim());
                message.addField("", msg, false);
            } else if (element.hasClass("post__live-container--figure")) {//Img / Artcile
                if (element.hasClass("post__live-container--figure")) {
                    Element figure = element.getElementsByClass("post__live-container--figure").first();
                    if (figure != null) {
                        for (Element figureElement : figure.getAllElements()) {
                            if (figureElement.hasClass("post__live-container--img")) {
                                String imgUrl = figureElement.attr("data-src");

                                if (!imgUrl.isBlank() && !imgUrl.isEmpty())
                                    message.setImage(imgUrl);
                            }
                        }
                    } else {
                        LOGGER.debug("Pas de Figure ?");
                    }
                } else {
                    LOGGER.debug("Une Image ?");
                }
            } else if (element.hasClass("post__live-container--img post__space-node")) { //Video
                //String url = element.getElementsByClass("article__video-container").first().getAllElements().get(0).absUrl("src");
                //message.setImage(url);
            }
        }

        Listener.channels.forEach(textChannel -> textChannel.sendMessageEmbeds(message.build()).queue());
    }

    public static void printQuestion(Element content_live, String liveUrl, String mainImg, String hour) {
        EmbedBuilder message = new EmbedBuilder();

        message.setColor(Color.BLUE);
        message.setTitle(hour + " - Vos Question", liveUrl);
        message.setThumbnail(mainImg);

        for (Element content : content_live.getAllElements()) {
            if (content.hasClass("post__live-container--comment-content")) {
                Element questionElement = content.getElementsByClass("post__live-container--comment-blockquote").first();
                Element pseudoQuestion = content.getElementsByClass("post__live-container--comment-author").first();

                if (questionElement != null && pseudoQuestion != null) {
                    String question = questionElement.text();
                    String pseudo = pseudoQuestion.text();
                    message.addField(pseudo, fixString(question), false);
                }
            } else if (content.hasClass("post__live-container--answer-content")) {
                Element element = content.getElementsByClass("post__live-container--answer-text post__space-node").first();
                if (element != null) {
                    String response = element.text();
                    message.addField("", fixString(response), false);
                }
            }
        }

        Listener.channels.forEach(textChannel -> textChannel.sendMessageEmbeds(message.build()).queue());
    }

    public static String fixString(String input) {
        if (input == null || input.isEmpty() || input.isBlank())
            return "";

        String toContinue = "[...]";

        if (input.length() > 1024)
            input = input.substring(0, 1024 - toContinue.length()) + toContinue;

        return input;
    }


    public static boolean isQuestion(Element content_live) {
        return content_live.getAllElements().stream().anyMatch(element -> element.hasClass("post__live-container--comment-content"));
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
