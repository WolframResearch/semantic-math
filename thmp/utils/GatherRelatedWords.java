package thmp.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GatherRelatedWords {

	private static final Pattern betweenWordGroupPattern = Pattern.compile("\\]\\), \\(\\[");
	private static final Pattern withinWordGroupPattern = Pattern.compile("(?<=\\]), (?=\\[)");
	// private static final Pattern wordGroupPattern = Pattern.compile("\\],
	// \\[");
	private static final Pattern wordsPattern = Pattern.compile("\\', \\'");
	private static final Pattern EMPTY_WORD_PATTERN = Pattern.compile("\\[\\]|\\[|\\]");

	// private static final Pattern VALID_WORD_PATTERN =
	// Pattern.compile("(?:(?<![A-z]*))([A-z]+)(?:(?![A-z]*))");
	private static final Pattern VALID_WORD_PATTERN = Pattern.compile("(?:.*?)([A-Za-z\\s-]+)(?:.*?)");

	public static void main(String[] args) {
		String fileStr = "src/thmp/data/scrapedRelatedWords.txt";
		Map<String, RelatedWords> relatedWordsMap = createRelatedWordsMapFromFile(fileStr);
		boolean serializeBool = true;
		String serializationFileStr = "src/thmp/data/relatedWordsMap.dat";
		if(serializeBool){
			List<Map<String, RelatedWords>> relatedWordsMapList = new ArrayList<Map<String, RelatedWords>>();
			relatedWordsMapList.add(relatedWordsMap);
			FileUtils.serializeObjToFile(relatedWordsMapList, serializationFileStr);
		}		
	}

	public static Map<String, RelatedWords> createRelatedWordsMapFromFile(String fileStr) {
		// word, synonyms, antonyms, related words.
		// has form e.g. (['arithmetic spiral'], ['Archimedean spiral', 'spiral
		// of Archimedes'], [], []), (['...'
		// String fileStr = "src/thmp/data/scrapedRelatedWords.txt";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(fileStr));
		} catch (FileNotFoundException e) {

			throw new IllegalStateException(e);
		}
		String line;
		String[] wordGroupsAr = null;
		try {
			//
			if (null != (line = br.readLine())) {
				wordGroupsAr = betweenWordGroupPattern.split(line);
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} finally {
			FileUtils.silentClose(br);
		}

		Map<String, RelatedWords> relatedWordsMap = new HashMap<String, RelatedWords>();

		for (String wordGroupSameMeaning : wordGroupsAr) {
			// System.out.println("wordGroupSameMeaning: " +
			// wordGroupSameMeaning);
			String[] wordGroupSameMeaningAr = withinWordGroupPattern.split(wordGroupSameMeaning);
			// word, synonyms, antonyms, related words.
			assert wordGroupSameMeaningAr.length == 4;

			// first group is the word
			String word = wordGroupSameMeaningAr[0];
			System.out.println("wordGroupSameMeaningAr " + Arrays.toString(wordGroupSameMeaningAr));
			String[] synonymsAr = wordsPattern.split(wordGroupSameMeaningAr[1]);
			stripWordsFromArray(synonymsAr);
			String[] antonymsAr = wordsPattern.split(wordGroupSameMeaningAr[2]);
			stripWordsFromArray(antonymsAr);
			String[] relatedWordsAr = wordsPattern.split(wordGroupSameMeaningAr[3]);
			stripWordsFromArray(relatedWordsAr);
			System.out.println("word: " + word + " synonymsAr: " + Arrays.toString(synonymsAr) + " antonymsAr: "
					+ Arrays.toString(antonymsAr) + " relatedWordsAr: " + Arrays.toString(relatedWordsAr));

			relatedWordsMap.put(word,
					new RelatedWords(Arrays.asList(synonymsAr), Arrays.asList(synonymsAr), Arrays.asList(synonymsAr)));

			/*
			 * for(String wordGroup : wordGroupSameMeaningAr){ String[]
			 * individualWords = wordsPattern.split(wordGroup); }
			 */
		}
		return relatedWordsMap;
	}

	/**
	 * Modifies the words array in-place, strip special chars such as quotations
	 * from each word.
	 * 
	 * @param synonymsAr
	 */
	public static void stripWordsFromArray(String[] wordsAr) {

		for (int i = 0; i < wordsAr.length; i++) {
			String word = wordsAr[i];
			Matcher matcher;
			if ((matcher = VALID_WORD_PATTERN.matcher(word)).matches()) {
				// System.out.println("VALID_WORD_PATTERN matches!");
				// System.out.println("WORD: " + word);
				wordsAr[i] = matcher.group(1);
			} else if (EMPTY_WORD_PATTERN.matcher(word).matches()) {
				wordsAr[i] = "";
			}

		}
	}

	private static class RelatedWords {
		// private String word;
		private List<String> synonymsList;
		private List<String> antonymsList;
		private List<String> relatedWordsList;

		public RelatedWords(List<String> synonymsList_, List<String> antonymsList_, List<String> relatedWordsList_) {
			// this.word = word_;
			if (null == synonymsList_) {
				this.synonymsList = Collections.<String> emptyList();
			} else {
				this.synonymsList = synonymsList_;
			}
			if (null == antonymsList_) {
				this.antonymsList = Collections.<String> emptyList();
			} else {
				this.antonymsList = antonymsList_;
			}
			if (null == relatedWordsList_) {
				this.relatedWordsList = Collections.<String> emptyList();
			} else {
				this.relatedWordsList = relatedWordsList_;
			}
		}

		/**
		 * @return the synonymsList
		 */
		public List<String> getSynonymsList() {
			return synonymsList;
		}

		/**
		 * @return the antonymsList
		 */
		public List<String> getAntonymsList() {
			return antonymsList;
		}

		/**
		 * @return the relatedWordsList
		 */
		public List<String> getRelatedWordsList() {
			return relatedWordsList;
		}

	}
}
