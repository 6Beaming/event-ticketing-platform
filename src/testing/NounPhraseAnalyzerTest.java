package testing;

import reports.EventCommentReport;
import reports.EventNounPhraseReport;
import reports.NounPhraseAnalyzer;

import java.util.List;

public class NounPhraseAnalyzerTest {

    public static void main(String[] args) {

        List<EventCommentReport> comments = List.of(

                new EventCommentReport(
                        1,
                        "Coldplay Concert",
                        "The amazing light show was beautiful. The music performance was incredible."
                ),

                new EventCommentReport(
                        1,
                        "Coldplay Concert",
                        "The light show and stage design were excellent."
                ),

                new EventCommentReport(
                        2,
                        "Jazz Festival",
                        "The amazing jazz performance had wonderful musicians."
                )
        );


        NounPhraseAnalyzer analyzer =
                new NounPhraseAnalyzer();


        List<EventNounPhraseReport> results =
                analyzer.analyze(comments);


        for (EventNounPhraseReport report : results) {

            System.out.println(
                    "Event: "
                            + report.getEventTitle()
            );

            report.getPhrases()
                    .forEach(
                            phrase ->
                                    System.out.println(
                                            phrase.getPhrase()
                                                    + " -> "
                                                    + phrase.getCount()
                                    )
                    );

            System.out.println();
        }
    }
}