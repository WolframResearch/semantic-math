package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

//import net.lingala.zip4j.core.ZipFile;
//import net.lingala.zip4j.exception.ZipException;

import thmp.ThmInput;

/**
 * Unzips files, extract math directories, and reads the latex files from them.
 * 
 * @author yihed
 *
 */
public class UnzipFile2 {

	private static final Pattern TXT_PATTERN = Pattern.compile("([^.]*)(\\.txt)");
	//name of file containing .gz file data.
	//lines such as "math-463636754.gz: gzip compressed data, was '....tar'"
	private static final String gzFileInfoFileNameStr = "fileStats.txt";
	//math0208018: LaTeX 2e document text
	static final Pattern TEX_PATTERN = Pattern.compile(".*(?:tex|TeX) .*");
	//extensions I don't want, but still get classified as TeX (auxiliary) file by 'file' command
	static final Pattern NONTEX_EXTENSION_PATTERN = Pattern.compile(".+(?:\\.sty|\\.pstex_t|auxiliary).*");
	
	/**
	 * Retrieves list of filenames in this directory. In this case .gz files to
	 * be uncompressed.
	 * 
	 * @return
	 * @param source
	 *            directory whose files are to be checked.
	 */
	private static List<String> getFileNames(String srcDir) {
		
		List<String> fileNames = new ArrayList<String>();
		File dir = new File(srcDir);
		if (!dir.isDirectory()) {
			System.out.println(srcDir + " is not a directory!");
			throw new IllegalStateException("Source directory" + srcDir + " is not a directory!");
		}
		File[] files = dir.listFiles();
		//System.out.println("srcDir inside getFileNames " + srcDir);
		for (File file : files) {
			String fileName = file.getName();
			//System.out.println("reading in fileName inside getFileNames(): " + fileName);
			// only append ones with .gz extension
			if (fileName.matches(".*\\.gz$")) {
				fileNames.add(fileName);
			}
		}
		return fileNames;
	}
	
	/**
	 * Source directory contains only math .gz files at this point.
	 * gunzip's the .gz files, and get only the names of .tex files.
	 * Retrieves list of tex math filenames in this directory. 
	 * 
	 * @return list of names to TeX files, some *don't* contain .tex exension. 
	 * file names only, not absolute paths.
	 * @param source
	 *            directory whose files are to be checked.
	 */
	private static List<String> getTexMathFileNames(String srcDirAbsolutePath) {
		
		List<String> fileNames = new ArrayList<String>();
		File dir = new File(srcDirAbsolutePath);
		if (!dir.isDirectory()) {
			String msg = "Source directory" + srcDirAbsolutePath + " is not a directory!";
			System.out.println(msg);
			throw new IllegalStateException(msg);
		}
		//create Map of file names and their file stats from fileStats.txt.
		//created by 'file some_file' in bash script
		Map<String, String> texFileInfoMap = createTexFileInfoMap();
		System.out.println("UnzipFile2.getTexMathFileNames - texFileInfoMap: " + texFileInfoMap);
		
		File[] files = dir.listFiles();	
		for(File file : files){
			//file name is just the name, not path
			String fileName = file.getName();
			if(texFileInfoMap.containsKey(fileName)){
				//System.out.println("UnzipFile2.getTexMathFileNames - src TeX fileName: " + fileName);
				fileNames.add(fileName);
			}			
		}
		return fileNames;
	}
	
