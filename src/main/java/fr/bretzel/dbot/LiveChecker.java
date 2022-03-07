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
import java.util.Calendar;
import java.util.TimerTask;

public class LiveChecker extends TimerTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveChecker.class);

    private static String lastHour = DBot.persistentData.getOrAdd(DBot.LAST_HOUR);
    private static String mainImg = "https://www.countryflags.com/wp-content/uploads/ukraine-flag-png-large.png";

    public static long lastUpdated = System.currentTimeMillis();

    @Override
    public void run() {
        lastUpdated = System.currentTimeMillis();
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

    public static boolean isFullyStuck() {
        return (System.currentTimeMillis() - lastUpdated) > 70000;
    }

    public static void printPost(Element post, Document webPage) {
        Element info = post.getElementsByClass("info-content").first();
        Element content_live = post.getElementsByClass("content--live").first();

        if (content_live == null) {
            System.out.println("Content Live = Null");
            return;
        }

        Element mainImgElement = webPage.getElementsByClass("hero__live-content").first();
        if (mainImgElement != null) {
            Element secondMainImgElement = mainImgElement.getElementsByClass("hero__live-img").first();
            if (secondMainImgElement != null) {
                String imgUrl = secondMainImgElement.absUrl("src").trim();
                if (!imgUrl.equalsIgnoreCase(mainImg)) {
                    mainImg = imgUrl;
                }
            }
        }

        String hour = getHourTitle(info);

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

        printNormalMessage(post, content_live, hour, title, mainImg, liveUrl);
    }

    public static void printNormalMessage(Element post, Element content_live, String hour, String title, String mainImg, String liveUrl) {
        EmbedBuilder message = new EmbedBuilder();

        message.setThumbnail(mainImg);

        Color color = Color.LIGHT_GRAY;

        if (isQuestion(content_live)) {
            color = Color.BLUE;
            title = " Vos Question";
        } else if (post.hasClass("post__live-container--essential") || post.hasAttr("data-post-essential")) {
            color = Color.RED;
            title = " L'Essentiel";

            Elements elements = content_live.select("h2");
            Element bigTitle = elements.first();

            if (bigTitle != null) {
                String msg = bigTitle.text();
                if (msg.length() > 256) {
                    msg = msg.substring(0, 256 - 3) + "...";
                }
                message.addField(msg, "", false);
            }
        }

        for (Element content : content_live.getAllElements()) {
            if (content.hasClass("post__live-container--answer-text post__space-node")) { //Simple Text
                String msg = fixString(content.getElementsByClass("post__live-container--answer-text post__space-node").first().text().trim());
                System.out.println("Test 1");
                message.addField("", msg, false);
            } else if (content.hasClass("post__live-container--figure")) {//Img / Article
                System.out.println("Test 2");
                if (content.hasClass("post__live-container--figure")) {
                    Element figure = content.getElementsByClass("post__live-container--figure").first();
                    if (figure != null) {
                        for (Element figureElement : figure.getAllElements()) {
                            if (figureElement.hasClass("post__live-container--img")) {
                                String imgUrl = figureElement.attr("data-src");

                                if (!imgUrl.isBlank() && !imgUrl.isEmpty())
                                    message.setImage(imgUrl);
                            }
                        }
                    }
                }
            } else if (content.hasClass("post__live-container--comment-content")) {
                System.out.println("Test 3");
                Element questionElement = content.getElementsByClass("post__live-container--comment-blockquote").first();
                Element pseudoQuestion = content.getElementsByClass("post__live-container--comment-author").first();

                if (questionElement != null && pseudoQuestion != null) {
                    String question = questionElement.text();
                    String pseudo = pseudoQuestion.text();
                    message.addField(pseudo, fixString(question), false);
                }
            } else if (content.hasClass("article__unordered-list")) {
                Elements elements = content.select("li");
                for (Element listed : elements) {
                    message.addField("", listed.text(), false);
                }
            } else if (content.hasClass("post__live-container--citation")) {
                Elements elements = content.getElementsByClass("post__live-container--citation");
                for (Element ele : elements) {
                    Elements blockQuote = ele.select("p");
                    for (Element element : blockQuote) {
                        message.addField("", element.text(), false);
                    }
                }
            }
        }

        String postId = "";
        if (post.hasAttr("data-post-id"))
            postId = "#" + post.attr("data-post-id");

        message.setColor(color);
        message.setTitle(hour.trim() + " - " + title.trim(), liveUrl + postId);

        Listener.channels.forEach(textChannel -> textChannel.sendMessageEmbeds(message.build()).queue());
    }

    public static String getHourTitle(Element info) {
        if (info == null) {
            return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + ":" + Calendar.getInstance().get(Calendar.MINUTE);
        } else {
            Element hourElement = info.getElementsByClass("date").first();
            if (hourElement != null) {
                return hourElement.text();
            }
        }

        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + ":" + Calendar.getInstance().get(Calendar.MINUTE);
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
            Element lastPost = document.getElementsByClass("post post__live-container").first();
            if (lastPost != null) {
                Element element = lastPost.getElementsByClass("post__live-container--link").first();
                if (element != null) {
                    String newUrl = element.attr("href");
                    LOGGER.debug("[DBOT]: New Live URL = " + newUrl);
                    DBot.persistentData.set(DBot.LAST_CHECKED_URL, newUrl);
                    return getLastLive();
                } else {
                    for (Element ele : lastPost.getAllElements()) {
                        if (ele.hasAttr("href")) {
                            String possibleLink = ele.attr("href");
                            if (possibleLink.contains("live")) {
                                DBot.persistentData.set(DBot.LAST_CHECKED_URL, possibleLink);
                                return getLastLive();
                            }
                        }
                    }
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
