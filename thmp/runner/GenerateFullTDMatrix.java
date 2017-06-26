package thmp.runner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import thmp.parse.TheoremContainer;
import thmp.search.Searcher;
import thmp.search.ThmSearch;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Generate full-dimensional Term-Document matrices from 
 * given serialized list of ParsedExpression's or ThmHypPair,
 * corresponding to the theorems gathered from a tar.
 * This is useful, say if we wanted to update the matrix creation
 * algorithm, or update the vocabulary.
 * 
 * @author yihed
 *
 */
public class GenerateFullTDMatrix {

	public static void main(String[] args) throws IOException{
		//enter file containing list of of paths or files that are FullTDMatrix's
		
		if(args.length < 1){
			System.out.println("Enter a file containing root paths to directories containing "
					+ "full-dim matrices! E.g. 0208_001Untarred/0208");
			return;
		}		
		String fileName = args[0];
		InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(fileName));
		BufferedReader bReader = new BufferedReader( inputStreamReader);
		String line;
		try{
			while((line=bReader.readLine()) != null){
				if(WordForms.getWhiteEmptySpacePattern().matcher(line).matches()){
					continue;
				}
				int lineLen = line.length();
				if(lineLen > 3 && line.substring(lineLen-3).equals(".mx")){
					line = FileUtils.findFilePathDirectory(line);
				}
				createFullTDMatrix(line);			
			}
		}finally{
			bReader.close();
			inputStreamReader.close();
		}
	}

	private static void createFullTDMatrix(String dirName){
		String texFilesDirPath = thmp.utils.FileUtils.addIfAbsentTrailingSlashToPath(dirName);
		
		String fullTermDocumentMxPath = texFilesDirPath + ThmSearch.TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME + ".mx";
		
		//some are named parsedExpressionList, but others are without the extension .dat
		String thmListPath = texFilesDirPath + ThmSearch.TermDocumentMatrix.PARSEDEXPRESSION_LIST_FILE_NAME_ROOT ;
		if(!Files.isRegularFile(Paths.get(thmListPath))){
			thmListPath = thmListPath + ".dat";
		}		
		@SuppressWarnings("unchecked")
		List<? extends TheoremContainer> thmContainerList = (List<? extends TheoremContainer>)FileUtils.deserializeListFromFile(thmListPath);
		
		ImmutableList<TheoremContainer> immutableThmContainerList = ImmutableList.copyOf(thmContainerList);
		String pathToWordFreqMap = Searcher.SearchMetaData.wordDocFreqMapPath();
		Map<String, Integer> wordFreqMap = getWordFreqMap(pathToWordFreqMap);
		//first serialize full dimensional TD mx, then project using provided projection mx.
		ThmSearch.TermDocumentMatrix.serializeHighDimensionalTDMx(immutableThmContainerList, fullTermDocumentMxPath, wordFreqMap);		
		FileUtils.closeKernelLinkInstance();		
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, Integer> getWordFreqMap(String pathToWordFreqMap){
		Map<String, Integer> wordFreqMap = ((List<Map<String, Integer>>)FileUtils.deserializeListFromFile(pathToWordFreqMap)).get(0);
		return wordFreqMap;
	}
	
}