	/**
	 * Pick out tex source files from system output of "file filename".
	 * @return
	 */
	private static Map<String, String> createTexFileInfoMap() {
		
		Map<String, String> fileInfoMap = new HashMap<String, String>();
		BufferedReader fileInfoBF = null;
		try{
			fileInfoBF = new BufferedReader(new FileReader(gzFileInfoFileNameStr));
		}catch(FileNotFoundException e){
			String msg = gzFileInfoFileNameStr + " file not found!";
			System.out.println(msg);
			throw new IllegalStateException(msg);
		}
		String line;
		try{
			while((line = fileInfoBF.readLine()) != null){
				//line could be ... LaTeX, or .tex etc
				//combine these two into one!
				if(!TEX_PATTERN.matcher(line).matches() || NONTEX_EXTENSION_PATTERN.matcher(line).matches()){
					//no String "tex" detected.
					continue;
				}
				//'file some_file' outputs e.g. "math ....gz: gzip compressed data..."
				String[] lineAr = line.split(":");
				if(lineAr.length < 2){
					continue;
				}				
				//only take file name rather than relative path containing directory info
				String fileName = lineAr[0];
				String[] fileNameAr = fileName.split("\\/");
				fileName = fileNameAr[fileNameAr.length - 1];
				
				String fileInfo = lineAr[1];
				fileInfoMap.put(fileName, fileInfo);
			}
		}catch(IOException e){
			throw new IllegalStateException(e);
		}finally{
			if(null != fileInfoBF){
				try{
					fileInfoBF.close();
				}catch(IOException e){
					//silently close, without potentially clobbering prior exceptions
					System.out.println("IOException while closing!");
				}
			}
		}
		return fileInfoMap;
	}

