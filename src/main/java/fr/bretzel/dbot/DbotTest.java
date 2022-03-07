package fr.bretzel.dbot;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class DbotTest {

    public static void main(String[] args) throws Exception {
        DBot.main(new String[]{"-silent"});
        String liveUrl = "https://www.lemonde.fr/international/live/2022/03/07/guerre-en-ukraine-en-direct-l-ukraine-salue-des-resultats-positifs-apres-de-nouvelles-negociations-la-russie-insatisfaite_6116412_3210.html";
        Document document = Jsoup.connect(liveUrl).get();
        Element element = document.select("section#id-253325").first();
        if (element != null)
            LiveChecker.printPost(element, document);
        else System.out.println("Cannot get the post");
    }
}
