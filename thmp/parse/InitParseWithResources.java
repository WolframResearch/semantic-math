package thmp.parse;

/**
 * Initialize parse server with resources, e.g. file paths
 * on server.
 * @author yihed
 *
 */
public class InitParseWithResources {

	private static boolean DEBUG = false;
		
	public static void set_DEBUG(boolean debug_){
		DEBUG = debug_;
	}
	
	public static boolean isDEBUG(){
		return DEBUG;
	}
	
}
