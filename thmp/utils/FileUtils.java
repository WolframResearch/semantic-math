package thmp.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

/**
 * Utility functions pertaining to files.
 * 
 * @author yihed
 *
 */
public class FileUtils {

	//singleton, only one instance should exist
	private static volatile KernelLink ml;
	
	/**
	 * Write content to file at absolute path.
	 * 
	 * @param contentList
	 * @param fileTo
	 */
	public static void writeToFile(List<String> contentList, Path fileTo) {
		try {
			Files.write(fileTo, contentList, Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write content to file at absolute path.
	 * 
	 * @param contentList
	 * @param fileTo
	 */
	public static void writeToFile(List<String> contentList, String fileToStr) {
		Path toPath = Paths.get(fileToStr);
		writeToFile(contentList, toPath);
	}
	/**
	 * Get KernelLink instance, 
	 * create one is none exists already.
	 * @return KernelLink instance.
	 */
	public static KernelLink getKernelLinkInstance() {
		//double-checked locking
		if(null == ml){
			//finer-grained locking than synchronizing whole method
			synchronized(FileUtils.class){
				//need to check again, in case ml was initialized while 
				//acquiring the lock.
				if(null == ml){
					ml = createKernelLink();
				}
			}			
		}	
		return ml;		
	}
	
	/**
	 * Creates the kernel link instance if none exists yet in this
	 * JVM session. Ensures only a single link instance is created, since 
	 * only one is needed.
	 * @return
	 */
	private static KernelLink createKernelLink() {

		String[] ARGV;
		
		String OS_name = System.getProperty("os.name");
		if (OS_name.equals("Mac OS X")) {
			ARGV = new String[] { "-linkmode", "launch", "-linkname",
					"\"/Applications/Mathematica2.app/Contents/MacOS/MathKernel\" -mathlink" };
		} else {
			// path on Linux VM (i.e. puremath.wolfram.com)
			// ARGV = new String[]{"-linkmode", "launch", "-linkname",
			// "\"/usr/local/Wolfram/Mathematica/11.0/Executables/MathKernel\"
			// -mathlink"};
			ARGV = new String[] { "-linkmode", "launch", "-linkname", "math -mathlink" };
		}
		try {
			ml = MathLinkFactory.createKernelLink(ARGV);
			System.out.println("MathLink created! " + ml);
			// discard initial pakets the kernel sends over.
			ml.discardAnswer();
		} catch (MathLinkException e) {
			e.printStackTrace();
		}
		return ml;
	}

}
