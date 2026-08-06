package reports;

public final class NounPhraseCount {

    private final String phrase;
    private final int count;


    public NounPhraseCount(
            String phrase,
            int count
    ) {
        this.phrase = phrase;
        this.count = count;
    }


    public String getPhrase() {
        return phrase;
    }


    public int getCount() {
        return count;
    }
}