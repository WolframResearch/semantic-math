package thmp.search;

/**
 * Fish out 2-grams, compute probabilities of one word following another.
 * O(N^2) time performance, where N is number of words.
 * Put words in HashMaps, where each entry is another map, with frequencies of words
 * that follow. N^2 space. Skip if common word.
 * F
 * @author yihed
 *
 */

public class NGramSearch {

	private static void e(){
		Map<String, HashMap<String, Integer>> nGramMap = new HashMap<String, HashMap<String, Integer>>();
		//get thmList from CollectThm
		List<String> thmList = CollectThm.get_thmList();
		
		//skip nonMathFluffWords, collapse list
		for(String thm : thmList){
			//split into words
			String[] thmAr = thm.split(CollectThm.splitDelim());
			for(int i = 0; i < thmAr.length; i++){
				String word = thmAr[i];
				
			}
		}
	}
	
	/**
	 * Computes frequencies 
	 */
	private static void computeFreq(){
		
	}
	
	public static void main(String[] args){
		
	}
	
}
