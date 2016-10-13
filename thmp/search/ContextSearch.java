package thmp.search;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

import thmp.ThmP1;
import thmp.utils.FileUtils;

/**
 * Search results based on context vectors produced during parsing.
 * 
 * @author yihed
 *
 */
public class ContextSearch {

	//bare thm list, without latex \label's or \index's, or refs, etc
	//private static final List<String> bareThmList = CollectThm.ThmList.get_bareThmList();
	private static final KernelLink ml = FileUtils.getKernelLink();
	//list of strings "{1, 0, ...}" corresponding to contexts of thms, same indexing as in thmList.
	private static final List<String> contextVecStringList = new ArrayList<String>();
	private static final int LIST_INDEX_SHIFT = 1;
	
	static{
		//need to set this when deployed to VM
		String contextVecFileStr = "src/thmp/data/contextVectors.txt";
		//read in contextVecStringList from file, result of GenerateContextVectors.
		//FileReader contextVecFile;
		//BufferedReader contextVecFileBReader;
		
		BufferedReader contextVecFileBReader = null;
		try{
			FileReader contextVecFileReader = new FileReader(contextVecFileStr);
			contextVecFileBReader = new BufferedReader(contextVecFileReader);
			String line;
			while((line = contextVecFileBReader.readLine()) != null){
				//a line is a String of the form "{1, 0, ...}"
				contextVecStringList.add(line);
			}			
			contextVecFileBReader.close();
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			if(contextVecFileBReader != null){
				closeBuffer(contextVecFileBReader);
			}
		}
		
	}
	
	/**
	 * Close the bufferedReader.
	 * @param bf
	 */
	private static void closeBuffer(BufferedReader bf){
		try{
			bf.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @param queryVec already in WL list form: {1, 5, ...}
	 * @param nearestThmIndexList List of thm indices, resulting from other 
	 * search algorithms such as SVD and/or intersection.
	 * Gets list of vectors from GenerateContextVectors.java, 
	 * pick out the nearest structual vectors using Nearest[].
	 * @return Gives an ordered list of vectors based on context.
	 */
	public static List<Integer> contextSearch(String queryVec, List<Integer> nearestThmIndexList){
		StringBuffer nearestThmsContextVecSB = new StringBuffer("{");
		int nearestThmIndexListSz = nearestThmIndexList.size();
		List<Integer> nearestVecList = null;
		
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			if(i < nearestThmIndexListSz-1){
				//except at the end!
				nearestThmsContextVecSB.append(contextVecStringList.get(thmIndex) + ",");
			}else{
				nearestThmsContextVecSB.append(contextVecStringList.get(thmIndex) + "}");
			}
			
		}
		
		//get the nearest thms from the list of thm (indices) passed in
		try{
			//get average of the nearestThmsContextVecSB!
			ml.evaluate("contextVecMean = Mean[Flatten[" + nearestThmsContextVecSB + "]];");
			ml.discardAnswer();
			ml.evaluate("q=" + queryVec + "/.{0->contextVecMean};");
			ml.discardAnswer();
			
			//ml.evaluate("Nearest[v->Range[Dimensions[v][[1]]], First@Transpose[q],"+numNearest+"] - " + LIST_INDEX_SHIFT);
			ml.evaluate("Nearest[" + nearestThmsContextVecSB + "->Range["+ nearestThmIndexListSz+"], q," 
					+ nearestThmIndexListSz +"]-" + LIST_INDEX_SHIFT);
			ml.waitForAnswer();
			Expr nearestContextVecs = ml.getExpr();
			
			System.out.println(nearestContextVecs);
			//use this when using Nearest
			//int[] nearestVecArray = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
			int[] nearestVecArray = (int[])nearestContextVecs.asArray(Expr.INTEGER, 1);
			Integer[] nearestVecArrayBoxed = ArrayUtils.toObject(nearestVecArray);
			nearestVecList = Arrays.asList(nearestVecArrayBoxed);
			
			//for(int i = nearestVecList.size()-1; i > -1; i--){
			for(int i = 0; i < nearestVecList.size(); i++){
				int thmIndex = nearestVecList.get(i);
				System.out.println(TriggerMathThm2.getThm(thmIndex));
				//System.out.println("thm vec: " + TriggerMathThm2.createQuery(TriggerMathThm2.getThm(d)));
			}
			
		}catch(MathLinkException | ExprFormatException e){
			e.printStackTrace();
		}
		return nearestVecList;
	}
	
	
	/**
	 * Creates query vector given String input.
	 * @param input Query input.
	 * @return
	 */
	private static String createQueryVector(String input){
		
	}
	
	
}
