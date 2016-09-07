package thmp.search;
/**
 * Class of constants, such as special word lists, in search.
 * This was creted to avoid loading a class unless absolutely
 * necessary, eg not when just invoking a constant,
 * thus reducing chance of cyclic initialization dependency.
 * @author yihed
 *
 */
public class ConstantsInSearch {


	//regex of key words that should be intentioanlly weighed more, eg "definition"
	//should be weighed as high as the highest word in vector
	private static final String PRIORITY_WORDS = "definition|define";
	
	public static String get_priorityWords(){
		return PRIORITY_WORDS;
	}
}
