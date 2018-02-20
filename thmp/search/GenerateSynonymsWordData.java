package thmp.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;

import thmp.parse.DetectHypothesis;
import thmp.parse.ThmInput;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Uses CollectThm.ThmWordsMaps.buildMapsNoAnno() to generate synonyms,
 * using word embeddings, e.g. word2vec. In particular prepare data for 
 * skip gram model.
 * 
 * @author yihed
 *
 */
public class GenerateSynonymsWordData {

	public static final String skipGramTrainDataFileName = "skipGramTrain.txt";
	private static final Pattern SINGLE_LINE_SKIP_PATTERN = Pattern.compile("^\\\\.*|^%.*|.*FFFFFF.*|.*fffff.*|\\/.*");
	private static final Pattern newLinePatt = Pattern.compile("\n");
	
	static{
		CollectThm.ThmList.set_gather_skip_gram_words_toTrue();
	}
	
	public static void main(String[] args) {
		/*
		 * private static void
		 * buildMapsNoAnno(ImmutableList.Builder<ImmutableMap<String, Integer>>
		 * thmWordsFreqListBuilder, Map<String, Integer> docWordsFreqPreMap,
		 * ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder,
		 * List<String> thmList, List<String> skipGramWordList_)
		 */
		// create list of strings from raw file.
		String skipGramWordsListPath = "src/thmp/data/skipGramWordsList2.txt";
		
		
		//String sourceFileStr = "src/thmp/data/Total.txt";
		String sourceFileStr = "/Users/yihed/Documents/arxivTexSrc/020Total.txt";
		sourceFileStr = "/Users/yihed/Downloads/test/skipGram2.txt";
		
		if(args.length < 1) {
			System.out.println("No data source path supplied. Using relative path " + sourceFileStr);
		}else {
			sourceFileStr = args[0];
		}
		
		/*BufferedReader srcFileReader = null;		
		try {
			//new BufferedReader(
			  //         new InputStreamReader(new FileInputStream(fileDir), "UTF-8"));
			//srcFileReader = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFileStr), "UTF-16"));
			srcFileReader = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFileStr)));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			FileUtils.silentClose(srcFileReader);
			return;
		}*/
		
		String sourceStr = FileUtils.readStrFromFile(sourceFileStr);
		
		extractSkipGramWords(sourceStr, skipGramWordsListPath);
	}

	/**
	 * Extracts skips grams to target file given source data path.
	 * @param sourceFileStr Source text to extract skip grams from.
	 * @param skipGramWordsListPath target path to write skip grams to.
	 */
	public static void extractSkipGramWords(
			String sourceStr, String skipGramWordsListPath) {
		
		List<String> sentenceList = new ArrayList<String>();
		//List<String> lines = FileUtils.readLinesFromFile(sourceFileStr);
		//Training data needs to come in sentences.
		String[] lines = newLinePatt.split(sourceStr.replace(". ", "\n"));
		int linesLen = lines.length;
		
		//try {
			for (int i = 0; i < linesLen; i++) {
				String line = lines[i];
				if (WordForms.getWhiteEmptySpacePattern().matcher(line).matches()) {
					continue;
				}

				if (SINGLE_LINE_SKIP_PATTERN.matcher(line).matches()) {
					continue;
				}
				//byte lineAr[] = line.getBytes("UTF-8"); 
				//line = new String(lineAr, "UTF-8"); 
				
				// should skip certain sections, e.g. \begin{proof}
				/*Matcher skipMatcher = WordForms.getSKIP_PATTERN().matcher(line);
				if (skipMatcher.find()) {
					while (i < linesLen) {
						line = lines.get(i);
						if (WordForms.getEND_SKIP_PATTERN().matcher(line).find()) {
							break;
						}
						i++;
					}
					continue;
				}	*/		
				line = ThmInput.removeTexMarkup(line, null, null);	
				sentenceList.add(line);
			}
		/*} catch (IOException e) {
			e.printStackTrace();
		} finally {
			FileUtils.silentClose(srcFileReader);
		}*/
		System.out.println("Done with gathering source text!");
		
		List<String> skipGramWordList = new ArrayList<String>();
		CollectThm.ThmWordsMaps.createSkipGramWordList(sentenceList, skipGramWordList);
		System.out.println("Writing skipGramWordList to file!");
		FileUtils.writeToFile(skipGramWordList, skipGramWordsListPath);
		//FileUtils.appendObjToFile(skipGramWordList, skipGramWordsListStr);
	}

}
