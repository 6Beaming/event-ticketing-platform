package testing;

import opennlp.tools.tokenize.SimpleTokenizer;

public class OpenNLPTest {

    public static void main(String[] args) {

        String text = "The light show was amazing.";

        String[] words =
                SimpleTokenizer.INSTANCE.tokenize(text);

        for (String word : words) {
            System.out.println(word);
        }
    }
}