package thmp.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility functions pertaining to files.
 * 
 * @author yihed
 *
 */
public class FileUtils {

	/**
	 * Write content to file at absolute path.
	 * @param contentList
	 * @param fileTo
	 */
	public static void writeToFile(List<String> contentList, Path fileTo){
		try {
			Files.write(fileTo, contentList, Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
}
