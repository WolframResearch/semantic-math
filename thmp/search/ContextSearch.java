package thmp.search;

import java.util.Arrays;
import java.util.List;

import com.wolfram.jlink.KernelLink;

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
	private static final List<String> contextVecStringList = new ArrayList<String>();
	
	static{
		//read in contextVecStringList
		
	}
	/**
	 * @param queryVec already in WL list form: {1, 5, ...}
	 * Gets list of vectors from GenerateContextVectors.java, 
	 * pick out the nearest structual vectors using Nearest[].
	 */
	public static void contextSearch(String queryVec, List<Integer> nearestThmIndices){
		
		//get the nearest thms from the 
		
		
	}
	
}
