package thmp.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;

import thmp.DetectHypothesis;
import thmp.ThmInput;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Uses CollectThm.ThmWordsMaps.buildMapsNoAnno() to generate synonyms.
 * 
 * @author yihed
 *
 */
public class GenerateSynonymsWordData {

	private static final Pattern SINGLE_LINE_SKIP_PATTERN = Pattern.compile("^\\\\.*|^%.*|.*FFFFFFFF.*");
	public static void main(String[] args) {
		/*
		 * private static void
		 * buildMapsNoAnno(ImmutableList.Builder<ImmutableMap<String, Integer>>
		 * thmWordsFreqListBuilder, Map<String, Integer> docWordsFreqPreMap,
		 * ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder,
		 * List<String> thmList, List<String> skipGramWordList_)
		 */
		// create list of strings, or just one string
		String skipGramWordsListStr = "src/thmp/data/skipGramWordsList.txt";
		List<String> sentenceList = new ArrayList<String>();
		
		String sourceFileStr = "src/thmp/data/Total.txt";
		BufferedReader srcFileReader = null;
		
		try {
			srcFileReader = new BufferedReader(new FileReader(new File(sourceFileStr)));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			FileUtils.silentClose(srcFileReader);
			return;
		}
		try {
			String line;
			while ((line = srcFileReader.readLine()) != null) {
				if (WordForms.getWhiteEmptySpacePattern().matcher(line).matches()) {
					continue;
				}

				if (SINGLE_LINE_SKIP_PATTERN.matcher(line).matches()) {
					continue;
				}
				// should skip certain sections, e.g. \begin{proof}
				Matcher skipMatcher = WordForms.getSKIP_PATTERN().matcher(line);
				if (skipMatcher.find()) {
					while ((line = srcFileReader.readLine()) != null) {
						if (WordForms.getEND_SKIP_PATTERN().matcher(line).find()) {
							break;
						}
					}
					continue;
				}
				
				line = ThmInput.removeTexMarkup(line, null, null);				
				sentenceList.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			FileUtils.silentClose(srcFileReader);
		}
		System.out.println("Done with gathering source text!");
		
		List<String> skipGramWordList = new ArrayList<String>();
		CollectThm.ThmWordsMaps.createSkipGramWordList(sentenceList, skipGramWordList);
		//FileUtils.writeToFile(skipGramWordList, skipGramWordsListStr);
		FileUtils.appendObjToFile(skipGramWordList, skipGramWordsListStr);
	}

}
