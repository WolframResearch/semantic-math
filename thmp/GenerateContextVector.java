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

	// bare thm list, without latex \label's or \index's, or \ref's, etc
	private static final List<String> bareThmList;
	// matrix, same dimension of term-document matrix in search. 
	private static final List<int[]> contextVecList = new ArrayList<int[]>();
	//list of strings
	private static final List<String> contextVecStringList = new ArrayList<String>();
	//brackets pattern
	private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[([^\\]]*)\\]");
	private static final KernelLink ml = thmp.utils.FileUtils.getKernelLink();
	
	static{
		Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		bareThmList = CollectThm.ThmList.get_bareThmList();
		generateContextVec(bareThmList, contextVecList, contextVecStringList);
		
		//obtain the average value of entries by sampling, and 
		//replace 0 by this avg.
		
		
		//get avg
		getAverageEntryVal(contextVecStringList, ml);
		
		//convert contextVecList 
		//write context vec list to file, with curly braces
		Path fileTo = Paths.get("src/thmp/data/contextVectors.txt");
		
		try {
			Files.write(fileTo, contextVecStringList, Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Gets average entry values of contextVecStringList
	 * to this average.
	 * @param contextVecStringList
	 */
	private static void getAverageEntryVal(List<String> contextVecStringList, KernelLink ml){
		
		try{
			//get random indices
			int numIndicesToTake = ;
			int[] randomIndices = new int[numIndicesToTake];
			Random rand = new Random();
			StringBuffer randomContextVecsSB = new StringBuffer();
			randomContextVecsSB.append("{");
			for(int i = 0; i < numIndicesToTake; i++){
				int nextRandNum = rand.nextInt(   );
				//randomIndices[i] = rand.nextInt(   );
				randomContextVecsSB.append(contextVecStringList.get(i) + ", ");
				
			}
			randomContextVecsSB.append("}");
			
			
			ml.evaluate("Flatten[" + randomContextVecsSB + "];");
			ml.waitForAnswer();
			Expr expr = ml.getExpr();
			//store the average somewhere
			
			
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
		
		
		String[] strAr;
		for(String thm : bareThmList){
			
			strAr = ThmP1.preprocess(thm);
			
			for(int i = 0; i < strAr.length; i++){				
				//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim())));
				try {
					ThmP1.parse(ThmP1.tokenize(strAr[i].trim()));
				} catch (IOException e) {
					e.printStackTrace();
				}				
			}
			
			int[] contextVec = ThmP1.getParseContextVector();
			//get context vector and add to contextVecMx
			contextVecList.add(contextVec);
			//System.out.println("Context vec: " + Arrays.toString(ThmP1.getParseContextVector()));
			String contextVecStr = Arrays.toString(contextVec);
			Matcher matcher = BRACKETS_PATTERN.matcher(contextVecStr);
			contextVecStr = matcher.replaceAll("{$1}");
			contextVecStringList.add(contextVecStr);
		}
	}
	
	public static void main(String[] args){
		
	}
}
