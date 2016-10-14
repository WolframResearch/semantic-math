package thmp;

import java.io.FileNotFoundException;
import java.io.IOException;
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

import org.apache.logging.log4j.core.util.FileUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

import thmp.search.CollectThm;

/**
 * Parses thms and generate context vecs, write context vecs to file.
 * 
 * @author yihed
 *
 */

public class GenerateContextVector {

	private static String contextVecFileStr = "src/thmp/data/contextVectors.txt";
	private static Path contextVecFilePath;	
	//brackets pattern
	private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[([^\\]]*)\\]");
	
	public static void set_contextVecFilePath(String pathStr){
		Path path = Paths.get(pathStr);
		contextVecFilePath = path;
	}
	
	/**
	 * Encapsulate subclass, so can set resources such as contextVecFileStr.
	 * And can call combineContextVectors() in outer class without initializing everything here.
	 * 
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
		/*Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}*/
		
		bareThmList = CollectThm.ThmList.get_bareThmList();
		generateContextVec(bareThmList, contextVecList, contextVecStringList);
		
		//obtain the average value of entries by sampling, and 
		//replace 0 by this avg.
		
		//get avg value, but should get average of the particular short-listed thms. 
		//getAverageEntryVal(contextVecStringList, ml);
		
		//convert contextVecList 
		//write context vec list to file, with curly braces
		Path fileTo = contextVecFilePath;
		if(fileTo == null){
			fileTo = Paths.get(contextVecFileStr);
		}
		
		try {
			Files.write(fileTo, contextVecStringList, Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	//used for testing from with outer class main().
	private static void initialize(){			
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
			int numIndicesToTake = contextVecStringListSz < 500 ? 60 : (contextVecStringListSz < 5000 ? 100 : 150);;
			
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
	 * @param input
	 * @return
	 */
	public static String createContextVector(String input) {
		//should check if actually created, return null if not
		return createContextVector(null, input);
	}
	
	/**
	 * @param contextVecList
	 * @param thm
	 * @return
	 */
	private static String createContextVector(List<int[]> contextVecList, String thm) {
		
		//if(thm.matches("\\s*")) return "";
		
		String[] strAr = ThmP1.preprocess(thm);
		System.out.println("****length " + strAr.length + " " + thm);
		//strAr = ThmP1.preprocess(thm);
		
		List<int[]> parseContextVecList = new ArrayList<int[]>();
		for(int i = 0; i < strAr.length; i++){				
			//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim())));				
			ThmP1.parse(ThmP1.tokenize(strAr[i].trim()));
			parseContextVecList.add(ThmP1.getParseContextVector());	
		}
		
		int[] contextVec = combineContextVectors(parseContextVecList);
		//get context vector and add to contextVecMx
		if(contextVecList != null){
			contextVecList.add(contextVec);
		}		
		//System.out.println("Context vec: " + Arrays.toString(ThmP1.getParseContextVector()));
		String contextVecStr = Arrays.toString(contextVec);
		Matcher matcher = BRACKETS_PATTERN.matcher(contextVecStr);
		contextVecStr = matcher.replaceAll("{$1}");
		return contextVecStr;
	}
	
	/**
	 * Combine context vectors in list, only add terms from higher-indexed vectors
	 * if the corresponding terms in previous vectors are 0.
	 * @param contextVecList
	 * @return
	 */
	public static int[] combineContextVectors(List<int[]> contextVecList){
		int contextVecLength = contextVecList.get(0).length;
		int contextVecListSz = contextVecList.size();
		
		int[] combinedVector = new int[contextVecLength];
		for(int i = 0; i < contextVecLength; i++){
			for(int j = 0; j < contextVecListSz; j++){
				int entry = contextVecList.get(j)[i];
				combinedVector[i] = entry;
				if(entry != 0) break;
			}
		}
		return combinedVector;
	}
	
	public static void main(String[] args){
		GetContextVec.initialize();
	}
}
