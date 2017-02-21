package thmp.crawl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Preprocesses zip file directory, e.g. delete non math .gz files.
 * @author yihed
 *
 */
public class PreprocessZipfileDir {

	/**
	 * args[0] contains *relative* path to the directory to be cleaned, so dir with all .gz files.
	 * Execution is currently in the parent directory, containing all .tar files.
	 */
	public static void main(String[] args){
		
		//delete non math .gz files, since unpacked files don't contain 'math' in their names,
		//even if they are math files.
		if(args.length == 0){
			System.out.println("Supply a directory of gzip files!");
			return;
		}
		//create absolute path from relative path.
		//srcBasePath contains all the .gz files.
		String srcBasePath = System.getProperty("user.dir") + "/" + args[0];
		System.out.println("PreprocessZipFile - file dir: " + System.getProperty("user.dir") + "/" + args[0]);
		
		//get file names
		File dir = new File(srcBasePath);
		if (!dir.isDirectory()) {
			String msg = "Source directory" + srcBasePath + " is not a directory!";
			System.out.println(msg);
			throw new IllegalStateException(msg);
		}
		File[] files = dir.listFiles();		
		for(File file : files){
			String fileName = file.getName();
			if(!fileName.matches("math.+\\.gz")){
				try{
					Files.delete(file.toPath());
				}catch(IOException e){
					System.out.println("IOException while deleting file!");
				}
			}
		}
	}
}
