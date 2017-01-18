package thmp;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

import thmp.ParseState.ParseStateBuilder;
import thmp.search.CollectThm;

/**
 * Parses thms and generate context vecs, write context vecs to file.
 * 
 * @author yihed
 *
 */

public class GenerateContextVector {

	//private static String CONTEXT_VEC_FILE_STR = "src/thmp/data/contextVecCommm5.txt";
	//private static String CONTEXT_VEC_FILE_STR = "src/thmp/data/contextVectorsMultilinearAlgebra.txt";
	private static String CONTEXT_VEC_FILE_STR = "src/thmp/data/contextVectorsFields.txt";
	//private static String CONTEXT_VEC_FILE_STR = "src/thmp/data/contextVecFunctionalAnalysis.txt";
	//private static String CONTEXT_VEC_FILE_STR = "src/thmp/data/contextVecTopology.txt";
	//private static String CONTEXT_VEC_FILE_STR = "src/thmp/data/contextVecAll.txt";
	
	private static Path contextVecFilePath;	
	//private static URL outputStreamURL;
	
	private static final boolean WRITE_UNKNOWNWORDS = false;
	//brackets pattern
	private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[([^\\]]*)\\]");
	
	public static void set_contextVecFilePathStr(String pathStr){
		Path path = Paths.get(pathStr);
		contextVecFilePath = path;
	}
	
	/*public static void set_contextVecOutputURL(URL url){
		outputStreamURL = url;
	}*/
	
	/**
	 * Encapsulate subclass, so can set resources such as contextVecFileStr.
	 * And can call combineContextVectors() in outer class without initializing everything here.
	 */
	public static class GetContextVec{
		// bare thm list, without latex \label's or \index's, or \ref's, etc
		private static final List<String> bareThmList;
		// matrix, same dimension of term-document matrix in search. 
		private static final List<int[]> contextVecList = new ArrayList<int[]>();
		//list of strings
		private static final List<String> contextVecStringList = new ArrayList<String>();
		
		//private static final KernelLink ml = thmp.utils.FileUtils.getKernelLink();
		
	static{
		
		bareThmList = CollectThm.ThmList.allThmsWithHypList();
		
		generateContextVec(bareThmList, contextVecList, contextVecStringList);
		
		if(WRITE_UNKNOWNWORDS){
			ThmP1.writeUnknownWordsToFile();
		}
		//obtain the average value of entries by sampling, and 
		//replace 0 by this avg.
		
		//get avg value, but should get average of the particular short-listed thms. 
		//getAverageEntryVal(contextVecStringList, ml);
		
		//convert contextVecList 
		//write context vec list to file, with curly braces
		Path fileTo = contextVecFilePath;
		if(fileTo == null){
			fileTo = Paths.get(CONTEXT_VEC_FILE_STR);
		}
		
		try {
			Files.write(fileTo, contextVecStringList, Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		
	}

	//used for testing from with outer class main().
	public static void initialize(){			
	}
	
	/**
	 * Gets average entry values of contextVecStringList
	 * to this average, saves to file.
	 * @param contextVecStringList
	 */
	private static void getAverageEntryVal(List<String> contextVecStringList, KernelLink ml){
		String contextVecAvgFileStr = "src/thmp/data/contextVecAvg.txt";
		Path contextVecAvgFilePath = Paths.get(contextVecAvgFileStr);
		int contextVecStringListSz = contextVecStringList.size();
		
		try{
			//get random indices. Make this more accurate!
			int numIndicesToTake = contextVecStringListSz < 500 ? 60 : (contextVecStringListSz < 5000 ? 100 : 150);
			
			Random rand = new Random();
			StringBuffer randomContextVecsSB = new StringBuffer();
			randomContextVecsSB.append("{");
			
			for(int i = 0; i < numIndicesToTake; i++){
				int nextRandNum = rand.nextInt(   contextVecStringListSz  );
				//randomIndices[i] = rand.nextInt(   );
				randomContextVecsSB.append(contextVecStringList.get(nextRandNum) + ", ");				
			}
			randomContextVecsSB.append("}");			
			
			ml.evaluate("Flatten[" + randomContextVecsSB + "];");
			ml.waitForAnswer();
			Expr expr = ml.getExpr();
			List<String> contextVecAvgList = new ArrayList<String>();
			contextVecAvgList.add(expr.toString());
			//store the average somewhere, write to a file
			thmp.utils.FileUtils.writeToFile(contextVecAvgList, contextVecAvgFilePath);
			
		}catch(MathLinkException e){
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Generates the context vectors.
	 * @param bareThmList
	 * @param contextVecList
	 */
	private static void generateContextVec(List<String> bareThmList, List<int[]> contextVecList,
			List<String> contextVecStringList){

		for(String thm : bareThmList){			
			String contextVecStr = createContextVector(contextVecList, thm);
			contextVecStringList.add(contextVecStr);
		}
	}
	
	}
	
	/**
	 * Creates context vector given input string.
	 * @param input User's input string.
	 * @return
	 */
	public static String createContextVector(String input) {
		//should check if actually created, return null if not
		return createContextVector(null, input);
	}
	
	/**
	 * @param contextVecList List of context vecs to be added to.
	 * Don't create new list if null.
	 * @param thm User's input string.
	 * @return
	 */
	private static String createContextVector(List<int[]> contextVecList, String thm) {
		
		//if(thm.matches("\\s*")) return "";
		
		String[] strAr = ThmP1.preprocess(thm);
		//System.out.println("****length " + strAr.length + " " + thm);
		//strAr = ThmP1.preprocess(thm);
		//List<int[]> parseContextVecList = new ArrayList<int[]>();

		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		parseStateBuilder.setWriteUnknownWordsToFile(WRITE_UNKNOWNWORDS);
		ParseState parseState = parseStateBuilder.build();
		
		for(int i = 0; i < strAr.length; i++){
			//alternate commented out line to enable tex converter
			//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
			parseState = ThmP1.tokenize(strAr[i].trim(), parseState);
			parseState = ThmP1.parse(parseState);
			//parseContextVecList.add(parseState.getContextVec());	
		}
		
		int[] contextVec = parseState.getCurThmCombinedContextVec();
		
		//get context vector and add to contextVecMx
		if(null != contextVecList){
			contextVecList.add(contextVec);
		}
		//System.out.println("Context vec: " + Arrays.toString(ThmP1.getParseContextVector()));		
		return contextVecIntArrayToString(contextVec);
	}
	
	/**
	 * Creates String representation of context vector from integer array.
	 * @param contextVec
	 * @return
	 */
	public static String contextVecIntArrayToString(int[] contextVec){
		String contextVecStr = Arrays.toString(contextVec);
		Matcher matcher = BRACKETS_PATTERN.matcher(contextVecStr);
		return matcher.replaceAll("{$1}");		
	}
	
	/**
	 * Combine context vectors in list, only add terms from higher-indexed vectors
	 * if the corresponding terms in previous vectors are 0. This is to create one single 
	 * context vector per theorem rather than multiple.
	 * @param contextVecList
	 * @return
	 */
	public static int[] combineContextVectors(List<int[]> contextVecList){
		
		/*System.out.println("^^^ contextVecList to combine ");
		for(int i = 0; i < contextVecList.size(); i++){
			System.out.println(Arrays.toString(contextVecList.get(i)));
		}*/
		if(0 == contextVecList.size()){
			return null;
		}
		int contextVecLength = contextVecList.get(0).length;
		int contextVecListSz = contextVecList.size();
		
		int[] combinedVector = new int[contextVecLength];
		for(int i = 0; i < contextVecLength; i++){
			for(int j = 0; j < contextVecListSz; j++){
				int entry = contextVecList.get(j)[i];
				int prevEntry = combinedVector[i];
				if(prevEntry == 0 || prevEntry < 0 && entry > 0){					
					combinedVector[i] = entry;
				}
				if(entry > 0) break;
			}
		}
		return combinedVector;
	}
	
	public static void main(String[] args){
		GetContextVec.initialize();
	}
}