	/**
	 * Extracts content out of tex files, based on file format info provided by
	 * shell script.
	 * @param srcBasePath Contains trailing slash '/'
	 * @param destBasePath
	 * @param fileNames List of names of all files to be examined, filenames are full path.
	 * @return list of fileNames of extracted files.
	 */
	private static List<String> extractTexContent(String srcBasePath, String destBasePath, List<String> fileNames) {
		if (fileNames.isEmpty()){
			System.out.println("List of files is empty!");
			return null;
		}
		List<String> extractedFileNames = new ArrayList<String>();
		
		// length of buffer read in, optimal size?
		byte[] buf = new byte[1024];
		try {
			// fileNames is non-empty list.
			//GZIPInputStream gzipInputStream = null;
			FileInputStream fileInputStream = null;
			FileOutputStream fileOutputStream = null;
			//output stream for the output in all the .gz files.
			//String totalOutputStr = destBasePath + "total.txt";
			//FileOutputStream totalOutputStream = new FileOutputStream(totalOutputStr);
			
			for (String fileName : fileNames) {
				// if not .gz file or a math file
				//should include statistics as well, <--which names?
				//if (!fileName.matches("math[^.]*\\.gz$")) {
					//System.out.println("Not a .gz or math file!");
					//continue;
				//}
				//System.out.print("Current file being unzipped: " + fileName + "\t");
				String src = srcBasePath + fileName;
				//name the output file to be the same, but with .txt extension.
				String dest = destBasePath + fileName.replaceAll("([^\\.]*)\\..*$", "$1.tex");
				
				extractedFileNames.add(dest);
				
				//gzipInputStream = new GZIPInputStream(new FileInputStream(src));
				fileInputStream = new FileInputStream(src);				
				fileOutputStream = new FileOutputStream(dest);
				
				int l;
				while ((l = fileInputStream.read(buf)) > 0) {
					// 0 is the offset, l is the length to read/write.
					fileOutputStream.write(buf, 0, l);
					//totalOutputStream.write(buf, 0, l);
				}
				
				fileInputStream.close();
				fileOutputStream.close();
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(Arrays.toString(e.getStackTrace()));
		}
		return extractedFileNames;
	}
	
	/**
	 * Unzips gz file. List of .gz fileNames, to be appended to the base path.
	 * @return list of fileNames of extracted files.
	 */
	private static List<String> unzipGz(String srcBasePath, String destBasePath, List<String> fileNames) {
		if (fileNames.isEmpty()){
			System.out.println("List of files is empty!");
			return null;
		}
		
		//System.out.println("filenames inside unzipGz()" + fileNames); 
		List<String> extractedFileNames = new ArrayList<String>();

		// length of buffer read in, optimal size?
		byte[] buf = new byte[1024];
		try {
			// fileNames is non-empty list.
			GZIPInputStream gzipInputStream = null;
			FileOutputStream gzipOutputStream = null;
			//output stream for the output in all the .gz files.
			//String totalOutputStr = destBasePath + "total.txt";
			//FileOutputStream totalOutputStream = new FileOutputStream(totalOutputStr);
			
			for (String fileName : fileNames) {
				// if not .gz file or a math file
				//should include statistics as well, <--which names?
				if (!fileName.matches("math[^.]*\\.gz$")) {
					//System.out.println("Not a .gz or math file!");
					continue;
				}
				//System.out.print("Current file being unzipped: " + fileName + "\t");
				String src = srcBasePath + fileName;
				//name the output file to be the same, but with .txt extension.
				String dest = destBasePath + fileName.replaceAll("([^\\.]*).gz$", "$1.txt");
				//only read from .tex files
				extractedFileNames.add(dest);

				gzipInputStream = new GZIPInputStream(new FileInputStream(src));
				gzipOutputStream = new FileOutputStream(dest);
				
				int l;
				while ((l = gzipInputStream.read(buf)) > 0) {
					// 0 is the offset, l is the length to read/write.
					gzipOutputStream.write(buf, 0, l);
					//totalOutputStream.write(buf, 0, l);
				}
				
				gzipInputStream.close();
				gzipOutputStream.close();
			}
			
			//totalOutputStream.close();

		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(Arrays.toString(e.getStackTrace()));
		}
		return extractedFileNames;
	}

	/**
	 * Reads in a directory, e.g. 0002, should be un-tar'ed directory containing
	 * .gzip (.gz) files. args[0] is *relative* path to directory containing *only* 
	 * math .gz files. Other files should have been deleted by PreprocessZipFileDir.java.
	 * 
	 * @param args Directory path
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException {
		System.out.println(System.getProperty("user.dir"));
		
		if(args.length == 0){
			System.out.println("Supply a directory of gzip files!");
			return;
		}
		//create srcBasePath contains all the .gz files, but shell script already uncompressed those.
		String srcBasePath = System.getProperty("user.dir") + "/" + args[0];
		String destBasePath = srcBasePath + "Content/";
		System.out.println("destBasePath: " + destBasePath);
		srcBasePath = srcBasePath + "/";
		System.out.println("srcBasePath: " + srcBasePath);
		//make base directory that the will contain the extracted files
		new File(destBasePath).mkdirs();
		
		/* OR
		 * Path path = Paths.get("C:\\Directory1");
		 * Files.createDirectories(path);
		 */
		
		// get all file names in the source directory
		//List<String> fileNames = getFileNames(srcBasePath);
		List<String> texFileNames = getTexMathFileNames(srcBasePath);
		//System.out.println(fileNames);
		// list of files we just extracted. These should be .txt files.
		List<String> extractedFiles = extractTexContent(srcBasePath, destBasePath, texFileNames);
		
		//commenting out for now, to not extract thms
		boolean extractThmsBool = false;
		if(extractThmsBool){
			List<String> totalTextList = new ArrayList<String>();
			/*FileWriter fw = new FileWriter("outfilename", true);
		    BufferedWriter bw = new BufferedWriter(fw);
		    PrintWriter out = new PrintWriter(bw));
		    out.println("thm");*/
	
			// reads in those files and extract theorems
			for (String file : extractedFiles) {
				// File fileFrom = new File(file);
				// InputStream fileStream = new FileInputStream(file);
				FileReader fileReader = new FileReader(file);
				BufferedReader fileBufferedReader = new BufferedReader(fileReader);
	
				Path fileTo = Paths.get(TXT_PATTERN.matcher(file).replaceAll("$1_thms$2"));
				//Path fileTo = Paths.get(file.replaceAll("([^.]*)(\\.txt)", "$1_thms$2"));
				
				List<String> thmList = ThmInput.readThm(fileBufferedReader, null, null);
				totalTextList.addAll(thmList);
				
				// write list of theorems to file
				Files.write(fileTo, thmList, Charset.forName("UTF-8"));
				
				fileReader.close();
				fileBufferedReader.close();
			}
			
			Path totalTxtPath = Paths.get(destBasePath + "Total.txt");
			//less efficient than PrintWriter's!
			Files.write(totalTxtPath, totalTextList, Charset.forName("UTF-8"));
		}
	}
}
