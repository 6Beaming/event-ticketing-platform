package reports;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class NounPhraseAnalyzer {

	private final POSTaggerME tagger;

	public NounPhraseAnalyzer() {
		try {
			POSModel model = new POSModel(new FileInputStream("models/en-pos-maxent.bin"));
			this.tagger = new POSTaggerME(model);
		} catch (IOException exception) {
			throw new RuntimeException(
					"Unable to load POS model.",
					exception);
		}
	}

	public List<EventNounPhraseReport> analyze(
			List<EventCommentReport> comments) {
		Map<Integer, List<EventCommentReport>> grouped = comments.stream()
				.collect(Collectors.groupingBy(
						EventCommentReport::getEventId));

		List<EventNounPhraseReport> reports = new ArrayList<>();

		for (List<EventCommentReport> eventComments : grouped.values()) {
			EventCommentReport first = eventComments.get(0);

			Map<String, Integer> counts = new HashMap<>();

			for (EventCommentReport comment : eventComments) {
				List<String> phrases = extractNounPhrases(comment.getCommentText());

				for (String phrase : phrases) {
					counts.merge(phrase, 1, Integer::sum);
				}
			}

			List<NounPhraseCount> top = counts.entrySet()
					.stream()
					.sorted(
							Map.Entry
									.<String, Integer>comparingByValue()
									.reversed())
					.limit(10)
					.map(entry -> new NounPhraseCount(
							entry.getKey(),
							entry.getValue()))
					.toList();

			reports.add(
					new EventNounPhraseReport(
							first.getEventId(),
							first.getEventTitle(),
							top));
		}

		return reports;
	}

	private List<String> extractNounPhrases(String text) {

		String[] tokens = SimpleTokenizer.INSTANCE.tokenize(text);

		String[] tags = tagger.tag(tokens);

		List<String> phrases = new ArrayList<>();

		StringBuilder current = new StringBuilder();

		for (int i = 0; i < tokens.length; i++) {
			String tag = tags[i];
			/*
			 * Keep:
			 * JJ adjective
			 * NN noun
			 * NNS plural noun
			 *
			 * Example:
			 * beautiful/JJ
			 * light/NN
			 * show/NN
			 */
			if (tag.startsWith("JJ") || tag.startsWith("NN")) {
				if (!current.isEmpty()) {
					current.append(" ");
				}
				current.append(tokens[i].toLowerCase());
			} else {
				if (current.length() > 0) {
					phrases.add(current.toString());
					current.setLength(0);
				}
			}
		}

		if (current.length() > 0) {
			phrases.add(
					current.toString());
		}

		return phrases.stream()
				.filter(
						phrase -> phrase.split(" ").length >= 2)
				.toList();
	}
}