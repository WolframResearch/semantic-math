package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.WLCommand;
import thmp.utils.FileUtils;

/**
 * Create appropriate tex files hierarchy, extracting tar files 
 * into directories bearing their names.
 * @author yihed
 */
public class CreateTexFileHierarchy {

	//set of file names known to be tex
	//private static final Set<String> texFileNamesSet = new HashSet<String>();
	private static final Runtime rt = Runtime.getRuntime();
	private static final Logger logger = LogManager.getLogger(WLCommand.class);
	private static final Pattern TAR_FILE_PATTERN = Pattern.compile("(.+)\\.tar");
	static final Pattern TEX_PATTERN = Pattern.compile(".*(?:tex|TeX) .*");
	
	/**
	 * Should have cd'd into directory with files that are *already* gun-zipped.
	 * Assuming non-math files have been deleted already.
	 * @param 
	 * @param contentDirPath path to directory to drop tex files to. 
	 * Actually should keep track of the names of tex files. 
	 * @return Set of filenames that are tex files.
	 */
	public static Set<String> createFileHierarchy(String srcDirAbsPath){
		
		Set<String> texFileNamesSet = new HashSet<String>();
		//check file type. don't need to untar if not tar ball
		File dir = new File(srcDirAbsPath);
		File[] files = dir.listFiles();	
		fileLoop: for(File file : files){
			
			Process pr = null;
			pr = executeShellCommand("file " + file.getName());
			if(null == pr){
				continue;
			}
			
			BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
			Matcher matcher;
			try{
				String line;
				while(null != (line = br.readLine())){
					if(UnzipFile2.TEX_PATTERN.matcher(line).matches() && !UnzipFile2.NONTEX_EXTENSION_PATTERN.matcher(line).matches()){
						//is tex file, e.g. math0209381, so leave alone.
						//put into parent directory for thm extraction
						executeShellCommand("    ");
						texFileNamesSet.add(file.getName());
						continue fileLoop;
					}
					//if(!TEX_PATTERN.matcher(line).matches() || NONTEX_EXTENSION_PATTERN.matcher(line).matches()){
					//if tar file, 
					if((matcher=TAR_FILE_PATTERN.matcher(line)).matches() ){
						//if tar file of directory, untar into directory of same name, so not to create tarball explosion. 
						//make directory
						String tarDirName = matcher.group(1);
						boolean fileCreated = new File(tarDirName).mkdir();
						if(!fileCreated){
							continue fileLoop;
						}
						try{
							pr = rt.exec("untar -C "+ tarDirName + " -xzf " + file.getName());
							//check return status?
						}catch(IOException e){
							String msg = "IOException in createFileHierarchy while executing comamnd";
							System.out.println(msg);
							logger.error(msg);
							continue;
						}
						findTexFilesInTarDir(tarDirName, texFileNamesSet);
						continue fileLoop;
					}
					//is neither tex file nor tarball, delete file
					file.delete();
				}
				
			}catch(IOException e){
				String msg = "IOException in createFileHierarchy!";
				System.out.println(msg);
				logger.error(msg);
			}
		}		
		return texFileNamesSet; 		
	}
	
	/**
	 * look through content and cp tex file(s), combine if necessary, 
	 * into parent directory.
	 * Should have cd'd into directory containing the tar file before calling this.
	 * Set of file names, some of which are relative file path, relative to current dir.
	 */
	private static void findTexFilesInTarDir(String tarDirName, Set<String> texFileNamesSet){
		
		File tarDirFile = new File(tarDirName);
		assert(tarDirFile.isDirectory());		
		File[] tarDirFiles = tarDirFile.listFiles();
		
		for(File file : tarDirFiles){
			String fileName = file.getName();
			//get all tex files inside dir, move them to parent dir
			Process pr = executeShellCommand("file " + fileName);
			
			if(null == pr){
				continue;
			}
			String output = getCommandOutput(pr);
			//add to map if Tex file,
			if(UnzipFile2.TEX_PATTERN.matcher(output).matches() && !UnzipFile2.NONTEX_EXTENSION_PATTERN.matcher(output).matches()){
				texFileNamesSet.add(tarDirName + File.separator + fileName);
			}
		}
		//gather set of tex files in dir, cp them all to  
		//parent directory with names paper.tex1, paper.tex2, etc.	
		
	}
	
	/**
	 * @param cmd
	 * @return nullable
	 */
	private static Process executeShellCommand(String cmd){
		Process pr = null;
		try{
			pr = rt.exec(cmd);
		}catch(IOException e){
			String msg = "IOExceotion in executeShellCommand!";
			System.out.println(msg);
			logger.error(msg);
		}
		return pr;
	}
	
	/**
	 * Reads the output of the command just executed.
	 * @return
	 */
	private static String getCommandOutput(Process pr){
		StringBuffer sb = new StringBuffer(30);
		BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));		
		try{
			String line;
			while(null != (line = br.readLine())){				
				sb.append(line).append("\n");
			}
			int sbLen = sb.length();
			if(sbLen > 1){
				sb.delete(sbLen-1, sbLen);
			}
		}catch(IOException e){
			String msg = "IOException while reading command output!";
			System.out.println(msg);
			logger.error(msg);
		}
		return sb.toString();
	}
	
	public static void main(String[] args){
		//String directoryName;
		//cd into directory containing gzip'ed files
		String srcBasePath = System.getProperty("user.dir") + "/" + args[0];
		Set<String> texFileNamesSet = createFileHierarchy(srcBasePath);
		List<Set<String>> texFileNamesSetList = new ArrayList<Set<String>>();
		texFileNamesSetList.add(texFileNamesSet);
		String fileStr = "srcBasePath/texFileNamesSetList.dat";
		FileUtils.serializeObjToFile(texFileNamesSetList, fileStr);
	}
	
}
