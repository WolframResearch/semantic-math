package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.parse.WLCommand;
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
	//math0208056: POSIX tar archive (GNU)
	private static final Pattern TAR_FILE_PATTERN = Pattern.compile(".+tar archive.+");
	static final Pattern TEX_PATTERN = Pattern.compile(".*(?:tex|TeX) .*");
	private static final String FILE_RENAME_PREFIX = "1";
	
	/**
	 * Should have cd'd into directory with files that are *already* gun-zipped.
	 * Assuming non-math files have been deleted already.
	 * @param 
	 * @param contentDirPath path to directory to drop tex files to. 
	 * Actually should keep track of the names of tex files. 
	 * @return Set of filenames that are tex files.
	 */
	public static Map<String, String> createFileHierarchy(String srcDirAbsPath){
		
		Map<String, String> texFileNamesMap = new HashMap<String, String>();
		//check file type. don't need to untar if not tar ball
		File dir = new File(srcDirAbsPath);
		File[] files = dir.listFiles();	
		fileLoop: for(File file : files){
			//System.out.println("CreateTexFileHiearchy - processing file "+ file);
			String fileName = file.getName();
			Process pr = null;
			String fileAbsolutePath = file.getAbsolutePath();
			pr = executeShellCommand("file \"" + fileAbsolutePath + "\"");
			if(null == pr){
				continue;
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
			//Matcher matcher;
			try{
				String line;
				while(null != (line = br.readLine())){					
					//System.out.println("CreateTexFileHiearchy - file info for file " + file.getName() + ": " + line);
					if(UnzipFile2.TEX_PATTERN.matcher(line).matches() && !UnzipFile2.NONTEX_EXTENSION_PATTERN.matcher(line).matches()){
						//is tex *file*, e.g. math0209381, so don't delte.
						texFileNamesMap.put(fileAbsolutePath, fileName);
						continue fileLoop;
					}
					//process if if tar file, 
					if(TAR_FILE_PATTERN.matcher(line).matches() ){
						//if tar file of directory, untar into directory of same name, so not to create tarball explosion. 
						//make directory
						String tarDirName = fileAbsolutePath;
						/*boolean fileCreated = new File(tarDirName).mkdir();
						if(!fileCreated){
							continue fileLoop;
						}*/		
						//rename, then makedir
						Path curTarFilePath = Paths.get(tarDirName);
						String renamedTarFileName = tarDirName + FILE_RENAME_PREFIX;
						Path renamedTarFilePath = Paths.get(renamedTarFileName);
						renamedTarFilePath = Files.move(curTarFilePath, renamedTarFilePath);
						//System.out.println("old file exists? " + Files.exists(curTarFilePath) +  " CreateTexFileHierarchy - renamed path: " + s);
						
						Files.createDirectory(curTarFilePath);
						
						pr = executeShellCommand("tar xf " + renamedTarFileName + " -C "+ tarDirName);
						//System.out.println("executing command: "+"tar xf " + renamedTarFileName + " -C "+ tarDirName );
						//flush the subprocess output stream.
						getCommandOutput(pr);
						//System.out.println( "commandOutput: " +getCommandOutput(pr));
						Files.delete(renamedTarFilePath);
						if(null == pr){
							System.out.println("CreteTexFileHiearchy - createFileHierarchy - pr is null!");
							continue fileLoop;
						}
						findTexFilesInTarDir(tarDirName, fileName, texFileNamesMap);
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
		return texFileNamesMap; 		
	}
	
	/** look through content and cp tex file(s), combine if necessary, 
	 * into parent directory.
	 * Should have cd'd into directory containing the tar file before calling this.
	 * Set of file names, some of which are relative file path, relative to current dir.
	 * @param tarDirAbsPath
	 * @param fileName
	 * @param texFileNamesMap
	 */
	private static void findTexFilesInTarDir(String tarDirAbsPath, String tarFileName, Map<String, String> texFileNamesMap){
		
		File tarDirFile = new File(tarDirAbsPath);
		assert(tarDirFile.isDirectory());		
		File[] tarDirFiles = tarDirFile.listFiles();
		//System.out.println("findTexFilesInTarDir: tarDirFiles: " + Arrays.toString(tarDirFiles));
		//System.out.println("CreateTexFileHiearchy - tarDirFiles.length "+tarDirFiles.length);
		for(File file : tarDirFiles){
			String fileName = file.getName();
			String fileAbsolutePathName = tarDirAbsPath + File.separator + fileName;
			//get all tex files inside dir, move them to parent dir
			Process pr = executeShellCommand("file \"" + fileAbsolutePathName + "\"");			
			System.out.println("executing command: "+ "file " + fileAbsolutePathName);
			if(null == pr){
				System.out.println("CreteTexFileHiearchy - findTexFilesInTarDir - pr is null!");
				continue;
			}
			
			String output = getCommandOutput(pr);
			System.out.println("findTexFilesInTarDir: getCommandOutput: " + output);
			//add to map if Tex file,
			if(UnzipFile2.TEX_PATTERN.matcher(output).matches() && !UnzipFile2.NONTEX_EXTENSION_PATTERN.matcher(output).matches()){
				texFileNamesMap.put(fileAbsolutePathName, tarFileName);
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
			String msg = "IOExceotion in executeShellCommand while executing " + cmd;
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
			//System.out.println("br.readLine() "+br.readLine());
			String line;
			while(null != (line = br.readLine())){				
				sb.append(line).append("\n");
				//System.out.println("getCommandOutput - next line: " + line);
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
		Map<String, String> texFileNamesSet = createFileHierarchy(srcBasePath);
		
		List<Map<String, String>> texFileNamesSetList = new ArrayList<Map<String, String>>();
		texFileNamesSetList.add(texFileNamesSet);
		String fileStr = srcBasePath +"/texFileNamesSetList.dat";
		FileUtils.serializeObjToFile(texFileNamesSetList, fileStr);
		String stringFileStr = srcBasePath + "/texFileNamesSetList.txt";
		FileUtils.writeToFile(texFileNamesSet, stringFileStr);
	}
	
}
