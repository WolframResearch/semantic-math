package thmp.search;

import com.wolfram.jlink.*;

public class TestJLink {
	
	public static final String[] ARGV = new String[]{"-linkmode", "listen", "-linkname", 
			"\"/Applications/Mathematica.app/Contents/MacOS/MathKernel\" -mathlink"};
	public static final String[] ARGV0 = new String[]{"-linkmode", "create", "-linkname", 
	"\"random1\" -mathlink"};
	public static final String[] ARGV1 = new String[]{"-linkmode", "create", "-linkname", 
			"\"/Applications/Mathematica.app/Contents/MacOS/MathKernel\" -mathlink"};
	public static final String[] ARGV2 = new String[]{"-linkmode", "create", "-linkname", 
	"\"/Applications/Mathematica.app/Contents/MacOS/MathKernel\" -mathlink"};	
	public static final String[] ARGV3 = new String[]{"-linkcreate", "-linkname", 
	"\"random14\" -mathlink", "-linkprotocol", "SharedMemory"};	
	public static final String[] ARGV4 = new String[]{"-linkmode", "listen", "-linkname", "\"random7\" -mathlink"};	
	
	//to listen: {"-linkmode", "listen", "-linkname", "\"random4\" -mathlink"}
	//to create: {"-linkcreate", "-linkname", "\"random0\" -mathlink", "-linkprotocol", "SharedMemory"}
	
	public static void main(String[] args){
		//KernelLink ml = StdLink.getLink();
		//ml.evaluateToOutputForm("1+3", 0);
		KernelLink ml = null;
		try{
			ml = MathLinkFactory.createKernelLink(ARGV3);
			//System.out.println("done launching");
		}catch(MathLinkException e){
			System.out.println("error at launch!");
			e.printStackTrace();
			return;
		}
		
		//try{
			System.out.println("got here");
			//ml.discardAnswer();
			System.out.println("got here2");
			String result = ml.evaluateToOutputForm("4+4", 0);
			System.out.println(result);
		//}catch(MathLinkException e){
			//e.printStackTrace();
		//}
		
	}
}
