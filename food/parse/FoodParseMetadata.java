package food.parse;

import thmp.utils.FileUtils;

/**
 * Meta data used for parsing recipes.
 * @author yihed
 */
public class FoodParseMetadata {

	public static final String foodTrieSerialPath 
		= FileUtils.getPathIfOnServlet("src/thmp/data/foodTrie.dat");
	public static final String foodMapSerialPath 
		= FileUtils.getPathIfOnServlet("src/thmp/data/foodMap.dat");
	
	static{
		
	}
	
}
